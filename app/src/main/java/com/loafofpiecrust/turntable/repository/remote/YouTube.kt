package com.loafofpiecrust.turntable.repository.remote

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.StreamProvider
import com.loafofpiecrust.turntable.service.OnlineSearchService


/**
 * TODO: Move implementation from OnlineSearchService to here.
 * TODO: Split impls for retrieving from: DynamoDB, YouTube Song search, YouTube Album search
 */
object YouTube: StreamProvider {
    override suspend fun sourceForSong(song: Song): Song.Media? {
        val streams = OnlineSearchService.instance.getSongStreams(song)
        return if (streams.status is OnlineSearchService.StreamStatus.Available) {
            Song.Media(
                streams.status.hqStream ?: streams.status.stream,
                start = streams.start,
                end = streams.end
            )
        } else null
    }
}