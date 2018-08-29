package com.loafofpiecrust.turntable.service

import android.app.DownloadManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.util.Range
import android.util.SparseArray
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.*
import com.loafofpiecrust.turntable.artist.MusicDownload
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.addToMediaStore
import com.mcxiaoke.koi.ext.intValue
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.delay
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jetbrains.anko.*
import org.jsoup.Jsoup
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.experimental.Continuation


class OnlineSearchService : Service(), AnkoLogger {
    enum class OrderBy(val code: Int) {
        DATE(1),
        NAME(2),
        DOWNLOADS(4),
        SHOWS(6),
        SEEDERS(10),
        LEECHERS(11),
        SIZE(7),
        LAST_POST(8),
        LAST_SEED(9),
        SPEED_UP(12), // iffy
        SPEED_DOWN(13) // iffy
    }

    enum class SortOrder(val code: Int) {
        ASC(1),
        DESC(2)
    }

    enum class Quality {
        AWFUL,  // ~128kbps
        UNKNOWN, // Unlisted quality is probably comparable to 192kbps
        LOW,    // ~192kpbs
        MEDIUM, // ~256kbps
        LOSSLESS, // Prioritize 320kbps mp3 over flac for download times & practicality
        HIGH,   // ~320kbps
    }

    data class Result(val id: String, val title: String, val year: Int?, val seeders: Int, val size: Long, val quality: Quality) {
        suspend fun retrieveMagnet(): String {
            val cookie = instance.login()
            val res = Jsoup.connect("http://rutracker.org/forum/viewtopic.php")
                .data(mapOf(
                    "t" to id
                ))
                .cookie(cookie?.name(), cookie?.value())
                .get()
                .body()

            val post = res.getElementById("topic_main").child(1).child(0).child(1).child(1)
            val body = post.child(0) // contains image & tracks somewhere
            val info = post.child(2).child(0)
            return info.child(4).child(1).getElementsByTag("a").first().attr("href")
        }
    }

    companion object {
        const val SEEDERS_OVER_QUALITY = 3
        lateinit var instance: OnlineSearchService private set
        const val IDENTITY_POOL_ID = "us-east-2:4c4b30e2-1c0b-4802-83b2-32b232a7f4c4"
//        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val STALE_ENTRY_AGE = TimeUnit.HOURS.toMillis(6)
        val STALE_UNAVAILABLE_AGE = TimeUnit.DAYS.toMillis(7)
    }

    private var cookie: okhttp3.Cookie? = null
    private var loggedIn = false

    class Torrent : SessionManager() {
        var info: TorrentInfo? = null
        var handle: TorrentHandle? = null
        internal val _progress = ConflatedBroadcastChannel(0)
        val progress: ReceiveChannel<Int> get() = _progress.openSubscription().distinctSeq()
    }

    sealed class StreamStatus {
        class Unknown: StreamStatus()

        data class Unavailable(
            val timestamp: Long = System.currentTimeMillis()
        ): StreamStatus() {
            val isStale: Boolean get() {
                val now = System.currentTimeMillis()
                return Math.abs(now - timestamp) >= STALE_UNAVAILABLE_AGE
            }
        }

        data class Available(
            val youtubeId: String,
            val stream: String,
            val hqStream: String? = null,
            val timestamp: Long = System.currentTimeMillis()
        ): StreamStatus() {
            val isStale: Boolean get() {
                val now = System.currentTimeMillis()
                return Math.abs(now - timestamp) >= STALE_ENTRY_AGE
            }
        }

        companion object {
            fun from(entry: SongDBEntry?): StreamStatus = when {
                entry == null -> StreamStatus.Unknown()
                entry.youtubeId == null -> StreamStatus.Unavailable()
                else -> tryOr(StreamStatus.Unknown()) {
                    entry.stream128 = given(entry.stream128) {
                        decompress(it)
                    }
                    entry.stream192 = given(entry.stream192) {
                        decompress(it)
                    }

                    StreamStatus.Available(
                        entry.youtubeId!!,
                        entry.stream128!!,
                        entry.stream192,
                        if (entry.stream128 != null || entry.stream192 != null) {
                            entry.timestamp
                        } else {
                            0 // force entries without stream urls, but *with* a videoId to be stale.
                        }
                    )
                }
            }
        }
    }

