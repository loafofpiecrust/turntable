package com.loafofpiecrust.turntable.model.artist

import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.repository.Repositories
import kotlinx.coroutines.runBlocking

/**
 * Artist of albums that are all local.
 */
class LocalArtist(
    override val id: ArtistId,
    override val albums: List<Album>
): Artist {
    override val startYear get() = albums.minBy { it.year.takeIf { it > 0 } ?: Int.MAX_VALUE }?.year
    override val endYear get() = albums.maxBy { it.year.takeIf { it > 0 } ?: Int.MIN_VALUE }?.year

    private fun resolveRemote() = runBlocking {
        Repositories.findOnline(id)
    }
    override val biography: String? get() = resolveRemote()?.biography
}