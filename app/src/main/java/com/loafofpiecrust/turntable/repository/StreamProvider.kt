package com.loafofpiecrust.turntable.repository

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.remote.YouTube
import com.loafofpiecrust.turntable.repository.remote.StreamCache

/**
 * Provides streaming urls for a song.
 */
interface StreamProvider {
    suspend fun sourceForSong(song: Song): Song.Media?
}

object StreamProviders: StreamProvider {
    private val LIST: Array<StreamProvider> = arrayOf(
        LocalApi,
        StreamCache
    )

    private val REMOTE: Array<StreamProvider> = arrayOf(
        YouTube
    )

    override suspend fun sourceForSong(song: Song): Song.Media? {
        return try {
            for (provider in LIST) {
                val source = provider.sourceForSong(song)
                if (source != null) {
                    return source
                }
            }

            var finalSource: Song.Media? = null
            for (provider in REMOTE) {
                val source = provider.sourceForSong(song)
                if (source != null) {
                    finalSource = source
                }
            }

            StreamCache.save(song, finalSource)
            finalSource
        } catch (e: Exception) {
            null
        }
    }
}