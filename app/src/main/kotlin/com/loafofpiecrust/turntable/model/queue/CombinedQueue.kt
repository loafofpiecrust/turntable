package com.loafofpiecrust.turntable.model.queue

import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.StaticQueue
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.with
import com.loafofpiecrust.turntable.util.without

data class CombinedQueue(
    val primary: MusicPlayer.Queue,
    val nextUp: List<Song>,
    val isPlayingNext: Boolean = false
): MusicPlayer.Queue {
    override val list: List<Song> =
        if (nextUp.isNotEmpty()) {
            primary.list.with(nextUp, primary.position + 1)
        } else primary.list

    override val position: Int get() = if (isPlayingNext) {
        primary.position + 1
    } else primary.position

    override val current: Song? get() = list.getOrNull(position)


    override fun next(): MusicPlayer.Queue {
        return if (isPlayingNext) {
            if (nextUp.size > 1) { // need more than 1 because currently playing the first one.
                // position stays the same since we just pop a nextUp
                CombinedQueue(primary, nextUp.drop(1), true)
            } else {
                // still drop the currently playing in 'nextUp', but we're done playing the next ones.
                CombinedQueue(primary.next(), nextUp.drop(1), false)
            }
        } else {
            if (nextUp.isNotEmpty()) {
                CombinedQueue(primary, nextUp, true)
            } else {
                CombinedQueue(primary.next(), nextUp, false)
            }
        }
    }

    override fun prev(): MusicPlayer.Queue =
        CombinedQueue(primary.prev(), nextUp, false)

    override fun shifted(from: Int, to: Int): MusicPlayer.Queue {
        // first determine if the 'from' index is within the nextUp
        val firstNextPos = if (isPlayingNext) position else position + 1
        val lastNextPos = firstNextPos + nextUp.size
        val isFromNext = nextUp.isNotEmpty() && from >= firstNextPos && from <= lastNextPos
        return if (isFromNext) {
            val mappedFrom = from - firstNextPos
            val mappedTo = to - firstNextPos
            // shift within nextUp not affecting the primary queue
            this.copy(nextUp = nextUp.shifted(mappedFrom, mappedTo))
        } else {
            val mappedFrom = if (from < firstNextPos) {
                from
            } else from - nextUp.size
            val mappedTo = if (to < firstNextPos) {
                to
            } else to - nextUp.size
            // shift within the primary queue without affecting the nextUp list
            this.copy(primary = primary.shifted(mappedFrom, mappedTo))
        }
    }

    /**
     * Shift the position of the queue to the given index within the combined song list.
     * This may be before or after the current position.
     *
     * Doesn't treat 'Up Next' specially; if the new position is past all items
     * queued in 'Up Next', then 'Up Next' is cleared.
     * This differs from Spotify's functionality which distinguishes which
     * part of the queue you're shifting within.
     */
    override fun shiftedPosition(newPos: Int): MusicPlayer.Queue {
        if (newPos == position) return this

        // first decide if the new position is within up next or past it.
        return if (newPos > position) {
            val diff = newPos - position
            if (diff > nextUp.size) {
                // shifting past all `nextUp` items
                copy(
                    primary = primary.shiftedPosition(primary.position + diff - nextUp.size),
                    nextUp = emptyList(),
                    isPlayingNext = false
                )
            } else {
                // shifting within `nextUp`
                copy(
                    nextUp = nextUp.drop(if (isPlayingNext) diff else diff - 1),
                    isPlayingNext = true
                )
            }
        } else {
            // when we reverse, we always retain nextUp and we're never playing nextUp anymore
            // positions in the previously played songs are only relevant to `primary`
            copy(
                primary = primary.shiftedPosition(newPos),
                nextUp = if (isPlayingNext) nextUp.drop(1) else nextUp,
                isPlayingNext = false
            )
        }
    }


    fun shuffled(): CombinedQueue {
        val p = if (primary is StaticQueue) {
            val current = primary.list[primary.position]
            val primaryWithoutCurrent = primary.list.without(primary.position).shuffled()
            primary.copy(list = listOf(current) + primaryWithoutCurrent, position = 0)
        } else primary

        return this.copy(primary = p)
    }

    fun restoreSequential(backup: MusicPlayer.Queue): CombinedQueue =
        if (backup is StaticQueue) {
            // find the current song in the backup list, and make that the position
            this.copy(primary = backup.copy(position = backup.list.indexOf(primary.current)))
        } else {
            this
        }
}