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
import com.loafofpiecrust.turntable.util.days
import com.loafofpiecrust.turntable.util.distinctSeq
import com.loafofpiecrust.turntable.util.hours
import com.loafofpiecrust.turntable.util.http
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.map
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jetbrains.anko.downloadManager
import org.jetbrains.anko.longToast
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

    data class SongDownload(val song: Song, val progress: Int, val id: Long) {
        fun cancel() {
            App.instance.downloadManager.remove(id)
        }

        companion object {
            val COMPARATOR = Comparator<SongDownload> { a, b ->
                a.song.id.compareTo(b.song.id)
            }
        }
    }

    private val _downloadingSongs = ConflatedBroadcastChannel(
        immutableListOf<SongDownload>()
    )
    val downloadingSongs get() = _downloadingSongs.openSubscription().map {
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

    fun addDownload(dl: SongDownload) {
        launch {
            val context = App.instance
            _downloadingSongs putsMapped { it.add(dl) }

            var finished = false
            while (!finished) {
                delay(500)

                val q = DownloadManager.Query()
                q.setFilterById(dl.id)

                val cursor = context.downloadManager.query(q)
                if (cursor.moveToFirst()) {
                    val status = cursor.intValue(DownloadManager.COLUMN_STATUS)
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val dled = cursor.intValue(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val total = cursor.intValue(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                            _downloadingSongs putsMapped { dls ->
                                val idx = dls.indexOf(dl)
                                val updated = dl.copy(progress = (dled * 100) / total)
                                if (idx != -1) {
                                    dls.set(idx, updated)
                                } else {
                                    dls.add(updated)
                                }
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = Uri.parse(cursor.stringValue(DownloadManager.COLUMN_LOCAL_URI))
                            val path = File(uri.path)
                            ({
                                val f = AudioFileIO.read(path)
                                val tags = f.tag
                                tags.setField(FieldKey.ARTIST, dl.song.id.artist.displayName)
                                tags.setField(FieldKey.ALBUM_ARTIST, dl.song.id.album.artist.displayName)
                                tags.setField(FieldKey.ALBUM, dl.song.id.album.displayName)
                                tags.setField(FieldKey.TITLE, dl.song.id.displayName)
                                tags.setField(FieldKey.TRACK, dl.song.track.toString())
                                if (dl.song.disc > 0) {
                                    tags.setField(FieldKey.DISC_NO, dl.song.disc.toString())
                                }
                                if (dl.song.year > 0) {
                                    tags.setField(FieldKey.YEAR, dl.song.year.toString())
                                }
                                AudioFileIO.write(context, f)
                                context.addToMediaStore(path)
                            }).retryOrCatch(2) { e: Exception ->
                                e.printStackTrace()
                                App.launchWith {
                                    it.longToast(e.message ?: "Metadata write error")
                                }
                                path.delete()
                            }
                            finished = true
                        }
                        else -> {
                            finished = true
                        }
                    }
                } else {
                    finished = true
                }
            }

            _downloadingSongs putsMapped { it.remove(dl) }
        }
    }

    fun findDownload(song: Song): ReceiveChannel<SongDownload?> {
        return downloadingSongs.map {
            it.binarySearchElem(
                SongDownload(song, 0, 0),
                SongDownload.COMPARATOR
            )
        }
    }

    fun isDownloading(song: Song): Boolean {
        return runBlocking { findDownload(song).first() } != null
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