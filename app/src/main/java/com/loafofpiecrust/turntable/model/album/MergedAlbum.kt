package com.loafofpiecrust.turntable.model.album

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.dedupMerge
import com.loafofpiecrust.turntable.dedupMergeSorted
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.channels.ReceiveChannel

data class MergedAlbum(
    private val a: Album,
    private val b: Album
): Album {
    override val id get() = a.id
    /**
     * The "largest" type associated with their album being merged.
     * TODO: Re-calculate the type based on whether these are local or remote.
     */
    override val type: Album.Type get() = minOf(a.type, b.type)
    override val year: Int get() {
        val yearA = a.year
        val yearB = b.year
        return if (yearA > 0 && yearB > 0) {
            minOf(yearA, yearB)
        } else yearA.takeIf { it > 0 } ?: yearB
    }

    private var tracks: List<Song>? = null
    override suspend fun resolveTracks(): List<Song> {
        tracks = tracks ?: (a.resolveTracks() + b.resolveTracks())
            .sortedBy { it.discTrack }
            .dedupMergeSorted(
                { a, b -> a.discTrack == b.discTrack && a.id.displayName == b.id.displayName },
                // TODO: Use MergedSong here?
                { a, b -> a }
            )

        return tracks ?: listOf()
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return a.loadThumbnail(req).switchMap {
            if (it != null) {
                produceSingle(it)
            } else b.loadThumbnail(req)
        }
    }
}