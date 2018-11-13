package com.loafofpiecrust.turntable.util


class MergingHashMap<K, V>(
    private val merge: (V, V) -> V
) : HashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        val previous = this[key]
        return super.put(key, if (previous != null && previous !== value) {
            merge(previous, value)
        } else {
            value
        })
    }
}