package com.loafofpiecrust.turntable.model.album

import java.util.*

class AlbumMap : TreeMap<AlbumId, Album>() {
    override fun put(key: AlbumId, value: Album): Album? {
        val previous = this[key]
        return if (previous != null) {
            super.put(key, MergedAlbum(previous, value))
            previous
        } else {
            super.put(key, value)
            null
        }
    }
}