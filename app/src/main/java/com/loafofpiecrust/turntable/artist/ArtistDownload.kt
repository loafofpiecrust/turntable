package com.loafofpiecrust.turntable.artist

import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jsoup.Jsoup
import java.io.File
import java.util.regex.Pattern

interface MusicDownload {
    /// Download the remote album to the default music folder
    /// TODO: Add return value or listener that remoteInfo progress, failure, and completion
    suspend fun download()
}

sealed class ArtistDownload(val quality: OnlineSearchService.Quality): MusicDownload {
    companion object {
        fun search(artist: Artist) : ArtistDownload? {
//            val tpb = async(CommonPool) { TPBAlbum.search(album) }
            val ru = task { RuTrackerArtist.search(artist) }
//            val yt = async(CommonPool) { YouTubeAlbum.search(album) }

//            val torrents = listOf(tpb, ru).map { it.await() }.filterNotNull()
//            return if (torrents.isNotEmpty()) {
//                yt.cancel()
//                torrents.sortedByDescending { it!!.value }.first()
//            } else {
//                yt.await()
//            }
            return runBlocking { ru.await().firstOrNull() }
        }
    }
    //    abstract val completeness: Int
    abstract val value: Int
}

sealed class TorrentArtist(
    private val goal: Artist,
    quality: OnlineSearchService.Quality
) : ArtistDownload(quality), AnkoLogger {
    data class AlbumResult(val fileIdx: Int, val file: File, val matchRatio: Int)
    data class SongResult(val fileIdx: Int, val matchRatio: Int)

    private var torrent: OnlineSearchService.Torrent? = null
    private val albumFiles = HashMap<Album, Map<Song, Int>>()

    abstract suspend fun retrieveInfo(sess: SessionManager): TorrentInfo?

    override suspend fun download() {
        if (torrent != null) {
            // Download is already started.
            return
        }
//        val magnet = magnet
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
                        sess.info = alert.handle().torrentFile()
                        sess.handle = h
//                        h.pause()
//                        prioritizeAll() // Then, re-enable the files of the album(s) we know to download.
                        h.resume() // Resume downloading.
                        buildSongIndex() // Map the albums we know of to files in this torrent
                        dontDownloadAllFiles() // Disable all files in the torrent
                    }
                    is BlockFinishedAlert -> {
                        val p = alert.handle().status().progress() * 100
                        sess._progress.offer(p.toInt())
                        println("torrent: progress $p")
                    }
                    is TorrentFinishedAlert -> {
                        println("torrent finished")
                        val ti = sess.info
                        if (ti != null) {
                            // Add the files to the MediaStore?

                        }
//                        sess.stop()
                    }
                }
            }
            override fun types(): IntArray? = null
        })

        sess.start()
