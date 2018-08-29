package com.loafofpiecrust.turntable.album

import android.os.Environment
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonArray
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.MusicDownload
import com.loafofpiecrust.turntable.artist.TorrentArtist
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.YouTubeSong
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Whitelist
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs


/// TODO: Use soundcloud as a backend for super indie shit. I mean, it'd be more legal and easier than youtube, FFS. It'd also have remixes and random stuff, but that won't be organized by album, unfortunately. Possibly treat playlists as a subtype of albums.

sealed class AlbumDownload(
    val quality: OnlineSearchService.Quality
): MusicDownload {
    companion object: AnkoLogger {
        fun search(album: Album) : AlbumDownload? {
            return null
//            val tpb = task { TPBAlbum.search(album) }
//            val ru = task { RuTrackerAlbum.search(album) }
//
//            // First check if we have a discography torrent up.
//            // If not, search for one.
//            // If there is one, index the album files
//            // Then, prioritize those files and start the download
//            val dls = OnlineSearchService.instance.downloads
//            val discography = dls.find {
//                it is TorrentArtist && it.goal.id == album.artist && it.indexAlbum(album) != null
//            }
//
//            return if (discography != null) {
//                debug { "torrent: found existing discography" }
//                TorrentAlbumFromArtist(album, discography as TorrentArtist)
//            } else {
//                // didn't have a discography torrent loaded already.
//                // see if there's one for the artist of this album
//
//                // Search for an artist discography torrent to get the album from
//                val artistResult = RuTrackerArtist.search(Artist(
//                    if (album.local is Album.LocalDetails.Downloaded) album.local.artistId else 0,
//                    album.artist,
//                    listOf(album),
//                    null
//                )).firstOrNull()
//                debug {"torrent: discography, ${artistResult?.name}, seeds=${artistResult?.seeders}" }
//
//                // The result _must_ have a year range containing the album we seek.
//                // Otherwise, we'd have to download magnet data and shit to check _if_ it has the album.
//                // TODO: Possibly loosen the year range requirement?
//                val year = album.year
//                if (artistResult?.years != null && year != null && year > 0 && artistResult.years.contains(year)) {
//                    // download through this discography
//                    // Don't do this here, this is only to return a search result, not download
//                    // Move this to a function like "downloadAlbum(album)" inside TorrentArtist
//                    // Which grabs the magnet and starts the download (if it isnt started)
//                    // and prioritizes the passed in album, leaving other priorities.
//                    // So, when any artist torrent is started, set all files to "Dont Download"
//                    // until downloadAlbum(Album) is called
////                    artistResult.dontDownloadAllFiles()
////                    artistResult.prioritize(album)
//                    val torrents = listOf(tpb.get(), ru.get(), TorrentAlbumFromArtist(album, artistResult)).filterNotNull()
//                    torrents.sortedByDescending { it.value }.first()
//
//                } else {
//                    // TODO: Replace with a cancellable promise
//                    val yt = deferred<YouTubeAlbum, Unit>()
//                    task { yt.resolve(YouTubeAlbum.search(album)) }
//                    val torrents = listOf(tpb, ru).map { it.get() }.filterNotNull()
//                    return if (torrents.isNotEmpty()) {
//                        if (!yt.promise.isDone()) yt.reject()
//                        val res = torrents.sortedByDescending { it.value }.first()
//                        debug { "torrent: album, quality=${res.quality}" }
//                        res
//                    } else {
//                        yt.promise.get()
//                    }
//                }
//            }
        }
    }
    //    abstract val completeness: Int
    abstract val value: Int
}

