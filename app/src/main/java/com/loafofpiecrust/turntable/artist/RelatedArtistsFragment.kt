package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.UIComponent
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.channels.broadcast
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout

@Parcelize
class RelatedArtistsUI(
    private val baseArtistId: ArtistId
) : UIComponent(), Parcelable {
    private val artists = produceSingle(Dispatchers.IO) {
        Spotify.similarTo(baseArtistId)
    }.broadcast()

    override fun AnkoContext<Any>.render() = verticalLayout {
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

        ArtistsUI.Custom(artists).createView(this)
    }
}