//        sess.startDht()

        val info = retrieveInfo(sess)
        sess.info = info
        if (info != null) {
            val orig = info.origFiles()
            val files = info.files()
//            for (i in 0 until orig.numFiles()) {
////                ti?.
//                val path = orig.filePath(i)
//                val id = orig.fileName(i)
//                println("torrent origfile: $id, $path")
////                                val values = ContentValues()
////                                values.put(MediaStore.MediaColumns.DATA, path)
////                                val success = contentResolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values, )
//            }
            sess.progress.consumeEach(UI) {
                println("torrent: progress $it")
            }
//            for (i in 0 until files.numFiles()) {
//                val path = files.filePath(i)
//                val id = files.fileName(i)
//                println("torrent file: $id, $path")
//            }
            println("torrent info: $info")
            // TODO: Use an Environment.getPath type thing, and the external Music dir or whatever has more space (Check free space?)
            sess.download(info, File("/storage/emulated/0/Music"))
            println("torrent past dl")
        } else {
            // Couldn't get the seeders quick enough
        }
    }

    /// Map the albums on the intended artist to the album folders in this torrent.
    private fun buildSongIndex() {
        val tor = torrent ?: return
        val info = tor.info ?: return
        val files = info.origFiles()
        val albums = goal.albums
        val paths = (0 until files.numFiles()).map { it to File(files.filePath(it)) }

        debug { "torrent: mapping ${albums.size} albums against ${files.numFiles()} files" }


        val folders = paths.groupBy { it.second.parentFile }

        albumFiles.putAll(albums.map { album ->
            debug { "torrent: mapping album '${album.id}'" }
            val (albumFolder, songFiles) = folders.asSequence()
                .maxBy { (folder, files) ->
                    // This means 2 folders that match can be distinguished by year
                    // Should also help distinguish between singles, EPs, and LPs
                    val year = album.year
                    val yearBias = if (year != null && year > 0 && folder.name.contains(year.toString())) 1 else 0
                    FuzzySearch.partialRatio(album.id.displayName, folder.name) + yearBias
                }!!

            debug { "torrent: album map: '${album.id}' to '${albumFolder.name}'" }
//            album to songFiles.map { it.first }
            album to mapOf(*album.tracks.mapNotNull { song ->
                val (ratio, songFile) = songFiles.map { (idx, file) ->
                    FuzzySearch.partialRatio(song.id.name, file.name) to (idx to file)
                }.maxBy { (matchRatio, rest) ->
                    matchRatio
                }!!
                val (idx, file) = songFile
                debug { "torrent: song map: '${song.id}' to '${file.name}'" }
                if (ratio > 50) {
                    song to idx
                } else { // Nowhere near a match
                    null
                }
            }.toTypedArray())
        })
        debug { "torrent: done mapping albums" }
    }

    private fun indexAlbum(album: Album): Map<Song, Int>? {
        val existing = albumFiles[album]
        if (existing != null) return existing

        val tor = torrent ?: return null
        val info = tor.info ?: return null
        val files = info.files()
        val paths = (0 until files.numFiles()).map { it to File(files.filePath(it)) }.toMutableList()

        val albumRes = paths
            .filter { (fileIdx, file) ->
                file.isDirectory
            }.mapIndexed { idx, (fileIdx, file) ->
            idx to AlbumResult(fileIdx, file, FuzzySearch.partialRatio(album.id.displayName, file.name))
        }.maxBy { (idx, res) ->
            // This means 2 folders that match can be distinguished by year
            // Should also help distinguish between singles, EPs, and LPs
            val year = album.year ?: 0
            val yearBias = if (year > 0 && res.file.name.contains(year.toString())) 1 else 0
            res.matchRatio + yearBias
        }!!

        val res = if (albumRes.second.matchRatio >= 50) {
            paths.removeAt(albumRes.first)
            mapOf(*album.tracks.mapNotNull { song ->
                val songRes = paths.filter { (fileIdx, file) ->
                    file.startsWith(albumRes.second.file) // song is in this album folder
                }.mapIndexed { idx, (fileIdx, file) ->
                    idx to SongResult(fileIdx, FuzzySearch.partialRatio(song.id.name, file.name))
                }.maxBy { (idx, res) -> res.matchRatio }!!
                if (songRes.second.matchRatio < 50) {
                    null
                } else {
                    song to songRes.second.fileIdx
                }
            }.toTypedArray())
        } else {
            null
        }

        if (res != null) albumFiles[album] = res
        return res
    }

    fun prioritize(album: Album) {
        val tor = torrent ?: return
        val handle = tor.handle ?: return
        val songIndices = indexAlbum(album) ?: return
        for ((song, idx) in songIndices) {
            handle.setFilePriority(idx, Priority.SIX)
        }
    }
    private fun prioritize(song: Song, album: Album) {
        val songIndices = indexAlbum(album) ?: return
        val idx = songIndices[song] ?: return
        torrent?.handle?.setFilePriority(idx, Priority.SIX)
    }
    fun prioritize(song: Song) {
        val album = albumFiles.keys.find { it.id == song.id.album } ?: return
        prioritize(song, album)
    }

    private fun prioritizeAll() {
        val tor = torrent ?: return
        val handle = tor.handle ?: return
        for ((album, songs) in albumFiles) {
            for ((song, idx) in songs) {
                debug { "torrent: setting priority of $idx to high" }
                handle.setFilePriority(idx, Priority.SIX)
            }
        }
    }

    fun dontDownloadAllFiles() {
        val tor = torrent ?: return
        val info = tor.info ?: return
        val handle = tor.handle ?: return
        val files = info.files()
        val indicesToKeep = albumFiles.values.flatMap { it.values }
        (0 until files.numFiles()).filter { !indicesToKeep.contains(it) }.forEach {
            handle.setFilePriority(it, Priority.IGNORE)
        }
    }

    fun start() = torrent?.handle?.resume()
    fun pause() = torrent?.handle?.pause()
}

