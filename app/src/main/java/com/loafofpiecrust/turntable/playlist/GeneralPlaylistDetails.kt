package com.loafofpiecrust.turntable.playlist

import android.view.View
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.ui.universal.ParcelableComponent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.runBlocking

@Parcelize
class GeneralPlaylistDetails(
    private val playlistId: PlaylistId
): UIComponent(), ParcelableComponent {
    private val playlist = runBlocking {
        Library.instance.findPlaylist(playlistId.uuid)
            .first()
    }

    private val playlistUI: UIComponent = when (playlist) {
        is MixTape -> MixtapeDetailsUI(playlistId)
        is CollaborativePlaylist -> PlaylistDetailsUI(playlistId)
        else -> AlbumCollectionDetails(playlistId)
    }

    override fun ViewContext.render(): View =
        renderChild(playlistUI)
}