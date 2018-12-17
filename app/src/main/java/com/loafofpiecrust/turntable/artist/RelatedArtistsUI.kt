package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.util.broadcastSingle
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.topPadding
import org.jetbrains.anko.verticalLayout

@Parcelize
class RelatedArtistsUI(
    private val baseArtistId: ArtistId
) : UIComponent(), Parcelable {
    private val artists = broadcastSingle(Dispatchers.IO) {
        Spotify.similarTo(baseArtistId)
    }

    override fun ViewContext.render() = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            UserPrefs.primaryColor.consumeEachAsync {
                backgroundColor = it
            }
            toolbar {
                standardStyle()
                title = context.getString(R.string.similar_to_artist, baseArtistId.displayName)
            }
        }

        ArtistsUI.Custom(artists, R.string.artist_limited_data)
            .createView(this)
    }
}