sealed class TorrentAlbum(
    private val goal: Album,
    quality: OnlineSearchService.Quality
) : AlbumDownload(quality) {
    private var torrent: OnlineSearchService.Torrent? = null
    private val songFiles = HashMap<Song, Int>()

    abstract suspend fun retrieveInfo(sess: SessionManager): TorrentInfo?

    override suspend fun download() {
//        val magnet = magnet
        val album = goal
        // Setup torrent session
        val sess = OnlineSearchService.Torrent()
        torrent = sess
        OnlineSearchService.instance.downloads.add(this)
        sess.addListener(object: AlertListener {
            override fun alert(alert: Alert<*>?) {
//                val type = alert?.type()
                when (alert) {
                    is AddTorrentAlert -> {
                        println("torrent added")
                        val h = alert.handle()
                        sess.handle = h
                        h.resume()
//                        buildSongIndex(h)
                    }
                    is BlockFinishedAlert -> {
                        val p = alert.handle().status().progress() * 100
                        sess._progress.offer(p.toInt())
//                        if (p.toInt() % 5 == 0) {
//                            println("torrent progress: $p")
//                        }
                    }
                    is TorrentFinishedAlert -> {
                        println("torrent finished")
                        val ti = sess.info
                        if (ti != null) {
                            for (path in ti.files().paths()) {
                                println("torrent file: $path")
//                                val values = ContentValues()
//                                values.put(MediaStore.MediaColumns.DATA, path)
//                                val success = contentResolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values, )
                            }

                        }
                        sess.stop()
                    }
                }
            }
            override fun types(): IntArray? = null
        })

        sess.start()

        val info = retrieveInfo(sess)
        sess.info = info
        if (info != null) {
            sess.progress.consumeEach(UI) {
                println("torrent progress $it")
            }
            println("torrent info: $info")
            // TODO: Use an Environment.getPath type thing, and the external Music dir or whatever has more space (Check free space?)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            sess.download(info, dir)
            println("torrent past dl")
        } else {
            // Couldn't get the seeders quick enough
        }
    }

    /// Map the songs on the intended album to the song files in this torrent.
    private fun buildSongIndex(handle: TorrentHandle) {
        val tor = torrent ?: return
        val info = tor.info ?: return
        val files = info.files()
        val songs = goal.tracks

        val paths = (0 until files.numFiles()).map { it to File(files.filePath(it)) }.toMutableList()
        val albumFolder = paths.find { (idx, file) ->
            file.isDirectory && FuzzySearch.partialRatio(file.name, goal.id.displayName) > 80
        } ?: return

        // Map of Song to file index within the torrent
        songFiles.putAll(songs.mapNotNull { song ->
            // Find the best match in remaining unmatched files
            val result = paths.filter { (fileIdx, file) ->
                // Only look inside the album folder
                file.startsWith(albumFolder.second)
            }.mapIndexed { idx, (fileIdx, file) ->
                // Map to the fuzzy comparison between the file id and song name
                idx to (fileIdx to FuzzySearch.partialRatio(song.id.name, file.name))
            }.maxBy { (idx, pair) -> pair.second }!! // Take the closest match

            if (result.second.second < 50) { // if matchRatio < 50%
                // barely a match at all...
                null
            } else { // should be a match
                paths.removeAt(result.first) // Remove it as an option for other songs
                song to result.second.first // => (song, fileIdx)
            }
        })
    }

    fun indexAlbum(album: Album) {

    }

    private fun getSongIndex(song: Song): Int?
        = songFiles[song]

    fun prioritize(song: Song) {
        val fileIdx = getSongIndex(song)
        if (fileIdx != null) {
            // Download this song _first_, ignoring rarity
            // TODO: Decide priority, maybe use SEVEN
            torrent?.handle?.setFilePriority(fileIdx, Priority.SIX)
        }
    }

    fun dontDownloadAll() {
        val tor = torrent ?: return
        val handle = tor.handle ?: return
        val info = tor.info ?: return
        val fileCount = info.files().numFiles()
        (0 until fileCount).forEach {
            handle.setFilePriority(it, Priority.IGNORE)
        }
    }
}

class TorrentAlbumFromArtist(
    goal: Album,
    private val discography: TorrentArtist
) : TorrentAlbum(goal, discography.quality) {
    override val value: Int
        get() = discography.value

    override suspend fun retrieveInfo(sess: SessionManager): TorrentInfo?
        = discography.retrieveInfo(sess)

    override suspend fun download() {
        discography.download()
        // TODO: Move this deprioritize to TorrentArtist.download()
//        discography.dontDownloadAllFiles()
//        discography.prioritize(goal)
    }
}