    data class SongDownload(val song: Song, val progress: Double, val id: Long) {
        companion object {
            val COMPARATOR = Comparator<SongDownload> { a, b ->
                a.song.id.compareTo(b.song.id)
            }
        }
    }

//    val downloadingSongs = HashMap<Long, SongDownload>()
    private val _downloadingSongs = ConflatedBroadcastChannel(listOf<SongDownload>())
    private val downloadingSongs get() = _downloadingSongs.openSubscription().map {
        it.sortedWith(SongDownload.COMPARATOR)
    }
    val downloads = ArrayList<MusicDownload>()

    @DynamoDBTable(tableName="MusicStreams")
    data class SongDBEntry(
        @DynamoDBHashKey(attributeName="SongId")
        var id: String,
        var youtubeId: String? = null,
        var timestamp: Long = System.currentTimeMillis(),
        var stream128: String? = null,
        var stream192: String? = null
    ) {
        constructor(): this("")

        /// Map of videoIds reported by users as not correct, to # of reports
        @DynamoDBAttribute
        var blacklist: Map<String, Int> = mapOf()
    }

    @DynamoDBTable(tableName="TurntableAlbums")
    data class AlbumDBEntry(
        @DynamoDBHashKey var id: String,
        var youtubeId: String?,
        var timestamp: Long = System.currentTimeMillis(),
        var stream: String? = null,
        var hqStream: String? = null,
        var torrentUrl: String? = null,
        var tracks: List<Pair<String, Range<Long>>> = listOf()
    )


    private val database: AmazonDynamoDBClient by lazy {
        val credentials = CognitoCachingCredentialsProvider(
            App.instance,
            IDENTITY_POOL_ID,
            Regions.US_EAST_2)

        AmazonDynamoDBClient(credentials).apply {
            setRegion(Region.getRegion(Regions.US_EAST_2))
        }
    }
    val dbMapper: DynamoDBMapper by lazy {
        DynamoDBMapper(database)
    }

    fun saveYouTubeStreamUrl(entry: SongDBEntry) = task {
        try {
            dbMapper.save(SongDBEntry(
                entry.id.toLowerCase(),
                entry.youtubeId,
                entry.timestamp,
                given(entry.stream128) { compress(it) },
                given(entry.stream192) { compress(it) }
            ))
//            database.putItem("MusicStreams", mapOf(
//                "SongId" to AttributeValue(entry.id),
//                "stream128" to AttributeValue(entry.stream128),
//                "stream192" to AttributeValue(entry.stream192)
//            ))
        } catch (e: Exception) {
            error("Failed to save song to database", e)
        }
    }

    fun checkSongStreams(song: Song): StreamStatus {
        return getExistingEntry(song.id.dbKey)
    }

    fun checkSongStreams(songs: List<Song>): List<Pair<Song, StreamStatus>> {
        val entries = dbMapper.batchLoad(mapOf(SongDBEntry::class.java as Class<*> to
            songs.map { KeyPair().withHashKey(it.id.dbKey) }
        ))["MusicStreams"]!!.map { StreamStatus.from(it as SongDBEntry) }
        return songs.zip(entries)
    }

    fun reloadSongStreams(id: SongId) {
        val key = id.dbKey
        database.deleteItem("MusicStreams", mapOf("SongId" to AttributeValue(key)))
    }
    fun reloadAlbumStreams(id: AlbumId) {
        val key = id.dbKey
        database.deleteItem("MusicStreams", mapOf("SongId" to AttributeValue(key)))
    }

    private fun getExistingEntry(key: String): StreamStatus {
        val now = System.currentTimeMillis()
        val entry = tryOr(null) { dbMapper.load(SongDBEntry::class.java, key) }
        return StreamStatus.from(entry)
    }

