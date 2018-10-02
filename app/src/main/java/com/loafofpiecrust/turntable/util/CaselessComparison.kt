package com.loafofpiecrust.turntable.util

import kotlin.math.min


fun <T: Any> compareByIgnoreCase(vararg selectors: (T) -> CharSequence): Comparator<T> {
    require(selectors.isNotEmpty())
    return Comparator { a, b -> compareValuesByIgnoreCase(a, b, selectors) }
//    return compareBy(selectors)
}


fun <T> compareValuesByIgnoreCase(a: T, b: T, selectors: Array<out (T)->CharSequence>): Int {
    for (fn in selectors) {
        val v1 = fn(a)
        val v2 = fn(b)
        val diff = compareValues(v1, v2)
        if (diff != 0) return diff
    }
    return 0
}

private fun compareValues(a: CharSequence, b: CharSequence): Int =
    a.compareTo(b, ignoreCase=true)


fun CharSequence.compareTo(other: CharSequence, ignoreCase: Boolean = false): Int {
    if (this === other) {
        return 0
    }

    val smallLen = min(length, other.length)
    if (ignoreCase) {
        for (idx in 0 until smallLen) {
            val a = this[idx].toLowerCase()
            val b = other[idx].toLowerCase()
            val res = a.compareTo(b)
            if (res != 0) return res
        }
    } else {
        for (idx in 0 until smallLen) {
            val a = this[idx]
            val b = other[idx]
            val res = a.compareTo(b)
            if (res != 0) return res
        }
    }

    // either:
    // 1. all characters are equal
    // 2. lengths are different. shorter one goes first.
    return length.compareTo(other.length)
}