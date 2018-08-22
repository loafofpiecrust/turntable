package com.loafofpiecrust.turntable.util


inline fun <T: Any> compareByIgnoreCase(vararg selectors: (T) -> String): Comparator<T> {
    require(selectors.isNotEmpty())
    return Comparator { a, b -> compareValuesByIgnoreCase(a, b, selectors) }
//    return compareBy(selectors)
}


fun <T> compareValuesByIgnoreCase(a: T, b: T, selectors: Array<out (T)->String>): Int {
    for (fn in selectors) {
        val v1 = fn(a)
        val v2 = fn(b)
        val diff = compareValues(v1, v2)
        if (diff != 0) return diff
    }
    return 0
}

private fun compareValues(a: String, b: String): Int =
    if (a === b) 0 else a.compareTo(b, ignoreCase=true)
