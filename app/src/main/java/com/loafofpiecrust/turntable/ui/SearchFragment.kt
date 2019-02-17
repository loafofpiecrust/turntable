package com.loafofpiecrust.turntable.ui

import android.os.Bundle
import android.os.Parcelable
import android.support.v4.widget.SwipeRefreshLayout
import android.view.Gravity
import android.view.ViewManager
import com.evernote.android.state.State
import com.github.ajalt.timberkt.Timber
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsUI
import com.loafofpiecrust.turntable.artist.ArtistsUI
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.popupMenu
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.serialize.arg
import com.loafofpiecrust.turntable.serialize.getValue
import com.loafofpiecrust.turntable.serialize.setValue
import com.loafofpiecrust.turntable.song.SongsUI
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.searchBar
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.toast

class SearchFragment : BaseFragment() {
    sealed class Category<T>: Parcelable {
        @Transient
        @IgnoredOnParcel
        val results = ConflatedBroadcastChannel<List<T>>(emptyList())

        @Parcelize class Artists: Category<Artist>()
        @Parcelize class Albums: Category<Album>()
        @Parcelize class Songs: Category<Song>()
    }

    private var category: Category<*> by arg()

    @State var prevQuery = ""
    private var searchJob: Job? = null

    private var repository: Repository = Repositories
    private var results: SwipeRefreshLayout? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowEnterTransitionOverlap = true

        // Upon restoring with a query, restore the results.
        if (prevQuery != "") {
            onSearchAction(prevQuery)
        }
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        val finalRadius = max(view.width, view.height).toFloat()
//        val anim = ViewAnimationUtils.createCircularReveal(view, 0, 0, 0f, finalRadius)
//        anim.start()
//    }

    override fun ViewManager.createView() = frameLayout {
        id = R.id.container
        topPadding = dimen(R.dimen.statusbar_height)
        clipToPadding = false

        val cat = category
        frameLayout {
            id = R.id.results
            topPadding = dip(64)
            results = when (cat) {
                is Category.Songs -> SongsUI.Custom(cat.results).createView(this)
                is Category.Albums -> AlbumsUI.Custom(cat.results).createView(this)
                is Category.Artists -> ArtistsUI.Custom(
                    cat.results,
                    R.string.search_results_empty_details
                ).createView(this)
            } as SwipeRefreshLayout
//            results?.isRefreshing = false
        }.lparams(matchParent, matchParent)

        searchBar {
            setHint(R.string.search_hint)
            requestFocusFromTouch()
            showKeyboard()
            setLogoHamburgerToLogoArrowWithoutAnimation(true)
            logo = Search.Logo.ARROW
            setOnLogoClickListener { activity?.onBackPressed() }

            setOnQueryTextListener(object : Search.OnQueryTextListener {
                override fun onQueryTextChange(newText: CharSequence) {
                    if (newText.isEmpty()) {
                        prevQuery = ""
                    }
                }

                override fun onQueryTextSubmit(query: CharSequence): Boolean {
                    onSearchAction(query.toString())
                    close()
                    return false
                }
            })

            // use menu icon to change api
            setMenuIcon(context.getDrawable(R.drawable.ic_cake))
            setOnMenuClickListener {
                popupMenu(Gravity.END) {
                    (sequenceOf(Repositories, LocalApi) + Repositories.ALL.asSequence())
                        .filter { it.displayName != -1 }
                        .forEach { api ->
                            val name = getString(api.displayName)
                            menuItem(name).onClick {
                                toast("Searching $name")
                                repository = api
                            }
                        }
                }
            }
        }.lparams(width = matchParent, height = wrapContent)
    }


    private suspend fun doSearch(query: String, cat: Category<*>) {
        when (cat) {
            is Category.Albums -> cat.results.send(repository.searchAlbums(query))
            is Category.Artists -> cat.results.send(repository.searchArtists(query))
            is Category.Songs -> cat.results.send(repository.searchSongs(query))
        }
    }

    fun onSearchAction(query: String, force: Boolean = false) {
        if (force || query != prevQuery) {
            prevQuery = query
            searchJob?.cancel()
            results?.isRefreshing = true
            searchJob = async(Dispatchers.IO) {
                doSearch(query, category)
            }
            searchJob?.invokeOnCompletion { err ->
                if (err != null && err !is CancellationException) {
                    Timber.e(err) { "Search failed" }
                    launch(Dispatchers.Main) {
                        activity?.toast("Search failed.")
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(category: Category<*>) = SearchFragment().apply {
            this.category = category
        }
    }
}