//class RuTrackerAlbum(
//    goal: Album,
//    val id: String,
//    val name: String,
//    val seeders: Int,
//    val size: Long,
//    quality: OnlineSearchService.Quality
//) : TorrentAlbum(goal, quality) {
//    companion object {
//        fun search(album: Album): RuTrackerAlbum? {
//            val cookie = OnlineSearchService.instance.login() ?: return null
//
//            val selfTitled = album.id.selfTitledAlbum
//
//            val res = Jsoup.connect("http://rutracker.org/forum/tracker.php")
//                .data(mapOf(
//                    "nm" to "${album.id.artist.name.toLowerCase()} ${album.id.displayName.toLowerCase()}", // search text
////                    "f" to "category",
//                    "o" to "${OnlineSearchService.OrderBy.SEEDERS.code}", // Sort by # of seeds
//                    "s" to "${OnlineSearchService.SortOrder.DESC.code}"
//                    // "start" to pageNumber * 50
//                    // "f" to forumIndexNumber
//                ))
//                .cookie(cookie.key, cookie.value as String)
//                .timeout(10000) // 10s
//                .post()
//                .body()
//
//            val entries = res.getElementById("tor-tbl").child(1).children()
//            val results = entries.map {
//                // No results
//                if (it.children().size < 2) {
//                    return@search null
//                }
//
//                val torrentName = it.child(3)?.child(0)?.child(0)?.text()
//                println("we did it, maybe: $torrentName")
//
//                // Can't really use flac image+.cue well at all.
//                if (torrentName?.contains(".cue") == true) {
//                    return@map null
//                }
//
//                // For self-titled albums, if there's no indication it's just the one album,
//                // skip it (for now)
//                // TODO: Add support for discography/collection entries
//                if (selfTitled) {
//                    val first = torrentName!!.indexOf(album.id.displayName, 0, true)
//                    if (first != -1) {
//                        // we have one occurrence
//                        val second =
//                            if (torrentName.indexOf(album.id.displayName, first+1, true) != -1) {
//                                println("we did it: a self-titled album.")
//                            } else {
//                                return@map null
//                            }
//                    }
//                }
//
//                val size = it.child(5)?.child(0)?.text() ?: "0" // size in bytes
//                var seeders = it.child(6)?.child(0)?.text() ?: "0"
//                if (seeders.trimStart().isEmpty()) {
//                    seeders = "0"
//                }
//                val topicId = it.child(3)?.child(0)?.child(0)?.attr("data-topic_id")
//
//                val isFlac = torrentName!!.contains(Regex("(FLAC|ALAC|flac|lossless)"))
//                val quality = if (!isFlac) {
//                    val isMp3 = torrentName.contains("mp3", ignoreCase = true)
//                    val m = Pattern.compile("(\\d{3})\\s*(kb(ps|/s|s)|mp3)").matcher(torrentName)
//                    if (m.find()) {
//                        val bitrate = m.group(1).toInt()
//                        when (bitrate) {
//                            in 0..191 -> OnlineSearchService.Quality.AWFUL
//                            in 192..255 -> OnlineSearchService.Quality.LOW
//                            in 256..319 -> OnlineSearchService.Quality.MEDIUM
//                            else -> OnlineSearchService.Quality.HIGH
//                        }
//                    } else if (isMp3) {
//                        OnlineSearchService.Quality.MEDIUM
//                    } else {
//                        OnlineSearchService.Quality.UNKNOWN
//                    }
//                } else {
//                    OnlineSearchService.Quality.LOSSLESS
//                }
//                val m = Pattern.compile("((19|20)\\d{2})").matcher(torrentName)
//                val year = if (m.find()) {
//                    m.group(1).toInt()
//                } else {
//                    null
//                }
//
//                // Different years, must be different releases
//                if (year != null && album.year != null && year != album.year) {
//                    return@map null
//                }
//
//                // No seeders, don't even try.
//                val seedCount = seeders.toInt()
//                if (seedCount <= 0) {
//                    return@map null
//                }
//
//                RuTrackerAlbum(album, topicId!!, torrentName, seeders.toInt(), size.toLong() / 1000, quality)
//            }.filterNotNull().sortedByDescending {
//                (it.quality.ordinal * OnlineSearchService.SEEDERS_OVER_QUALITY) + it.seeders
//            }
//
//            println("we did it! $results")
//            return results.firstOrNull()
//        }
//    }
//
//    override val value: Int
//        get() = quality.ordinal * 3 + seeders
//
//    override fun retrieveInfo(sess: SessionManager): TorrentInfo? {
//        val cookie = OnlineSearchService.instance.login()
//        val res = Jsoup.connect("http://rutracker.org/forum/viewtopic.php")
//            .data(mapOf(
//                "t" to id
//            ))
//            .cookie(cookie?.key, cookie?.value as String)
//            .get()
//            .body()
//
//        val post = res.getElementById("topic_main").child(1).child(0).child(1).child(1)
//        val body = post.child(0) // contains image & tracks somewhere
//        val info = post.child(2).child(0)
//        val magnet = info.child(4).child(1).getElementsByTag("a").first().attr("href")
//
//        do {
//            val nodes = sess.stats().dhtNodes()
//            if (nodes < 10) {
//                Thread.sleep(500)
//            }
//        } while (nodes < 10)
//
//        val magnetData = sess.fetchMagnet(magnet, 30)
//        return if (magnetData != null) {
//            TorrentInfo.bdecode(magnetData)
//        } else {
//            null
//        }
//    }
//}