class RuTrackerArtist private constructor(
    goal: Artist,
    val id: String,
    val title: String,
    val seeders: Int,
    val size: Long,
    val years: IntRange?,
    quality: OnlineSearchService.Quality
) : TorrentArtist(goal, quality) {
    override val value: Int
        get() = quality.ordinal * 3 + seeders

    override suspend fun retrieveInfo(sess: SessionManager): TorrentInfo? {
        val cookie = OnlineSearchService.instance.login()
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
        val magnet = info.child(4).child(1).getElementsByTag("a").first().attr("href")

        do {
            val nodes = sess.stats().dhtNodes()
            if (nodes < 10) {
                Thread.sleep(500)
            }
        } while (nodes < 10)

        val magnetData = sess.fetchMagnet(magnet, 30)
        return if (magnetData != null) {
            TorrentInfo.bdecode(magnetData)
        } else {
            null
        }
    }

    companion object {
        suspend fun search(artist: Artist): List<RuTrackerArtist> {
            val cookie = OnlineSearchService.instance.login() ?: return listOf()

            val res = Jsoup.connect("http://rutracker.org/forum/tracker.php")
                .data(mapOf(
                    "nm" to "${artist.id.name} discography", // search text
//                    "f" to "category",
                    "o" to "${OnlineSearchService.OrderBy.SEEDERS.code}", // Sort by # of seeds
                    "s" to "${OnlineSearchService.SortOrder.DESC.code}"
                    // "start" to pageNumber * 50
                    // "f" to forumIndexNumber
                ))
//                .cookie(cookie.key, cookie.value as String)
                .timeout(10000) // 10s
                .post()
                .body()

            val entries = res.getElementById("tor-tbl").child(1).children()
            val results = entries.map {
                // No results
                if (it.children().size < 2) {
                    return@search listOf()
                }

                val torrentName = it.child(3)?.child(0)?.child(0)?.text()
                println("we did it, maybe: $torrentName")

                // Can't really use flac image+.cue at all.
                if (torrentName?.contains(".cue") == true) {
                    return@map null
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
                    // TODO: For unknown, estimate quality by total size / total duration
                    when {
                        m.find() -> {
                            val bitrate = m.group(1).toInt()
                            when (bitrate) {
                                in 0..191 -> OnlineSearchService.Quality.AWFUL
                                in 192..255 -> OnlineSearchService.Quality.LOW
                                in 256..319 -> OnlineSearchService.Quality.MEDIUM
                                else -> OnlineSearchService.Quality.HIGH
                            }
                        }
                        isMp3 -> OnlineSearchService.Quality.HIGH // Generally, assume 320kbps
                        else -> OnlineSearchService.Quality.UNKNOWN
                    }
                } else {
                    OnlineSearchService.Quality.LOSSLESS
                }
                // Year range
                val m = Pattern.compile("(\\d{4})\\s*-\\s*(\\d{4})").matcher(torrentName)
                val years = if (m.find()) {
                    m.group(1).toInt()..m.group(2).toInt()
                } else {
                    null
                }

                // No seeders, don't even try.
                val seedCount = seeders.toInt()
                if (seedCount <= 0) {
                    return@map null
                }

                RuTrackerArtist(artist, topicId!!, torrentName, seeders.toInt(), size.toLong() / 1000, years, quality)
            }.filterNotNull().sortedByDescending {
                (it.quality.ordinal * OnlineSearchService.SEEDERS_OVER_QUALITY) + it.seeders
            }
            // TODO: Prioritize wider year ranges for a more encompassing discography

            println("we did it! $results")
            return results
        }
    }
}