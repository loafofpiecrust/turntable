package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceTask
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

class RelatedArtistsFragment: BaseFragment() {
    @Arg lateinit var artistId: ArtistId
    lateinit var gridAdapter: ArtistsAdapter

    override fun ViewManager.createView(): View = recyclerView {
        // TODO: dynamic grid size
        layoutManager = GridLayoutManager(context, 3)
        adapter = ArtistsAdapter { view, artists, pos ->
            // smoothly transition the cover image!
            val artist = artists[pos]
            ctx.replaceMainContent(
                ArtistDetailsFragment.fromArtist(artist, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE),
                true,
                view.transitionViews
            )
        }.apply {
            subscribeData(produceTask(BG_POOL + jobs) { Spotify.similarTo(artistId) })
        }
    }
}