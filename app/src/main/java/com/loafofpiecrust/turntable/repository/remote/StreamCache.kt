package com.loafofpiecrust.turntable.repository.remote

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.github.ajalt.timberkt.Timber
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.dbKey
import com.loafofpiecrust.turntable.repository.StreamProvider
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.tryOr
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

object StreamCache: StreamProvider {
    val mapper: DynamoDBMapper by lazy {
        DynamoDBMapper.builder()
            .dynamoDBClient(OnlineSearchService.instance.database)
            .build()
    }

    override suspend fun sourceForSong(song: Song): Song.Media? {
        val key = song.id.dbKey
        return when (val entry = getExistingEntry(key)) {
            is StreamStatus.Available -> {
                if (entry.isStale) {
                    null
                } else {
                    Song.Media.fromYouTube(entry.stream, entry.hqStream, entry.expiryDate)
                }
            }
            // prevent the system from searching beyond this.
            // TODO: Provide an explicit possible return value for this case!
            is StreamStatus.Unavailable -> throw Exception()
            is StreamStatus.Unknown -> null
        }
    }

    private fun getExistingEntry(key: String): StreamStatus {
        val entry = tryOr(null) {
            mapper.load(SongDBEntry::class.java, key)
        }
        return StreamStatus.from(entry)
    }

    fun save(song: Song, media: Song.Media?) {
        val status = if (media != null) {
            StreamStatus.Available(
                song.id.dbKey,
                null,
                stream = media.mediocreSource()!!.url,
                hqStream = media.bestSource()?.url,
                expiryDate = media.expiryDate
            )
        } else {
            StreamStatus.Unavailable(song.id.dbKey)
        }

        GlobalScope.launch {
            try {
                mapper.save(status.toDatabaseEntry())
            } catch (e: Exception) {
                Timber.e(e) { "Failed to save song to database" }
            }
        }
    }


    sealed class StreamStatus {
        protected abstract val key: String
        abstract fun toDatabaseEntry(): SongDBEntry?

        object Unknown: StreamStatus() {
            override val key: String
                get() = ""

            override fun toDatabaseEntry(): SongDBEntry? = null
        }

        class Unavailable(
            override val key: String
        ): StreamStatus() {
            override fun toDatabaseEntry() = SongDBEntry(
                key,
                youtubeId = null,
                expiryDate = System.currentTimeMillis() + OnlineSearchService.STALE_UNAVAILABLE_AGE.toMillis().toLong()
            )
        }

        data class Available(
            override val key: String,
            val youtubeId: String?,
            val stream: String,
            val hqStream: String? = null,
            val expiryDate: Long = System.currentTimeMillis() + OnlineSearchService.STALE_ENTRY_AGE.toMillis().toLong()
        ): StreamStatus() {
            val isStale: Boolean get() =
                System.currentTimeMillis() > expiryDate

            override fun toDatabaseEntry() = SongDBEntry(
                key,
                youtubeId = youtubeId,
                expiryDate = expiryDate,
                stream128 = stream,
                stream192 = hqStream
            )
        }

        companion object {
            fun from(entry: SongDBEntry?): StreamStatus = when {
                entry == null -> Unknown
                entry.stream128 == null -> Unavailable(entry.id)
                else -> tryOr(Unknown) {
                    Available(
                        entry.id,
                        entry.youtubeId,
                        entry.stream128!!,
                        entry.stream192,
                        if (entry.stream128 != null || entry.stream192 != null) {
                            entry.expiryDate
                        } else {
                            0 // force entries without stream urls, but *with* a videoId to be stale.
                        }
                    )
                }
            }
        }
    }

    @DynamoDBTable(tableName = "MusicStreams")
    data class SongDBEntry(
        @DynamoDBHashKey(attributeName = "SongId")
        var id: String,
        var youtubeId: String?,
        var expiryDate: Long,
        var stream128: String? = null,
        var stream192: String? = null
    ) {
        private constructor(): this("", null, 0)
    }
}