package com.loafofpiecrust.turntable.artist

import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.appcompat.v7.titleResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.topPadding
import org.jetbrains.anko.verticalLayout

class RelatedArtistsFragment(): BaseFragment() {
    constructor(artistId: ArtistId): this() {
        this.artistId = artistId
    }

    private var artistId: ArtistId by arg()

    override fun ViewManager.createView() = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            UserPrefs.primaryColor.consumeEachAsync {
                backgroundColor = it
            }
            toolbar {
                standardStyle()
                title = getString(R.string.similar_to_artist, artistId.displayName)
            }
        }

        val artists = produceSingle(Dispatchers.IO) {
            Spotify.similarTo(artistId)
        }
        artistList(
            artists.replayOne(),
            ArtistsFragment.Category.RelatedTo(artistId),
            ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE,
            3
        )
    }
}