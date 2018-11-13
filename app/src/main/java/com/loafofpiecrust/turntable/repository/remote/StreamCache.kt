package com.loafofpiecrust.turntable.repository.remote

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.dbKey
import com.loafofpiecrust.turntable.repository.StreamProvider
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.util.compress
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

object StreamCache: StreamProvider, AnkoLogger {
    val mapper: DynamoDBMapper by lazy {
        DynamoDBMapper.builder()
            .dynamoDBClient(OnlineSearchService.instance.database)
            .build()
    }

    override suspend fun sourceForSong(song: Song): Song.Media? {
        val key = song.id.dbKey
        return when (val entry = getExistingEntry(key)) {
            is OnlineSearchService.StreamStatus.Available -> {
                if (entry.isStale) {
                    null
                } else {
                    Song.Media.fromYouTube(entry.stream, entry.hqStream)
                }
            }
            is OnlineSearchService.StreamStatus.Unavailable -> null
            is OnlineSearchService.StreamStatus.Unknown -> null
        }
    }

    private fun getExistingEntry(key: String): OnlineSearchService.StreamStatus {
        val entry = tryOr(null) {
            mapper.load(OnlineSearchService.SongDBEntry::class.java, key)
        }
        return OnlineSearchService.StreamStatus.from(entry)
    }

    fun save(song: Song, media: Song.Media) = GlobalScope.launch {
        try {
            // TODO: Include both hq and lq streams in [Song.Media]
            mapper.save(OnlineSearchService.SongDBEntry(
                song.id.dbKey,
                null,
                stream128 = media.mediocreSource()!!.url.compress(),
                stream192 = media.bestSource()?.url?.compress()
            ))
        } catch (e: Exception) {
            error("Failed to save song to database", e)
        }
    }
}