class TPBAlbum(
    goal: Album,
    val title: String,
    private val seeders: Int,
    val size: Long,
    quality: OnlineSearchService.Quality,
    private val magnet: String
) : TorrentAlbum(goal, quality) {

    companion object {
        suspend fun search(album: Album): TPBAlbum? {
            // Sorted by seeders (desc), in the Audio category
            val query = URLEncoder.encode("${album.id} ${album.id.artist}", "UTF-8")
            val res = Jsoup.parse(Http.get(
                "https://thepiratebay.org/search/$query/0/7/100"//,
//                timeout = 10.0
            ).text)

            // TODO: Add check for no results
            val table = res.getElementById("searchResult").child(1)
            val results = table.children().take(10).map {
                val mainInfo = it.child(1)
                val title = mainInfo.child(0).child(0).text()

                if (!title.contains(album.id.displayName, ignoreCase=true)) {
                    return@map null
                }

                val magnet = mainInfo.child(1).attr("href")
                val details = mainInfo.child(4).text()

                val m = Pattern.compile("Size ([\\d.]+) ([GMK])iB").matcher(details)
                val size = if (m.find()) {
                    val num = m.group(1).toDouble()
                    val unit = when(m.group(2)) {
                        "G" -> 1_000_000L
                        "M" -> 1_000L
                        "K" -> 1L
                        else -> 1_000L
                    }
                    (num * unit).toLong()
                } else {
                    0L
                }
                val seeders = it.child(2).text().toInt()

                // TODO: Maybe check for quality, but on TPB there tend to be no quality markers
                TPBAlbum(album, title, seeders, size, OnlineSearchService.Quality.HIGH, magnet)
            }

            return results.firstOrNull()
        }
    }

    override suspend fun retrieveInfo(sess: SessionManager): TorrentInfo? {
        // Seems unneccessary to wait here, when fetchMagnet has a timeout.
        do {
            val nodes = sess.stats().dhtNodes()
            if (nodes < 10) {
                delay(500)
            }
        } while (nodes < 10)

        val magnetData = sess.fetchMagnet(magnet, 30)
        return if (magnetData != null) {
            TorrentInfo.bdecode(magnetData)
        } else {
            null
        }
    }

    override val value: Int
        get() = quality.ordinal * 3 + seeders
}


