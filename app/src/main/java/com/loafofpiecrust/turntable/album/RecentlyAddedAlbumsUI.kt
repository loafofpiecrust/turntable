package com.loafofpiecrust.turntable.album

import android.os.Parcelable
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.appcompat.v7.titleResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.topPadding
import org.jetbrains.anko.verticalLayout

@Parcelize
class RecentlyAddedAlbumsUI: UIComponent(), Parcelable {
    override fun ViewContext.render() = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            UserPrefs.primaryColor.consumeEachAsync { c ->
                backgroundColor = c
            }
            toolbar {
                standardStyle()
                titleResource = R.string.recently_added_albums
            }
        }

        val recentAlbums = Library.remoteAlbums.openSubscription()
            .map { albums ->
                albums.sortedByDescending { a ->
                    runBlocking { Library.findAlbumExtras(a.id).first()?.addedDate }
                }.take(30)
            }
            .replayOne()

        AlbumsUI.Custom(recentAlbums).createView(this)
    }
}