    private suspend fun createEntry(key: String, videoId: String): StreamStatus {
        return suspendedTask<StreamStatus> { cont ->
            YTExtractor(key, videoId, cont)
                .extract("https://youtube.com/watch?v=$videoId", true, true)
        }.await()
    }

    data class SongStream(
        val status: StreamStatus,
        val start: Int = 0,
        val end: Int = 0
    )
    suspend fun getSongStreams(song: Song): SongStream {
        val key = song.id.dbKey
        val existing = getExistingEntry(key)
        info { "youtube: ${song.id} existing entry? $existing" }
        return if (existing is StreamStatus.Available) {
            SongStream(if (existing.isStale) {
                createEntry(key, existing.youtubeId)
            } else existing)
        } else if (existing is StreamStatus.Unavailable && !existing.isStale) {
            SongStream(existing)
        } else {
            val albumKey = song.id.album.dbKey
            val album = /*Library.instance.findAlbumOfSong(song).first()
                ?:*/ LocalAlbum(song.id.album, listOf(song))

            getExistingEntry(albumKey).let { entry ->
                if (entry is StreamStatus.Available) {
                    YouTubeFullAlbum.grabFromPage(album, entry.youtubeId)?.let { ytAlbum ->
                        ytAlbum.tracks[song]?.let { ytTrack ->
                            SongStream(
                                entry,
                                start = ytTrack.start,
                                end = ytTrack.end
                            )
                        }
                    }
                } else null
            } ?: given(YouTubeFullAlbum.search(album)) { ytAlbum ->
                given(ytAlbum.tracks[song]) {
                    SongStream(
                        createEntry(albumKey, ytAlbum.id),
                        it.start, it.end
                    )
                }
            } ?: tryOr(SongStream(StreamStatus.Unknown())) {
                val res = Http.get("https://us-central1-turntable-3961c.cloudfunctions.net/parseStreamsFromYouTube", params = mapOf(
                    "title" to song.id.displayName.toLowerCase(),
                    "album" to song.id.album.displayName.toLowerCase(),
                    "artist" to song.id.artist.displayName.toLowerCase(),
                    "duration" to song.duration.toString()
                )).gson.obj

                 val lq = res["lowQuality"].nullObj?.get("url")?.string

                if (lq == null) {
                    // not available on youtube!
                    saveYouTubeStreamUrl(SongDBEntry(
                        key, null, System.currentTimeMillis()
                    ))
                    SongStream(StreamStatus.Unavailable())
                } else {
                    val hq = res["highQuality"].nullObj?.get("url")?.string
                    val id = res["id"].string
                    saveYouTubeStreamUrl(SongDBEntry(
                        key, id,
                        stream128 = lq,
                        stream192 = hq
                    ))
                    SongStream(StreamStatus.Available(id, lq, hq))
                }
            }
        }
    }

    suspend fun getAlbumStreams(album: Album): Pair<Album, StreamStatus> {
        val key = album.id.dbKey

        return given(YouTubeFullAlbum.search(album)) { ytAlbum ->
            val resolvedAlbum = ytAlbum.resolveToAlbum()
            getExistingEntry(key).let {
                resolvedAlbum to if (it is StreamStatus.Available && !it.isStale) {
                    it
                } else createEntry(key, ytAlbum.id)
            }
        } ?: album to StreamStatus.Unavailable()
    }

