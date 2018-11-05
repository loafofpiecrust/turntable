package com.loafofpiecrust.turntable.repository

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.remote.YouTube

/**
 * Provides streaming urls for a song.
 */
interface StreamProvider {
    suspend fun sourceForSong(song: Song): Song.Media?
}

object StreamProviders: StreamProvider {
    private val LIST: Array<StreamProvider> = arrayOf(
        LocalApi,
        YouTube
    )

    override suspend fun sourceForSong(song: Song): Song.Media? {
        for (provider in LIST) {
            val source = provider.sourceForSong(song)
            if (source != null) {
                return source
            }
        }
        return null
    }
}