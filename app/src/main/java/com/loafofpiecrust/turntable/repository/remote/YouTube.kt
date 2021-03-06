package com.loafofpiecrust.turntable.repository.remote

import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.StreamProvider
import com.loafofpiecrust.turntable.util.http
import com.loafofpiecrust.turntable.util.parameters
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url


/**
 * The stream urls returned from here have some restrictions:
 * - Only usable for a limited duration. A stream url generally lasts for 6 hours.
 * - We seem to be able to use them from multiple devices despite the url containing an original IP.
 */
object YouTube: StreamProvider {
    override suspend fun sourceForSong(song: Song): Song.Media? {
        val res = http.get<JsonObject> {
            url(BuildConfig.YOUTUBE_SONG_URL)
            parameters(
                "title" to song.id.displayName.toLowerCase(),
                "album" to song.id.album.displayName.toLowerCase(),
                "artist" to song.id.artist.displayName.toLowerCase(),
                "albumArtist" to song.id.album.artist.displayName.toLowerCase(),
                "duration" to song.duration
            )
        }

        val lqObj = res["lowQuality"].nullObj
        val lq = lqObj?.get("url")?.string?.let { url ->
            Song.Media.Source(url, Song.Media.Quality.LOW, lqObj["format"].string)
        }

        return if (lq == null) {
            // not available on youtube!
            null
        } else {
            val hqObj = res["highQuality"].nullObj
            val hq = hqObj?.get("url")?.string?.let { url ->
                Song.Media.Source(url, Song.Media.Quality.MEDIUM, hqObj["format"].string)
            }
            val expiryDate = res["expiryDate"].long
            Song.Media(
                if (hq != null) listOf(lq, hq) else listOf(lq),
                expiryDate
            )
        }
    }
}