package com.loafofpiecrust.turntable.util

import java.util.*

class CharSequenceView(
    private val inner: CharSequence,
    private val start: Int,
    private val end: Int
): CharSequence {
    override val length: Int
        get() = when {
            end != -1 && start != -1 -> end - start
            end != -1 -> end
            start != -1 -> inner.length - start
            else -> inner.length
        }

    override fun get(index: Int): Char {
        val idx = if (start != -1) {
            index + start
        } else index

        assert(idx < end || end == -1)

        return inner[idx]
    }

    override fun subSequence(startIndex: Int, endIndex: Int) =
        CharSequenceView(this, startIndex, endIndex)

    override fun equals(other: Any?): Boolean {
        if (other !is CharSequence) return false
        val length = this.length
        if (length != other.length) return false
        for (idx in 0 until length) {
            if (this[idx] != other[idx]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String = when {
        start != -1 && end != -1 -> inner.toString().substring(start, end)
        start != -1 -> inner.toString().substring(start)
        end != -1 -> inner.toString().substring(0, end)
        else -> inner.toString()
    }

    override fun hashCode(): Int = if (start != -1 && end != -1) {
        Objects.hash(inner, start, end)
    } else inner.hashCode()
}

fun CharSequence.subSequenceView(startIndex: Int, endIndex: Int = -1) =
    CharSequenceView(this, startIndex, endIndex)