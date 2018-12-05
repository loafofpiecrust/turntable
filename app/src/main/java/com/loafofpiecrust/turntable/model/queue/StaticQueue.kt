package com.loafofpiecrust.turntable.model.queue

import com.loafofpiecrust.turntable.clamp
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.shifted
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StaticQueue(
    override val list: List<Song>,
    override val position: Int
) : Queue {
    internal constructor(): this(emptyList(), 0)

    override val current: Song? get() = list.getOrNull(position)

    override fun toNext(): StaticQueue = when {
        list.isEmpty() -> this
        position < list.size - 1 -> copy(position = position + 1)
        else -> this
    }

    override fun toPrev(): StaticQueue = when {
        list.isEmpty() -> this
        position > 0 -> copy(position = position - 1)
        else -> this
    }

    override fun shifted(from: Int, to: Int): StaticQueue =
        copy(list = list.shifted(from, to))

    override fun shiftedPosition(newPos: Int): StaticQueue {
        val newPos = clamp(newPos, 0, list.size - 1)
        return if (newPos != this.position) {
            copy(position = newPos)
        } else this
    }
}