package com.loafofpiecrust.turntable.service

import android.app.DownloadManager
import android.net.Uri
import android.util.Range
import android.util.SparseArray
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.MusicDownload
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.selfTitledAlbum
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.remote.StreamCache
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.addToMediaStore
import com.mcxiaoke.koi.ext.intValue
import com.mcxiaoke.koi.ext.stringValue
import io.ktor.client.features.cookies.cookies
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.collections.immutable.immutableListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jetbrains.anko.downloadManager
import org.jsoup.Jsoup
import java.io.File
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


class OnlineSearchService : CoroutineScope by GlobalScope {
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

    init {
        instance = this
    }

    data class Result(val id: String, val title: String, val year: Int?, val seeders: Int, val size: Long, val quality: Song.Media.Quality) {
        suspend fun retrieveMagnet(): String {
            val cookie = instance.login()
            val res = Jsoup.connect("http://rutracker.org/forum/viewtopic.php")
                .data(mapOf(
                    "t" to id
                ))
                .cookie(cookie?.name, cookie?.value)
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
        const val IDENTITY_POOL_ID = BuildConfig.DYNAMODB_POOL_ID
//        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val STALE_ENTRY_AGE = 6.hours
        val STALE_UNAVAILABLE_AGE = 3.days
    }

    private var cookie: io.ktor.http.Cookie? = null
    private var loggedIn = false

    class Torrent : SessionManager() {
        var info: TorrentInfo? = null
        var handle: TorrentHandle? = null
        internal val _progress = ConflatedBroadcastChannel(0)
        val progress: ReceiveChannel<Int> get() = _progress.openSubscription().distinctSeq()
    }

    data class SongDownload(val song: Song, val progress: Double, val id: Long) {
        companion object {
            val COMPARATOR = Comparator<SongDownload> { a, b ->
                a.song.id.compareTo(b.song.id)
            }
        }
    }

    private val _downloadingSongs = ConflatedBroadcastChannel(
        immutableListOf<SongDownload>()
    )
    private val downloadingSongs get() = _downloadingSongs.openSubscription().map {
        it.sortedWith(SongDownload.COMPARATOR)
    }
    val downloads = ArrayList<MusicDownload>()

    @DynamoDBTable(tableName = "MusicStreams")
    data class AlbumDBEntry(
        @DynamoDBHashKey(attributeName = "SongId")
        var id: String,
        var youtubeId: String?,
        var expiryDate: Long,
        var stream128: String? = null,
        var stream192: String? = null,
//        var torrentUrl: String? = null,
        var tracks: List<Pair<String, Range<Long>>> = listOf()
    )

    private val awsCredentials by lazy {
        CognitoCachingCredentialsProvider(
            App.instance,
            IDENTITY_POOL_ID,
            Regions.US_EAST_2
        )
    }

