package com.loafofpiecrust.turntable.radio

import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.task
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.runBlocking
import kotlin.math.max

@Parcelize
class RadioQueue private constructor(
    private val seed: MutableList<Music>,
    private val recommendations: MutableList<Song>,
    override val list: List<Song>,
    override val position: Int
): MusicPlayer.Queue {
    companion object {
        private const val lookAhead = 2
        private var job: Job? = null

        suspend fun fromSeed(seed: List<Music>): RadioQueue? {
            val recs = Spotify.recommendationsFor(seed)
            return if (recs.isNotEmpty()) {
                RadioQueue(
                    seed.toMutableList(),
                    recs.drop(lookAhead + 1).toMutableList(),
                    recs.take(lookAhead + 1),
                    0
                )
            } else null
        }
    }

    override val current: Song?
        get() = list.getOrNull(position)

    override fun next(): MusicPlayer.Queue {
        runBlocking { job?.join() } // cancel any Spotify loading

        val toAdd = maxOf(0, lookAhead - list.size + position + 2)
        val newRecs = (if (toAdd > 0) {
            recommendations.drop(toAdd).toMutableList()
        } else recommendations.toMutableList())
        
        return RadioQueue(
            seed,
            newRecs.also { recs ->
                if (recs.size < lookAhead) {
                    job = task {
                        recs.addAll(Spotify.recommendationsFor(seed.shuffled().take(5)))
                    }
                }
            },
            if (toAdd > 0) {
                list + newRecs.take(toAdd)
            } else list,
            position + 1
        )
    }

    override fun prev(): MusicPlayer.Queue = if (position > 0) {
        RadioQueue(
            seed,
            recommendations,
            list,
            position - 1
        )
    } else this

    override fun shifted(from: Int, to: Int): MusicPlayer.Queue =
        RadioQueue(seed, recommendations, list.shifted(from, to), position)

    /**
     * Consider recycling songs between the current and new positions
     */
    override fun shiftedPosition(newPos: Int): MusicPlayer.Queue {
        return if (newPos > position) {
            // shift forward
            runBlocking { job?.join() } // cancel any Spotify loading

            val songsLeft = list.size - 1 - newPos
            val toAdd = maxOf(0, lookAhead - songsLeft)
            val newRecs = (if (toAdd > 0) {
                recommendations.drop(toAdd)
            } else recommendations).toMutableList()

            RadioQueue(
                seed,
                newRecs.also { recs ->
                    if (recs.size < lookAhead) {
                        job = task {
                            recs.addAll(Spotify.recommendationsFor(seed.shuffled().take(5)))
                        }
                    }
                },
                if (toAdd > 0) {
                    list + recommendations.take(toAdd)
                } else list,
                newPos
            )
        } else {
            // shift backward
            RadioQueue(
                seed,
                recommendations,
                list,
                max(newPos, 0)
            )
        }
    }

    fun addSeed(item: Music) {
        this.seed.add(item)
        while (recommendations.size > lookAhead) {
            recommendations.removeAt(recommendations.lastIndex)
        }
    }
}