    /// TODO: Include both 128 and 192 streams in the return value
//    fun getYouTubeStreams(song: Song): Pair<Song, SongDBEntry>? {
//        val key = "${song.name}|${song.album}|${song.artist}".toLowerCase()
//        async(UI) { println("youtube: looking for ${key}") }
//        val now = System.currentTimeMillis()
//        val entry = dbMapper.load(SongDBEntry::class.java, key)
//        val howOldIsStale = TimeUnit.HOURS.toMillis(12) // 2 months is a potentially stale entry, refresh it
//        return if (entry?.stream128 != null) {
//            async(UI) { println("youtube: found DB entry, took ${(System.currentTimeMillis() - now)}ms") }
//
//            // Play the existing stream for speed, but also refresh the entry to keep the DB up to date
//            if (entry.timestamp == null || Math.abs(now - entry.timestamp!!) > howOldIsStale) {
//                task {
//                    val ytSong = YouTubeSong.search(song) ?: return@task
//                    object : YouTubeExtractor(App.instance) {
//                        override fun onExtractionComplete(streams: SparseArray<YtFile>, meta: VideoMeta) {
//                            val stream128 = streams[140] // m4a audio, 128kbps
//                            val stream128ogg = streams[171]
//                            val stream64webm = streams[250]
//                            val stream192 = streams[251] // webm audio, 192kbps
//                            val stream128video = streams[43]
//                            val stream96video = streams[18]
//                            val low = stream128 ?: stream128ogg ?: stream128video ?: stream96video ?: stream64webm
//                            val entry = SongDBEntry(key, ytSong.id, System.currentTimeMillis(), low?.url, stream192?.url)
//                            saveYouTubeStreamUrl(entry)
//                        }
//                    }.extract("https://youtube.com/watch?v=${ytSong.id}", true, true)
//                }
//            }
//
//            entry.stream128 = given(entry.stream128) {
//                decompress(it)
//            }
//            entry.stream192 = given(entry.stream192) {
//                decompress(it)
//            }
//
//            song to entry
//        } else {
//            // Check for an album video first
//            val albumKey = "--full-album--|${song.album}|${song.artist}"
//            val ytAlbum = YouTubeFullAlbum.search(Album.justForSearch(song.album, song.artist).copy(tracks = listOf(song)))
//            if (ytAlbum != null) {
//                val videoTrack = ytAlbum.tracks.firstOrNull()
//                if (videoTrack != null) {
//                    val albumEntry = dbMapper.load(SongDBEntry::class.java, albumKey)
//                    if (albumEntry?.stream128 != null) {
//                        albumEntry.stream128 = given(albumEntry.stream128) {
//                            decompress(it)
//                        }
//                        albumEntry.stream192 = given(albumEntry.stream192) {
//                            decompress(it)
//                        }
//                        return videoTrack to albumEntry
//                    } else {
//                        val cont = deferred<Pair<Song, SongDBEntry>?, Unit>()
//                        object : YouTubeExtractor(App.instance) {
//                            override fun onExtractionComplete(streams: SparseArray<YtFile>?, meta: VideoMeta) {
//                                if (streams == null) {
//                                    cont.resolve(null)
//                                    return
//                                }
//                                val stream128 = streams[140] // m4a audio, 128kbps
//                                val stream128ogg = streams[171]
//                                val stream64webm = streams[250]
//                                val stream192 = streams[251] // webm audio, 192kbps
////                        async(UI) { println("youtube: stream 192 at ${stream192?.id}") }
//                                val stream128video = streams[43]
//                                val stream96video = streams[18]
//                                val low = stream128 ?: stream128ogg ?: stream128video ?: stream96video ?: stream64webm
////                        async(UI) { println("youtube: stream 128 at ${low.id}") }
//                                val entry = SongDBEntry(albumKey, ytAlbum.id, System.currentTimeMillis(), low?.url, stream192?.url)
//                                saveYouTubeStreamUrl(entry)
//                                cont.resolve(videoTrack to entry)
//                            }
//
//                            override fun onCancelled(result: SparseArray<YtFile>?) {
//                                super.onCancelled(result)
//                                cont.resolve(null)
//                            }
//
//                            override fun onCancelled() {
//                                super.onCancelled()
//                                cont.resolve(null)
//                            }
//                        }.extract("https://youtube.com/watch?v=${ytAlbum.id}", true, true)
//
//                        given (cont.promise.get()) {
//                            return it
//                        }
//                    }
//                }
//            }
//
//            async(UI) { println("youtube: looking on YouTube itself") }
//            val ytSong = YouTubeSong.search(song) ?: return null
//            async(UI) { println("youtube: loading stream") }
//            // TODO: Check if we have internet before doing this
//            val cont = deferred<Pair<Song, SongDBEntry>?, Unit>()
//            object : YouTubeExtractor(App.instance) {
//                override fun onExtractionComplete(streams: SparseArray<YtFile>?, meta: VideoMeta) {
//                    if (streams == null) {
//                        cont.resolve(null)
//                        return
//                    }
//                    val stream128 = streams[140] // m4a audio, 128kbps
//                    val stream128ogg = streams[171]
//                    val stream64webm = streams[250]
//                    val stream192 = streams[251] // webm audio, 192kbps
////                        async(UI) { println("youtube: stream 192 at ${stream192?.id}") }
//                    val stream128video = streams[43]
//                    val stream96video = streams[18]
//                    val low = stream128 ?: stream128ogg ?: stream128video ?: stream96video ?: stream64webm
////                        async(UI) { println("youtube: stream 128 at ${low.id}") }
//                    val entry = SongDBEntry(key, ytSong.id, System.currentTimeMillis(), low?.url, stream192?.url)
//                    saveYouTubeStreamUrl(entry)
//                    cont.resolve(song to entry)
//                }
//
//                override fun onCancelled(result: SparseArray<YtFile>?) {
//                    super.onCancelled(result)
//                    cont.resolve(null)
//                }
//
//                override fun onCancelled() {
//                    super.onCancelled()
//                    cont.resolve(null)
//                }
//            }.extract("https://youtube.com/watch?v=${ytSong.id}", true, true)
//
//            cont.promise.get()
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
        println("torrent service destroyed")
    }

    suspend fun login(): Cookie? {
        // TODO: Get the expiration time of the login cookie
        if (cookie != null) { // and not expired yet
            return cookie
        } else {
            val res = Http.post("http://rutracker.org/forum/login.php",
                headers = mapOf("content-type" to "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"),
                body = "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; id=\"login_username\"\r\n\r\nloafofpiecrust\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; id=\"login_password\"\r\n\r\nPok10101\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; id=\"login\"\r\n\r\nвход\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--"
            )
            val cookie = Http.client.cookieJar().loadForRequest(HttpUrl.parse("http://rutracker.org/forum/login.php")).find { it.name() == "bb_session" }
//            val cookie = res.cookies.getCookie("bb_session")
            return if (cookie != null) {
                loggedIn = true
                this.cookie = cookie
                cookie
            } else {
                // Login failed
                println("RuTracker login failed")
                null
            }
        }
    }

    suspend fun search(album: Album, cb: (List<Result>) -> Unit) {
        val cookie = login() ?: return

        val selfTitled = album.id.selfTitledAlbum

        val res = Jsoup.connect("http://rutracker.org/forum/tracker.php")
            .data(mapOf(
                "nm" to "${album.id.artist.name.toLowerCase()} ${album.id.displayName.toLowerCase()}", // search text
//                    "f" to "category",
                "o" to "${OrderBy.SEEDERS.code}", // Sort by # of seeds
                "s" to "${SortOrder.DESC.code}"
                // "start" to pageNumber * 50
                // "f" to forumIndexNumber
            ))
            .cookie(cookie.name(), cookie.value())
            .timeout(10000) // 10s
            .post()
            .body()

        val entries = res.getElementById("tor-tbl").child(1).children()
        val results = entries.map {
            // No results
            if (it.children().size < 2) {
                cb(listOf())
                return
            }

            val torrentName = it.child(3)?.child(0)?.child(0)?.text()
            println("we did it, maybe: $torrentName")

            // Can't really use flac image+.cue well at all.
            if (torrentName?.contains(".cue") == true) {
                return@map null
            }

            // For self-titled albums, if there's no indication it's just the one album,
            // skip it (for now)
            // TODO: Add support for discography/collection entries
            if (selfTitled) {
                val first = torrentName!!.indexOf(album.id.displayName, 0, true)
                if (first != -1) {
                    // we have one occurrence
                    val second =
                        if (torrentName.indexOf(album.id.displayName, first+1, true) != -1) {
                            println("we did it: a self-titled album.")
                        } else {
                            return@map null
                        }
                }
            }

            val size = it.child(5)?.child(0)?.text() ?: "0" // size in bytes
            var seeders = it.child(6)?.child(0)?.text() ?: "0"
            if (seeders.trimStart().isEmpty()) {
                seeders = "0"
            }
            val topicId = it.child(3)?.child(0)?.child(0)?.attr("data-topic_id")

            val isFlac = torrentName!!.contains(Regex("(FLAC|ALAC|flac|lossless)"))
            val quality = if (!isFlac) {
                val isMp3 = torrentName.contains("mp3", ignoreCase = true)
                val m = Pattern.compile("(\\d{3})\\s*(kb(ps|/s|s)|mp3)").matcher(torrentName)
                when {
                    m.find() -> {
                        val bitrate = m.group(1).toInt()
                        when (bitrate) {
                            in 0..191 -> Quality.AWFUL
                            in 192..255 -> Quality.LOW
                            in 256..319 -> Quality.MEDIUM
                            else -> Quality.HIGH
                        }
                    }
                    isMp3 -> Quality.MEDIUM
                    else -> Quality.UNKNOWN
                }
            } else {
                Quality.LOSSLESS
            }
            val m = Pattern.compile("((19|20)\\d{2})").matcher(torrentName)
            val year = if (m.find()) {
                m.group(1).toInt()
            } else {
                null
            }

            // Different years, must be different releases
            if (year != null && album.year != null && year != album.year) {
                return@map null
            }

            // No seeders, don't even try.
            val seedCount = seeders.toInt()
            if (seedCount <= 0) {
                return@map null
            }

            Result(topicId!!, torrentName, year, seeders.toInt(), size.toLong() / 1000, quality)
        }.filterNotNull().sortedByDescending {
            (it.quality.ordinal * SEEDERS_OVER_QUALITY) + it.seeders
        }

        println("we did it! $results")
        cb(results)
    }

