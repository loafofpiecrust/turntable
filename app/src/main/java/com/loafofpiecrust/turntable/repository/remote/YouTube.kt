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
            header("x-api-key", BuildConfig.AWS_API_KEY)
        }

        val lq = res["lowQuality"].nullObj?.get("url")?.string

        return if (lq == null) {
            // not available on youtube!
            null
        } else {
            val hq = res["highQuality"].nullObj?.get("url")?.string
            val expiryDate = res["expiryDate"].long
            Song.Media.fromYouTube(lq, hq, expiryDate)
        }
    }
}