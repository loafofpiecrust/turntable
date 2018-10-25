package com.loafofpiecrust.turntable.model.artist

import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.model.album.Album
import kotlinx.coroutines.runBlocking


class LocalArtist(
    override val id: ArtistId,
    override val albums: List<Album>
): Artist {
    override val startYear get() = albums.minBy { it.year.takeIf { it > 0 } ?: Int.MAX_VALUE }?.year
    override val endYear get() = albums.maxBy { it.year.takeIf { it > 0 } ?: Int.MIN_VALUE }?.year

    private val remote get() = runBlocking { Repository.find(id) }
    override val biography: String? get() = remote?.biography
}