//    object DownloadReceiver: BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
////            ActivityStarter.fill(this, intent)
//            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
//            val dl = OnlineSearchService.instance.downloadingSongs[downloadId]
//            dl?.let { (song, progress) ->
//                val query = DownloadManager.Query()
//                query.setFilterById(downloadId)
//                val cur = context.downloadManager.query(query)
//                if (cur.moveToFirst()) {
//                    val status = cur.intValue(DownloadManager.COLUMN_STATUS)
//                    // If the download was successful, fill in metadata for the file.
//                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
//                        val uri = Uri.parse(cur.stringValue(DownloadManager.COLUMN_LOCAL_URI))
//                        val name = cur.stringValue(DownloadManager.COLUMN_TITLE)
//
//                        val path = File(uri.path)
//                        task(UI) {
//                            println("youtube: downloaded song ${name} to ${path}")
//                        }
//                        try {
//                            val f = AudioFileIO.read(path)
//                            val tags = f.tag
//                            tags.setField(FieldKey.ARTIST, song.artist)
//                            tags.setField(FieldKey.ALBUM_ARTIST, song.artist)
//                            tags.setField(FieldKey.ALBUM, song.album)
//                            tags.setField(FieldKey.TITLE, song.name)
//                            tags.setField(FieldKey.TRACK, song.track.toString())
//                            if (song.disc > 0) {
//                                tags.setField(FieldKey.DISC_NO, song.disc.toString())
//                            }
//                            if (song.year != null && song.year > 0) {
//                                tags.setField(FieldKey.YEAR, song.year.toString())
//                            }
//                            AudioFileIO.write(f)
//                            context.addToMediaStore(path)
//                        } catch(e: Exception) {
//                            e.printStackTrace()
//                        }
//
//                    }
//                }
//            }
//        }
//    }

    override fun onCreate() {
        super.onCreate()
        instance = this

//        App.instance.registerReceiver(DownloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onBind(intent: Intent) = null

    private fun updateDownloads() {
        task(ALT_BG_POOL) {
            delay(500)

//            val toRemove = mutableListOf<SongDownload>()
            val dls = _downloadingSongs.value
            dls.forEach { dl ->
                val q = DownloadManager.Query()
                q.setFilterById(dl.id)

                val cursor = App.instance.downloadManager.query(q)
                if (cursor.moveToFirst()) {
                    val status = cursor.intValue(DownloadManager.COLUMN_STATUS)
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val dled = cursor.intValue(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val total = cursor.intValue(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                            val dls = _downloadingSongs.value
                            _downloadingSongs puts dls.withReplaced(dls.indexOf(dl), dl.copy(progress = dled.toDouble() / total.toDouble()))
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = Uri.parse(cursor.stringValue(DownloadManager.COLUMN_LOCAL_URI))
                            val path = File(uri.path)
                            try {
                                val f = AudioFileIO.read(path)
                                val tags = f.tag
                                tags.setField(FieldKey.ARTIST, dl.song.id.artist.displayName)
                                tags.setField(FieldKey.ALBUM_ARTIST, dl.song.id.album.artist.toString())
                                tags.setField(FieldKey.ALBUM, dl.song.id.album.toString())
                                tags.setField(FieldKey.TITLE, dl.song.id.displayName)
                                tags.setField(FieldKey.TRACK, dl.song.track.toString())
                                if (dl.song.disc > 0) {
                                    tags.setField(FieldKey.DISC_NO, dl.song.disc.toString())
                                }
                                dl.song.year?.let {
                                    if (it > 0) {
                                        tags.setField(FieldKey.YEAR, it.toString())
                                    }
                                }
                                AudioFileIO.write(ctx, f)
                                App.instance.addToMediaStore(path)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            _downloadingSongs puts _downloadingSongs.value.withoutElem(dl)
                        }
                        DownloadManager.STATUS_FAILED -> _downloadingSongs puts _downloadingSongs.value.withoutElem(dl)
                    }
                }
            }

            if (_downloadingSongs.value.isNotEmpty()) {
                updateDownloads()
            }
        }
    }

    fun addDownload(dl: SongDownload) {
        _downloadingSongs appends dl
        updateDownloads()
    }

    fun findDownload(song: Song): ReceiveChannel<SongDownload?> {
        return downloadingSongs.map {
            it.binarySearchElem(
                SongDownload(song, 0.0, 0),
                SongDownload.COMPARATOR
            )
        }
    }
}


class YTExtractor(
    val key: String,
    private val videoId: String,
    val cont: Continuation<OnlineSearchService.StreamStatus>
) : YouTubeExtractor(App.instance) {
    override fun onExtractionComplete(streams: SparseArray<YtFile>?, meta: VideoMeta?) {
        if (streams == null) {
            cont.resume(OnlineSearchService.StreamStatus.Unavailable())
            return
        }

        val stream128 = streams[140] // m4a audio, 128kbps
        val stream256 = streams[141]
        val stream128webm = streams[171]
//                    val stream64webm = streams[250]
        val stream160webm = streams[251] // webm audio, 192kbps
        val stream128video = streams[43]
        val stream96video = streams[18]
        val low = stream128 ?: stream128webm ?: stream128video ?: stream96video
        val high = stream256 ?: stream160webm
        val entry = OnlineSearchService.SongDBEntry(key, videoId, System.currentTimeMillis(), low?.url, high?.url)
        OnlineSearchService.instance.saveYouTubeStreamUrl(entry)
        if (low?.url != null || high?.url != null) {
            cont.resume(OnlineSearchService.StreamStatus.Available(
                    videoId,
                    (low?.url ?: high?.url)!!,
                    high?.url
            ))
        } else {
            cont.resume(OnlineSearchService.StreamStatus.Unavailable())
        }
    }

    override fun onCancelled(result: SparseArray<YtFile>?) {
        super.onCancelled(result)
        cont.resume(OnlineSearchService.StreamStatus.Unavailable())
    }

    override fun onCancelled() {
        super.onCancelled()
        cont.resume(OnlineSearchService.StreamStatus.Unavailable())
    }
}