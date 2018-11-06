package com.loafofpiecrust.turntable.repository

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.remote.StreamCache
import com.loafofpiecrust.turntable.repository.remote.FirebaseStreamFunction
import com.loafofpiecrust.turntable.tryOr

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
        FirebaseStreamFunction
    )

    override suspend fun sourceForSong(song: Song): Song.Media? {
        for (provider in LIST) {
            val source = tryOr(null) { provider.sourceForSong(song) }
            if (source != null) {
                return source
            }
        }
        for (provider in REMOTE) {
            val source = tryOr(null) { provider.sourceForSong(song) }
            if (source != null) {
                return source.also {
                    StreamCache.save(song, it)
                }
            }
        }
        return null
    }
}