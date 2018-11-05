package com.loafofpiecrust.turntable.util

class Memoize1<in T, out R>(
    private val f: (T) -> R
) : (T) -> R {
    private val values = LinkedHashMap<T, R>()
    override fun invoke(x: T): R {
        return values.getOrPut(x) { f(x) }
    }
    fun clearCache() {
        values.clear()
    }
}

fun <T, R> ((T) -> R).memoize() = Memoize1(this)