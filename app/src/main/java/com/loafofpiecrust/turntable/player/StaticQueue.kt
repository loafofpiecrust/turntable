package com.loafofpiecrust.turntable.player

import com.loafofpiecrust.turntable.clamp
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.model.song.Song

data class StaticQueue(
    override val list: List<Song>,
    override val position: Int
): MusicPlayer.Queue {
    override val current: Song? get() = list.getOrNull(position)

    override fun next() = when {
        list.isEmpty() -> this
        position < list.size - 1 -> StaticQueue(list, position + 1)
        else -> this
    }

    override fun prev() = when {
        list.isEmpty() -> this
        position > 0 -> StaticQueue(list, position - 1)
        else -> this
    }

    override fun shifted(from: Int, to: Int): MusicPlayer.Queue {
        return this.copy(list = list.shifted(from, to))
    }

    override fun shiftedPosition(newPos: Int): MusicPlayer.Queue {
        val newPos = clamp(newPos, 0, list.size - 1)
        return if (newPos != this.position) {
            copy(position = newPos)
        } else this
    }
}