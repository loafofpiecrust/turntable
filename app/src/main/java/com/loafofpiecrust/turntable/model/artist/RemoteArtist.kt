package com.loafofpiecrust.turntable.model.artist

import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.util.produceSingle

class RemoteArtist(
    override val id: ArtistId,
    val details: Details,
    override val startYear: Int? = null,
    override val endYear: Int? = null
) : Artist {
    override val albums get() = details.albums
    override val biography get() = details.biography

    override fun loadThumbnail(req: RequestManager) =
        details.thumbnailUrl?.let { url ->
            produceSingle(req.load(url))
        } ?: super.loadThumbnail(req)

    /**
     * Properties only obtained with remoteInfo:
     * - albums
     * - members
     * - biography
     * Each API implements whether they have any of this info already
     * or if it's all lazy or exists at all or what
     */
    interface Details {
        val albums: List<Album>
        val biography: String
        val thumbnailUrl: String?
//        val members: List<Artist.Member>
    }
}