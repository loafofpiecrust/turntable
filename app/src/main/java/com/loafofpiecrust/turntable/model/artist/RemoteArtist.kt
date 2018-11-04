package com.loafofpiecrust.turntable.model.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.model.album.Album


class RemoteArtist(
    override val id: ArtistId,
    val details: Details,
    override val startYear: Int? = null,
    override val endYear: Int? = null
): Artist {
    override val albums get() = details.albums
    override val biography get() = details.biography

    // Properties only obtained with remoteInfo:
    // - albums
    // - members
    // - biography
    // Each API implements whether they have any of this info already
    // or if it's all lazy or exists at all or what
    interface Details {
        val albums: List<Album>
        val biography: String
//        val members: List<Artist.Member>
    }
}