    val database: AmazonDynamoDBClient by lazy {
        AmazonDynamoDBClient(awsCredentials).apply {
            setRegion(Region.getRegion(Regions.US_EAST_2))
        }
    }
    val dbMapper: DynamoDBMapper by lazy {
        DynamoDBMapper.builder()
            .dynamoDBClient(database)
            .build()
    }

//    fun saveYouTubeStreamUrl(entry: SongDBEntry) = launch {
//        try {
//            dbMapper.save(SongDBEntry(
//                entry.id.toLowerCase(),
//                entry.youtubeId,
//                entry.expiryDate,
//                entry.stream128?.compress(),
//                entry.stream192?.compress()
//            ))
////            database.putItem("MusicStreams", mapOf(
////                "SongId" to AttributeValue(entry.uuid),
////                "stream128" to AttributeValue(entry.stream128),
////                "stream192" to AttributeValue(entry.stream192)
////            ))
//        } catch (e: Exception) {
//            Timber.e(e) { "Failed to save song to database" }
//        }
//    }

//    fun checkSongStreams(song: Song): StreamStatus {
//        return getExistingEntry(song.id.dbKey)
//    }
//
//    fun checkSongStreams(songs: List<Song>): List<Pair<Song, StreamStatus>> {
//        val entries = dbMapper.batchLoad(mapOf(SongDBEntry::class.java as Class<*> to
//            songs.map { KeyPair().withHashKey(it.id.dbKey) }
//        ))["MusicStreams"]!!.map { StreamStatus.from(it as SongDBEntry) }
//        return songs.zip(entries)
//    }
//
//    fun reloadSongStreams(id: SongId) {
//        val key = id.dbKey
//        database.deleteItem("MusicStreams", mapOf("SongId" to AttributeValue(key)))
//    }
//    fun reloadAlbumStreams(id: AlbumId) {
//        val key = id.dbKey
//        database.deleteItem("MusicStreams", mapOf("SongId" to AttributeValue(key)))
//    }

//    private fun getExistingEntries(keys: Iterable<String>): Map<String, StreamStatus> {
//        val m = mapOf<Class<*>, List<KeyPair>>(SongDBEntry::class.java to keys.map { KeyPair().withHashKey(it) })
//        return dbMapper.batchLoad(m)["MusicStreams"]!!
//            .map {
//                val entry = it as SongDBEntry
//                entry.id to StreamStatus.from(entry)
//            }.toMap()
//    }

//    private fun getExistingEntry(key: String): StreamStatus {
//        val entry = tryOr(null) { dbMapper.load(SongDBEntry::class.java, key) }
//        return StreamStatus.from(entry)
//    }
//
//    private suspend fun createEntry(key: String, videoId: String): StreamStatus {
//        return withContext(Dispatchers.IO) {
//            suspendCoroutine<StreamStatus> { cont ->
//                YTExtractor(key, videoId, cont)
//                    .extract("https://youtube.com/watch?v=$videoId", true, true)
//            }
//        }
//    }

//    data class SongStream(
//        val status: StreamStatus,
//        val start: Int = 0,
//        val end: Int = 0
//    )

//    private suspend fun evalExistingStream(song: Song, existing: StreamStatus): SongStream {
//        val key = song.id.dbKey
//        return if (existing is StreamStatus.Available) {
//            SongStream(if (existing.isStale) {
//                createEntry(key, existing.youtubeId)
//            } else existing)
//        } else if (existing is StreamStatus.Unavailable && !existing.isStale) {
//            SongStream(existing)
//        } else {
//            val albumKey = song.id.album.dbKey
//            val album = /*Library.findAlbumOfSong(song).first()
//                ?:*/ LocalAlbum(song.id.album, listOf(song))
//
//            getExistingEntry(albumKey).let { entry ->
//                if (entry is StreamStatus.Available) {
//                    YouTubeFullAlbum.grabFromPage(album, entry.youtubeId)?.let { ytAlbum ->
//                        ytAlbum.tracks[song]?.let { ytTrack ->
//                            SongStream(
//                                entry,
//                                start = ytTrack.start,
//                                end = ytTrack.end
//                            )
//                        }
//                    }
//                } else null
//            } ?: YouTubeFullAlbum.search(album)?.let { ytAlbum ->
//                ytAlbum.tracks[song]?.let {
//                    SongStream(
//                        createEntry(albumKey, ytAlbum.id),
//                        it.start, it.end
//                    )
//                }
//            } ?: tryOr(SongStream(StreamStatus.Unknown)) {
//                val res = http.get<JsonObject> {
//                    url("https://us-central1-turntable-3961c.cloudfunctions.net/parseStreamsFromYouTube")
//                    parameters(
//                        "title" to song.id.displayName.toLowerCase(),
//                        "album" to song.id.album.displayName.toLowerCase(),
//                        "artist" to song.id.artist.displayName.toLowerCase(),
//                        "duration" to song.duration.toString()
//                    )
//                }
//
//                val lq = res["lowQuality"].nullObj?.get("url")?.string
//
//                if (lq == null) {
//                    // not available on youtube!
//                    val entry = SongDBEntry(
//                        key, null, System.currentTimeMillis()
//                    )
//                    saveYouTubeStreamUrl(entry)
//                    SongStream(StreamStatus.Unavailable(entry.expiryDate))
//                } else {
//                    val hq = res["highQuality"].nullObj?.get("url")?.string
//                    val id = res["id"].string
//                    val entry = SongDBEntry(
//                        key, id,
//                        stream128 = lq,
//                        stream192 = hq
//                    )
//                    saveYouTubeStreamUrl(entry)
//                    SongStream(StreamStatus.Available(id, lq, hq, entry.expiryDate))
//                }
//            }
//        }
//    }

//    suspend fun getSongStreams(song: Song): SongStream {
//        val key = song.id.dbKey
//        val existing = getExistingEntry(key)
//        Timber.d { "youtube: ${song.id} existing entry? $existing" }
//        return evalExistingStream(song, existing)
//    }

//    fun getManyExistingSongStreams(songs: List<Song>): Map<Song, StreamStatus> {
//        // TODO: More preemptive check rather than this LAME comparison
//        if (songs.all { it.id.album == songs[0].id.album }) {
//            val existingAlbum = getExistingAlbumStream(songs[0].id.album)
//            if (existingAlbum is StreamStatus.Available) {
//                return songs.map { it to existingAlbum }.toMap()
//            }
//        }
//
//        val keys = songs.map { it.id.dbKey }
//        val existingEntries = getExistingEntries(keys)
//        return songs.map {
//            it to existingEntries[it.id.dbKey]!!
//        }.toMap()
//    }

//    private fun getExistingAlbumStream(albumId: AlbumId): StreamStatus {
//        return getExistingEntry(albumId.dbKey)
//    }

//    suspend fun getAlbumStreams(album: Album): Pair<Album, StreamStatus> {
//        val key = album.id.dbKey
//
//        return YouTubeFullAlbum.search(album)?.let { ytAlbum ->
//            val resolvedAlbum = ytAlbum.resolveToAlbum()
//            getExistingEntry(key).let {
//                resolvedAlbum to if (it is StreamStatus.Available && !it.isStale) {
//                    it
//                } else createEntry(key, ytAlbum.id)
//            }
//        } ?: album to StreamStatus.Unavailable()
//    }

