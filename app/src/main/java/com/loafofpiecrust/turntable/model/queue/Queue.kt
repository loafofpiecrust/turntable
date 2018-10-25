package com.loafofpiecrust.turntable.model.queue

import com.loafofpiecrust.turntable.model.song.Song

interface Queue {
    val list: List<Song>
    val position: Int
    val current: Song?

    fun toNext(): Queue
    fun toPrev(): Queue
    fun shifted(from: Int, to: Int): Queue
    fun shiftedPosition(newPos: Int): Queue
    fun peek(): Song? = list.getOrNull(position + 1)
}