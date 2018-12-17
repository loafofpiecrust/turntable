package com.loafofpiecrust.turntable.model.album

import android.graphics.drawable.Drawable
import android.os.Parcelable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.util.produceSingle
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking

@Parcelize
class RemoteAlbum(
    override val id: AlbumId,
    val remoteId: Album.RemoteDetails, // Discogs, Spotify, or MusicBrainz ID
    override val type: Album.Type = Album.Type.LP,
    override val year: Int = 0
): Album, Parcelable {
    private constructor(): this(AlbumId(), Spotify.AlbumDetails(""))

    @IgnoredOnParcel
    @delegate:Transient
    override val tracks: List<Song> by lazy {
        runBlocking(Dispatchers.IO) {
            // grab tracks from online
            tryOr(emptyList()) { remoteId.resolveTracks(id) }
        }
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return (remoteId.thumbnailUrl ?: remoteId.artworkUrl)?.let {
            produceSingle(req.load(it))
        } ?: super.loadThumbnail(req)
    }
}