    suspend fun login(): io.ktor.http.Cookie? {
        // TODO: Get the expiration time of the login cookie
        if (cookie != null) { // and not expired yet
            return cookie
        } else {
            http.post<HttpResponse> {
                url("http://rutracker.org/forum/login.php")
                contentType(ContentType.parse("multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"))
                body = "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; uuid=\"login_username\"\r\n\r\nloafofpiecrust\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; uuid=\"login_password\"\r\n\r\nPok10101\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; uuid=\"login\"\r\n\r\nвход\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--"
            }
            val cookie = http.cookies("http://rutracker.org/forum/login.php").find { it.name == "bb_session" }
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
            .cookie(cookie.name, cookie.value)
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
                val m = Regex("(\\d{3})\\s*(kb(ps|/s|s)|mp3)").find(torrentName)
                when {
                    m != null -> {
                        val bitrate = m.groupValues[1].toInt()
                        when (bitrate) {
                            in 0..191 -> Song.Media.Quality.AWFUL
                            in 192..255 -> Song.Media.Quality.LOW
                            in 256..319 -> Song.Media.Quality.MEDIUM
                            else -> Song.Media.Quality.HIGH
                        }
                    }
                    isMp3 -> Song.Media.Quality.MEDIUM
                    else -> Song.Media.Quality.UNKNOWN
                }
            } else {
                Song.Media.Quality.LOSSLESS
            }
            val m = Regex("((19|20)\\d{2})").find(torrentName)
            val year = if (m != null) {
                m.groupValues[1].toInt()
            } else null

            // Different years, must be different releases
            if (year != null && album.year > 0 && year != album.year) {
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

//    override fun onCreate() {
//        super.onCreate()
//        instance = this
//
////        App.instance.registerReceiver(DownloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
//    }

    private fun updateDownloads() {
        launch {
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

                            _downloadingSongs putsMapped { dls ->
                                dls.withReplaced(dls.indexOf(dl), dl.copy(progress = dled.toDouble() / total.toDouble())).toImmutableList()
                            }
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
                                dl.song.year.let {
                                    if (it > 0) {
                                        tags.setField(FieldKey.YEAR, it.toString())
                                    }
                                }
                                AudioFileIO.write(App.instance, f)
                                App.instance.addToMediaStore(path)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            _downloadingSongs putsMapped { it.remove(dl) }
                        }
                        DownloadManager.STATUS_FAILED -> _downloadingSongs putsMapped { it.remove(dl) }
                    }
                }
            }

            if (_downloadingSongs.value.isNotEmpty()) {
                updateDownloads()
            }
        }
    }

    suspend fun addDownload(dl: SongDownload) {
        _downloadingSongs putsMapped { it.add(dl) }
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
    val cont: Continuation<StreamCache.StreamStatus>
) : YouTubeExtractor(App.instance) {
    override fun onExtractionComplete(streams: SparseArray<YtFile>?, meta: VideoMeta?) {
        if (streams == null) {
            cont.resume(StreamCache.StreamStatus.Unavailable(key))
            return
        }

        val stream128 = streams[140] // m4a audio, 128kbps
        val stream256 = streams[141]
        val stream128webm = streams[171]
        val stream160webm = streams[251] // webm audio, 192kbps
        val stream128video = streams[43]
        val stream96video = streams[18]
        val low = stream128 ?: stream128webm ?: stream128video ?: stream96video
        val high = stream256 ?: stream160webm
//        val entry = OnlineSearchService.SongDBEntry(key, videoId, System.currentTimeMillis(), low?.url, high?.url)
//        OnlineSearchService.instance.saveYouTubeStreamUrl(entry)
        if (low?.url != null || high?.url != null) {
            cont.resume(StreamCache.StreamStatus.Available(
                key,
                videoId,
                (low?.url ?: high?.url)!!,
                high?.url
            ))
        } else {
            cont.resume(StreamCache.StreamStatus.Unavailable(key))
        }
    }

    override fun onCancelled(result: SparseArray<YtFile>?) {
        super.onCancelled(result)
        cont.resume(StreamCache.StreamStatus.Unavailable(key))
    }

    override fun onCancelled() {
        super.onCancelled()
        cont.resume(StreamCache.StreamStatus.Unavailable(key))
    }
}