class YouTubeAlbum(
    private val goal: Album,
    val songs: List<YouTubeSong>
) : AlbumDownload(OnlineSearchService.Quality.MEDIUM) {
    /// Youtube download is like 10 seeders
    /// But for every song missing, value quickly degrades
    /// So, a YT full album = RuTracker 256kbps w/ 10 seeders
    /// A YT album with 2 missing tracks = RuTracker 256kbps w/ 3 seeders
    override val value: Int
        get() = (quality.ordinal * OnlineSearchService.SEEDERS_OVER_QUALITY + 10) - ((goal.tracks.size - songs.size) * 3.5f).toInt()

    companion object {
        fun search(album: Album): YouTubeAlbum = runBlocking {
            // Search for all the tracks on youtube concurrently
            val songs = album.tracks
                .parMap { YouTubeSong.search(it) }
                .mapNotNull { it.get() }

            println("albumyt: completeness = ${songs.size}/${album.tracks.size}")

            YouTubeAlbum(album, songs)
        }
    }

    override suspend fun download() = runBlocking {
        val results = songs
            .parMap { it.download() }.awaitAll()
    }
}

data class YouTubeFullAlbum(
    val goal: Album,
    val title: String,
    val id: String,
    val duration: Int,
    val tracks: Map<Song, Track>,
    val stream128: String?
) {
    data class Track(
        var title: String,
        val start: Int,
        val end: Int
    )


    fun resolveToAlbum(): Album {
        return goal
    }

    companion object: AnkoLogger by AnkoLogger<YouTubeFullAlbum>() {
        private val HMS_PAT by lazy { Pattern.compile("\\b\\(?(\\d{1,2}):(\\d{2}):(\\d{2})\\)?\\b") }
        private val MS_PAT by lazy { Pattern.compile("\\b\\(?(\\d{1,2}):(\\d{2})\\)?\\b") }

        suspend fun search(album: Album): YouTubeFullAlbum? {
            val res = Http.get("https://www.googleapis.com/youtube/v3/search", params = mapOf(
                "key" to BuildConfig.YOUTUBE_API_KEY,
                "part" to "snippet",
                "type" to "video",
                "q" to "${album.id.artist} ${album.id.displayName} full album",
                "maxResults" to "4",
                "videoSyndicated" to "true"
            )).gson

            info { "youtube: searching query '${album.id.artist} ${album.id.displayName} full album'" }

            val items = res["items"].nullArray ?: JsonArray()
            val results = items.map { it.obj }.map {
                val details = it["snippet"].obj
                val title = details["title"].string
//                    val desc = details["description"].string
                val videoId = it["id"]["videoId"].nullString

                val matchRatio = FuzzySearch.partialRatio(album.id.displayName, title)
                if (matchRatio < 85 || !title.contains("Full Album", true) || videoId == null) {
                    return@map null
                }
                info { "youtube: potential album match '$title' at $videoId" }
//                task(UI) { println("youtube: potential result matches ${matchRatio}%") }

//                    object : YouTubeExtractorOld(App.instance) {
//                        override fun onExtractionComplete(streams: SparseArray<YtFile>?, meta: VideoMeta?) {
//                            if (streams == null) return
//                            if (meta == null) return
//                            val duration = meta.videoLength
//                            val desc = meta.
//                        }
//                    }.extract("http://youtube.com/watch?v=$videoId", true, true)
//                val descTracks = task { grabFromVideo(album, videoId) }
//                val commTracks = task { grabFromComments(album, videoId) }
//                descTracks to commTracks
                grabFromPage(album, videoId)
//            }.flatMap { listOf(it?.first?.await(), it?.second?.await()) }
            }.mapNotNull { it }
                .sortedByDescending { it.tracks.size }

            return results.firstOrNull()
        }

        /// TODO: Turn this into a select expr
        suspend fun grabFromPage(album: Album, videoId: String): YouTubeFullAlbum?
            = grabFromVideo(album, videoId) ?: grabFromComments(album, videoId)

        private suspend fun grabFromComments(album: Album, videoId: String): YouTubeFullAlbum? = run {
            val res = Http.get("https://www.googleapis.com/youtube/v3/commentThreads", params = mapOf(
                "key" to BuildConfig.YOUTUBE_API_KEY,
                "part" to "snippet",
                "videoId" to videoId,
                "maxResults" to "4",
                "order" to "relevance",
                "textFormat" to "plainText"
            )).gson.obj

            res["items"].array.map { it.obj }.mapNotNull {
                tryOr(null) {
                    val info = it["snippet"]["topLevelComment"]["snippet"].obj
                    val desc = info["textDisplay"].string

                    val videoTracks = tracksFromDesc(desc, Int.MAX_VALUE)
//                async(UI) {
//                    println("youtube: video tracks = $videoTracks")
//                }

                    val tracks = album.tracks.mapNotNull { song ->
                        val (ratio, vidTrack) = videoTracks.map {
                            val ratio = FuzzySearch.ratio(it.title.toLowerCase(), song.id.displayName.toLowerCase())
                            ratio to it
                        }.maxBy { it.first } ?: return@mapNotNull null

                        if (ratio > 80) {
                            info { "youtube: mapping song '${song.id.name}' to section ${vidTrack.start}-${vidTrack.end} called '${vidTrack.title}'" }
                            song to vidTrack
                        } else null
                    }.toMap()

                    // Map tracks from description

                    YouTubeFullAlbum(album, "", videoId, Int.MAX_VALUE, tracks, null)
                }
            }.maxBy { it.tracks.size }
        }

        private suspend fun grabFromVideo(album: Album, videoId: String): YouTubeFullAlbum? = run {
            // Unfortunately, to grab the description we have to parse the video page :(
            val pageRes = Http.get("https://youtube.com/watch?v=$videoId", cacheLevel = Http.CacheLevel.PAGE).text
            val descStart = "<p id=\"eow-description\" class=\"\" >"
            val descStartIdx = pageRes.indexOf(descStart) + descStart.length
            val descEndIdx = pageRes.indexOf("</p>", startIndex=descStartIdx)
            val descRaw = pageRes.substring(descStartIdx, descEndIdx)

            val pretty = Jsoup.clean(
                descRaw,
                "",
                Whitelist.none().addTags("br", "p"),
                Document.OutputSettings().prettyPrint(true)
            )
            val desc = Jsoup.clean(
                pretty,
                "",
                Whitelist.none(),
                Document.OutputSettings().prettyPrint(false)
            )

//                task(UI) { println("youtube: album desc = $desc") }


            val lengthStart = "\"length_seconds\":"
            val lengthStartIdx = pageRes.indexOf(lengthStart) + lengthStart.length + 1
            val lengthEndIdx = pageRes.indexOf("\"", lengthStartIdx)
            val durationStr = pageRes.substring(lengthStartIdx, lengthEndIdx)
            val duration = durationStr.toInt() * 1000

            val goalDuration = album.tracks.sumBy { it.duration }
            val durationDiff = duration - goalDuration
            if (Math.abs(durationDiff) <= 5500) { // Duration is just about the same
                // So, just map the tracks in order.
                // This is the mapping *least* likely to be correct.
                val tracks = mutableMapOf<Song, Track>()
                // Add the average difference to each song, which should be < 1 second
                val durationDiscrepancy = durationDiff / album.tracks.size
                var currStart = 0
                for (song in album.tracks) {
                    val duration = song.duration + durationDiscrepancy
                    tracks[song] = Track("", currStart, currStart + duration)
//                    val vidTrack = song.copy(remote = if (song.remote != null) {
//                        song.remote.copy(start = currStart)
//                    } else {
//                        Song.RemoteDetails(null, null, null, start = currStart)
//                    }, duration = song.duration + durationDiscrepancy)
//                    tracks.add(vidTrack)
                    currStart += duration
                }

                YouTubeFullAlbum(album, "", videoId, duration, tracks, null)
            } else {

                val videoTracks = tracksFromDesc(desc, duration)

//                async(UI) {
//                    println("youtube: video tracks = $videoTracks")
//                }

                val tracks = album.tracks.mapNotNull { song ->
                    val (ratio, vidTrack) = videoTracks.mapIndexed { idx, track ->
                        var ratio = FuzzySearch.ratio(track.title.toLowerCase(), song.id.displayName.toLowerCase())
                        if (idx + 1 == song.track) {
                            ratio += 3
                        }
                        ratio to track
                    }.maxBy { it.first } ?: return@mapNotNull null

                    if (ratio > 80) {
                        info { "youtube: mapping song '${song.id.displayName}' to section ${vidTrack.start}-${vidTrack.end} called '${vidTrack.title}'" }
                        val videoSongDuration = vidTrack.end - vidTrack.start
                        val newDurationCorrect = abs(song.duration - videoSongDuration) < TimeUnit.MINUTES.toMillis(1)
                        val end = if (newDurationCorrect) {
                            vidTrack.end
                        } else {
                            vidTrack.start + song.duration
                        }
                        song to vidTrack.copy(end = end)
//                        val remote = if (song.remote != null) {
//                            song.remote.copy(start = vidTrack.start)
//                        } else {
//                            Song.RemoteDetails(null, null, null, start = vidTrack.start)
//                        }
//                        song.copy(remote = remote, duration = if (newDurationCorrect || song.duration <= 0) videoSongDuration else song.duration)
                    } else null
                }.toMap()

                // Map tracks from description

                YouTubeFullAlbum(album, "", videoId, duration, tracks, null)
            }
        }

        private fun tracksFromDesc(desc: String, duration: Int) = run {
            // Find lines with track start times and sort them by the start time
            val lines = desc.lines()
            // pattern for time signatures: h:m:s or m:s or m.s or h.m.s (dots for potential typos)
//                    val timePatStart = Pattern.compile("^((\\d{1,2}):)?(\\d{1,2})[:.](\\d{2})\\b")
//                    val prev: Track? = null
            var prevIdx = -1
            val trackLines = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val hms = HMS_PAT.matcher(line)
                    val ms = MS_PAT.matcher(line)
                    when {
                        hms.find() -> {
                            val sec = hms.group(3).toInt()
                            val min = hms.group(2).toInt()
                            val hr = hms.group(1).toInt()
                            val start = (((hr * 60 + min) * 60) + sec) * 1_000
                            start to hms.replaceFirst("").trim().replace(Regex("^\\d+\\s*[.-]\\s*"), "")
                        }
                        ms.find() -> {
                            val min = ms.group(1).toInt()
                            val sec = ms.group(2).toInt()
                            val start = ((min * 60) + sec) * 1_000
                            start to ms.replaceFirst("").trim().replace(Regex("^\\d+\\s*[.-]\\s*"), "")
                        }
                        else -> null
                    }
                }.filterIndexed { idx, time ->
                    if (prevIdx != -1 && idx > prevIdx + 2) {
                        false
                    } else {
                        prevIdx = idx
                        true
                    }
                }

            val videoTracks = (0 until trackLines.size).mapNotNull { idx ->
                val (start, title) = trackLines[idx]
                val end = trackLines.getOrNull(idx + 1)?.first

                when {
                    idx > 0 && start == 0 -> null
                    end == null && idx == 0 -> null
                    end == null || end == 0 -> Track(title, start, duration)
                    else -> Track(title, start, end)
                }
            }

            if (videoTracks.isNotEmpty()) {
                val titles = videoTracks.map { it.title.trim() }
                val suffix = titles.longestSharedSuffix(true) ?: ""
                val prefix = titles.longestSharedPrefix(true) ?: ""
                task(UI) { println("youtube: prefix = $prefix, suffix = $suffix, titles: $titles") }
//                    var longestCommonSuffix = -1
//                    val first = videoTracks[0]
//                    do {
//                        longestCommonSuffix += 1
//                        val c = first.name[first.name.length - 1 - longestCommonSuffix]
//                        val all = videoTracks.all { it.name[it.name.length - 1 - longestCommonSuffix] == c }
//                    } while (all)

                if (suffix.isNotEmpty() || prefix.isNotEmpty()) {
                    videoTracks.forEach {
                        it.title = it.title.substring(prefix.length, it.title.length - suffix.length)
                    }
                }
            }

            videoTracks
        }
    }
}