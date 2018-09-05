package com.loafofpiecrust.turntable.song

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import com.github.salomonbrys.kotson.*
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.artist.MusicDownload
import com.loafofpiecrust.turntable.parMap
import com.loafofpiecrust.turntable.provided
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.downloadManager
import java.util.regex.Pattern
import kotlin.math.abs

sealed class SongDownload: MusicDownload

data class YouTubeSong(
    val goal: Song,
    val title: String,
    val id: String,
    val duration: Int
) : SongDownload() {

    companion object: AnkoLogger by AnkoLogger<YouTubeSong>() {
        val API_KEY = BuildConfig.YOUTUBE_API_KEY
        private val PAT_UNOFFICIAL by lazy {
            Pattern.compile("\\b(live|remix|mix|cover|unofficial|instrumental|sessions)\\b")
        }
        private val PAT_TIME_S by lazy { Pattern.compile("PT(\\d+)S") }
        private val PAT_TIME_M by lazy { Pattern.compile("PT(\\d+)M") }
        private val PAT_TIME_MS by lazy { Pattern.compile("PT(\\d+)M(\\d+)S") }
        private val PAT_TIME_HMS by lazy { Pattern.compile("PT(\\d+)H(\\d+)M(\\d+)S") }

        suspend fun search(song: Song): YouTubeSong? {
//            val ub = Uri.parse("http://youtube.com/results?sp=EgIQAVAU").buildUpon()
//            ub.appendQueryParameter("q", song.name + " " + song.artist)
////        val query = (song.name + " " + song.artist).withReplaced(' ', '+').withReplaced("&", "%26").withReplaced(",", "%2C")
//            val time = System.currentTimeMillis()
////            println("albumyt: searching with '${ub.toString()}'")
//            val res = Jsoup.connect(ub.build().toString())
//                .timeout(10000)
//                .get()
//            var lastTime = System.currentTimeMillis()
////            println("video request took ${lastTime - time}ms")
//            val resultList = try {
//                res.getElementById("results").child(0).child(1).child(0)
//            } catch (e: Exception) {
//                return null
//            }
////            println("video results: ${resultList.children().size}")
//            val videos = resultList.children().map {
//                val item = try {
//                    it.child(0).child(0)
//                } catch(e: Exception) { return@map null }
//                val thumb = item.child(0)
//                val info = item.child(1)
//
//                // duration of the song IN SECONDS
//                val duration: Int = try {
//                    thumb.child(0).child(0).child(0).child(1).text().let {
//                        val colon = it.split(":")
//                        val mins = colon[0].toInt()
//                        val secs = colon[1].toInt()
//                        (mins * 60 + secs) * 1000
//                    }
//                } catch(e: Exception) { return@map null }
//                if (duration > (song.duration + 20_000) || duration < (song.duration - 20_000)) {
//                    // the song duration is not _nearly_ the same.
//                    return@map null
//                }
//
////            val name = info.child(0).child(0)
//                val name = item.getElementsByClass("yt-lockup-name").get(0).child(0)
//                val titleText = name.text()
//                val videoUrl = name.attr("href")
//
//                // Ignore live versions
//                val liveReg = Regex("\\bLIVE\\b", RegexOption.IGNORE_CASE)
//                if (titleText.contains(liveReg) && !song.name.contains(liveReg)) {
//                    return@map null
//                }
//
////                println("video result: '$titleText' at '$videoUrl'")
//                // TODO: Check the duration for similarity as well
//                val hasSongName = titleText.contains(song.name, ignoreCase = true)
//                val hasArtistName = titleText.contains(song.artist, ignoreCase = true)
////            val correctness = if (hasSongName && hasArtistName) { 100 } else { 0 }
//                YouTubeSong(song, titleText, videoUrl, duration)
//            }.filterNotNull() //.sortedByDescending { it.correctness }
//            lastTime = System.currentTimeMillis()
////            println("video results took ${lastTime - time}ms total")
//            // TODO: More extensive vetting here
//            return videos.firstOrNull()

            val otherArtist = if (song.id.artist.displayName != song.id.album.artist.altName) {
                song.id.album.artist.altName ?: ""
            } else ""
            val query = if (song.id.artist.displayName.length <= 50) {
                "${song.id.displayName} ${song.id.artist} $otherArtist"
            } else {
                "${song.id.displayName} ${song.id.album.displayName}"
            }.replace('&', ' ').trim()

//            return null


            val res = Http.get("https://www.googleapis.com/youtube/v3/search", params = mapOf(
                "key" to API_KEY,
                "part" to "snippet",
                "type" to "video",
                "q" to query,
                "maxResults" to "7"//,
//                "videoSyndicated" to "true",
//                "videoEmbeddable" to "true"
            )).gson.obj


            debug { "youtube: query = '$query'" }

            val items = res["items"].array
            val results = items.mapIndexed { idx, it -> idx to it.obj }.parMap { (idx, it) ->
                try {
                    // TODO: Reincorporate filtering by duration
                    val details = it["snippet"].obj
                    val title = details["title"].string.toLowerCase()

                    // Unless specifically requested, ignore live versions, mixes, covers, etc.
                    val m = PAT_UNOFFICIAL.matcher(title)
                    if (m.find()) {
                        if (!song.id.name.contains(m.group(1), ignoreCase = true)) {
                            return@parMap null
                        }
                    }

                    val partialRatio = FuzzySearch.partialRatio(song.id.name.toLowerCase(), title)
//                    val weightedRatio = FuzzySearch.weightedRatio(song.name.toLowerCase(), name.toLowerCase())
                    var matchRatio = partialRatio
                    if (matchRatio < 75) {
                        return@parMap null
                    }

                    val desc = details["description"].nullString ?: ""
                    val channel = details["channelTitle"].nullString?.toLowerCase() ?: ""
                    val videoId = it["id"]["videoId"].string


                    // Also check for _dates_ in the name, as those are almost always live sessions!
                    val datePat = Pattern.compile("((\\d+/\\d+/\\d+)|(\\d+-\\d+-\\d+)|(\\d+.\\d+.\\d+))").matcher(title)
                    if (datePat.find()) {
                        if (!song.id.name.contains(datePat.group(1))) {
                            return@parMap null
                        }
                    }

                    // Some songs will be the id of the album! So, don't load the whole album
                    // if we have no duration to tell us not to.
//                val albumPat = Pattern.compile("\\b(Full\\s+Album)\\b", Pattern.CASE_INSENSITIVE).matcher(name)
//                if (albumPat.find()) {
//                    if (!song.name.contains(albumPat.group(1), true)) {
//                        return@parMap null
//                    }
//                }

                    // Fucking YouTube. We still need the video duration to filter by and confirm sameness.
                    val res = Http.get("https://www.googleapis.com/youtube/v3/videos", params = mapOf(
                        "key" to API_KEY,
                        "id" to videoId,
                        "part" to "contentDetails,statistics"
                    )).gson.obj
                    val item = res["items"][0]

                    // If the video has over 10000 views,
                    // it's more likely to have higher quality audio streams
                    val viewCount = item["statistics"]["viewCount"].long
                    matchRatio += when {
                        viewCount > 100_000 -> 8
                        viewCount > 10_000 -> 5
                        else -> minOf(viewCount.toInt() / 1000, 5)
                    }
                    val durationStr = item["contentDetails"]["duration"].string
                    val onlyMin = PAT_TIME_M.matcher(durationStr)
                    val ms = PAT_TIME_MS.matcher(durationStr)
                    val hms = PAT_TIME_HMS.matcher(durationStr)
                    val onlySec = PAT_TIME_S.matcher(durationStr)
                    val duration = when {
                        ms.find() -> {
                            val min = ms.group(1).toInt()
                            val sec = ms.group(2).toInt()
                            (sec + min * 60) * 1_000 // ms
                        }
                        onlyMin.find() -> {
                            val min = onlyMin.group(1).toInt()
                            min * 60_000
                        }
                        hms.find() -> {
                            val hr = hms.group(1).toInt()
                            val min = hms.group(2).toInt()
                            val sec = hms.group(3).toInt()
                            (sec + (min + hr * 60) * 60) * 1_000 // ms
                        }
                        onlySec.find() -> {
                            val sec = onlySec.group(1).toInt()
                            sec * 1_000 // ms
                        }
                        else -> {
                            debug { "youtube: unknown duration '$durationStr'" }
                            0
                        }
                    }


                    val durationDiff = abs(duration - song.duration)
                    // TODO: Possibly tighten this filter
                    if (song.duration > 0 && duration > 0 && durationDiff > 90_000) {
                        // Allow 1 minute discrepancy for different listings of the song
                        // the song duration is not _nearly_ the same.
                        return@parMap null
                    }


                    matchRatio -= if (song.duration > 0) {
                        if (duration > 0) {
                            durationDiff / 3000
                        } else 5
                    } else 0
                    matchRatio -= idx

                    // Check for the artist
                    val artist = song.id.artist.displayName.toLowerCase()
                    val artistMatch = maxOf(
                        FuzzySearch.partialRatio(artist, title),
                        FuzzySearch.partialRatio(artist, channel)
                    )
                    matchRatio -= when {
                        artistMatch >= 85 -> 0
                        desc.contains(artist, true) -> 5
                        else -> 10
                    }

                    // Check for the album artist if it's different and not "Various Artists"
                    val albumArtist = song.id.album.artist.name.toLowerCase()
                    if (artist != albumArtist && albumArtist != "various artists") {
                        val artistMatch = maxOf(
                            FuzzySearch.partialRatio(albumArtist, title),
                            FuzzySearch.partialRatio(albumArtist, channel)
                        )
                        if (artistMatch > 85 || desc.contains(albumArtist, true)) {
                            matchRatio += 7
                        }
                    }

                    // Check for the specific album
                    val album = song.id.album.displayName
                    val albumMatch = FuzzySearch.partialRatio(album, title)
                    if (albumMatch > 85 || desc.contains(album, true)) {
                        matchRatio += 5
                    }

                    debug { "youtube: possibly '$title' is ${duration}ms, match=$matchRatio, id=$videoId" }

                    // TODO: Prioritize shit that has the artist id in the name and/or description
                    // TODO: Check for album id in description, if it's there prioritize. If not, don't penalize

                    (matchRatio to YouTubeSong(song, title, videoId, duration))
                        .provided { matchRatio >= 84 }
                } catch (e: Exception) {
                    debug { e.stackTrace }
                    null
                }
            }.mapNotNull { it.await() }.sortedByDescending { (match, _) -> match }
            val choice = results.firstOrNull()?.second

            if (choice != null) {
                debug { "youtube: picked '${choice.title}' is ${choice.duration}ms, id=${choice.id}" }
            }
            return choice
        }
    }

    override suspend fun download() {
//
//        val song = goal
//        val infoUrl = "http://www.youtubeinmp3.com/fetch/?format=JSON&video=https://www.youtube.com$url"
//        val infoRes = Jsoup.connect(infoUrl)
//            .method(Connection.Method.GET)
//            .ignoreContentType(true)
//            .execute()
//        val body = infoRes.body()
//
////        println("albumyt: response is ${infoRes.contentType()}")
//        val downloadUrl = if (body.startsWith("{")) {
//            // Song is already converted. Let's download!
//            val json = JSONObject(body)
//            json.getString("link")
//        } else {
//            // Song needs converting.
//            val res = Jsoup.connect("http://www.youtubeinmp3.com/download/?video=https://www.youtube.com$url")
//                .timeout(10000)
//                .get()
//            val id = "http://www.youtubeinmp3.com" + res.getElementById("download").attr("href")
//            // Tell the server to generate the mp3 plox (clicking the generate button)
//            Jsoup.connect(id)
//                .method(Connection.Method.GET)
//                .execute()
//
//            delay(6000)
//            do {
//                delay(1000)
//                val infoRes = Jsoup.connect(infoUrl)
//                    .method(Connection.Method.GET)
//                    .ignoreContentType(true)
//                    .execute()
//                val body = infoRes.body()
//            } while (!body.startsWith("{"))
//
//            "http://www.youtubeinmp3.com/fetch/?video=https://www.youtube.com$url"
//        }
        val s = OnlineSearchService.instance.getSongStreams(goal)
        val streams = s.status as? OnlineSearchService.StreamStatus.Available ?: return
        val downloadUrl = streams.hqStream ?: streams.stream

//        println("albumyt song id: $downloadUrl")
        val req = DownloadManager.Request(Uri.parse(downloadUrl))
        req.setTitle(title)
//        req.setTitle("Downloading YT song")

        req.allowScanningByMediaScanner()
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        req.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MUSIC,
            "${goal.id.filePath}.mp3"
        )

//        launch(UI) {
            val dlManager = App.instance.downloadManager
            val id = dlManager.enqueue(req)
//        }
    }
}