package com.loafofpiecrust.turntable.playlist

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.MusicAdapter
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.universal.ParcelableComponent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.topPadding
import org.jetbrains.anko.verticalLayout

@Parcelize
class AlbumCollectionDetails(
    private val playlistId: PlaylistId
): UIComponent(), ParcelableComponent {
    private val playlist = runBlocking {
        Library.findPlaylist(playlistId.uuid)
            .first() as AlbumCollection
    }

    override fun ViewContext.render(): View = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            toolbar {
                standardStyle()
                title = playlist.id.displayName
                backgroundColor = playlist.color ?: UserPrefs.primaryColor.value
            }
        }

        recyclerView {
            layoutManager = LinearLayoutManager(context)
            adapter = MusicAdapter(coroutineContext, playlist.albums)
        }
    }
}