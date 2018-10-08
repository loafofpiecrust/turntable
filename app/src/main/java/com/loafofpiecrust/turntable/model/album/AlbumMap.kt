package com.loafofpiecrust.turntable.model.album

import java.util.*
import kotlin.collections.HashMap


class MergingHashMap<K, V>(
    private val merge: (V, V) -> V
) : HashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        val previous = this[key]
        return if (previous != null) {
            super.put(key, merge(previous, value))
            previous
        } else {
            super.put(key, value)
            null
        }
    }
}