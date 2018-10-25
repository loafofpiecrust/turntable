package com.loafofpiecrust.turntable.model.album


class MergingHashMap<K, V>(
    private val merge: (V, V) -> V
) : HashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        val previous = this[key]
        return super.put(key, if (previous != null) {
            merge(previous, value)
        } else {
            value
        })
    }
}