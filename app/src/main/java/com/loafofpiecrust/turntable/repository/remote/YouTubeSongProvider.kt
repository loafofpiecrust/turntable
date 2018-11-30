package com.loafofpiecrust.turntable.repository.remote

import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.StreamProvider
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson


/**
 * TODO: Move implementation from OnlineSearchService to here.
 * TODO: Split impls for retrieving from: DynamoDB, YouTube Song search, YouTube Album search
 */
object YouTubeSongProvider: StreamProvider {
    // TODO: Album-based search.
    override suspend fun sourceForSong(song: Song): Song.Media? {
//        return YouTubeSong.search(song)?.let { ytSong ->
//            val status = suspendCoroutine<OnlineSearchService.StreamStatus> { cont ->
//                YTExtractor(song.id.dbKey, ytSong.id, cont)
//                    .extract("https://youtube.com/watch?v=${ytSong.id}", true, true)
//            }
//            if (status is OnlineSearchService.StreamStatus.Available) {
//                Song.Media.fromYouTube(status.stream, status.hqStream)
//            } else null
//        }
        return fromService(song)
    }

    private suspend fun fromService(song: Song): Song.Media? = run {
        val streams = OnlineSearchService.instance.getSongStreams(song)
        if (streams.status is OnlineSearchService.StreamStatus.Available) {
            Song.Media(
                listOf(Song.Media.Source(streams.status.hqStream ?: streams.status.stream)),
                start = streams.start,
                end = streams.end
            )
        } else null
    }
}

/**
 * The stream urls returned from here have some restrictions:
 * - Only usable for a limited duration. A stream url generally lasts for 6 hours.
 * - We seem to be able to use them from multiple devices despite the url containing an original IP.
 */
object FirebaseStreamFunction: StreamProvider {
    override suspend fun sourceForSong(song: Song): Song.Media? {
        val res = Http.get("https://us-central1-turntable-3961c.cloudfunctions.net/parseStreamsFromYouTube", params = mapOf(
            "title" to song.id.displayName.toLowerCase(),
            "album" to song.id.album.displayName.toLowerCase(),
            "artist" to song.id.artist.displayName.toLowerCase(),
            "albumArtist" to song.id.album.artist.displayName.toLowerCase(),
            "duration" to song.duration.toString()
        )).gson().obj


        val lq = res["lowQuality"].nullObj?.get("url")?.string

        return if (lq == null) {
            // not available on youtube!
            null
        } else {
            val hq = res["highQuality"].nullObj?.get("url")?.string
            val expiryDate = res["expiryDate"].long
//            val id = res["id"].string
            Song.Media.fromYouTube(lq, hq)
        }
    }
}