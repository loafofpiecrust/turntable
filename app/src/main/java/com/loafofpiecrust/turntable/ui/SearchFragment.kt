package com.loafofpiecrust.turntable.ui

import android.os.Bundle
import android.os.Parcelable
import android.support.v4.widget.SwipeRefreshLayout
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewManager
import com.evernote.android.state.State
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.AlbumsUI
import com.loafofpiecrust.turntable.artist.*
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.browse.LocalApi
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.popupMenu
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.song.songsList
import com.loafofpiecrust.turntable.util.arg
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.toast
import kotlin.math.max


class SearchFragment : BaseFragment() {
//    enum class Category {
//        ARTISTS,
//        ALBUMS,
//        SONGS,
//    }

    sealed class Category<T>: Parcelable {
        @Transient
        @IgnoredOnParcel
        val results = ConflatedBroadcastChannel<List<T>>()

        @Parcelize class Artists: Category<Artist>()
        @Parcelize class Albums: Category<Album>()
        @Parcelize class Songs: Category<Song>()
    }

    private var category: Category<*> by arg()

    // TODO: Save the stream of results instead!
    private var albumsGrid : AlbumsAdapter? = null
    private var artistsGrid : ArtistsAdapter? = null
    private var songsList : SongsAdapter? = null

    @State var prevQuery = ""
    private var searchJob: Job? = null

    private var repository: Repository = Repository.Companion
    private var results: SwipeRefreshLayout? = null


    override fun onCreate() {
        super.onCreate()
//        enterTransition = Slide().apply {
//            slideEdge = Gravity.BOTTOM
//        }
        allowEnterTransitionOverlap = true

        // Upon restoring with a query, restore the results.
        if (prevQuery != "") {
            onSearchAction(prevQuery)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val finalRadius = max(view.width, view.height).toFloat()
        val anim = ViewAnimationUtils.createCircularReveal(view, 0, 0, 0f, finalRadius)
        anim.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        albumsGrid = null
        artistsGrid = null
        songsList = null
    }

    override fun ViewManager.createView() = frameLayout {
        id = R.id.container
        topPadding = dimen(R.dimen.statusbar_height)
        clipToPadding = false

        val cat = category
        frameLayout {
            id = R.id.results
            topPadding = dip(64)
            results = when (cat) {
                is Category.Songs -> songsList(
                    SongsFragment.Category.All,
                    cat.results,
                    startRefreshing = false
                )
                is Category.Albums -> AlbumsUI.Custom(
                    cat.results,
                    AlbumsUI.SortBy.NONE
                ).createView(this)
                is Category.Artists -> ArtistsUI.Custom(
                    cat.results
                ).createView(this)
            } as SwipeRefreshLayout
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
                    (sequenceOf(LocalApi) + Repository.DEFAULT_SOURCES.asSequence())
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


    private suspend fun doSearch(query: String, cat: Category<*>): ReceiveChannel<*> {
        lateinit var result: ReceiveChannel<*>
        when (cat) {
            is Category.Albums -> cat.results.send(repository.searchAlbums(query))
            is Category.Artists -> cat.results.send(repository.searchArtists(query))
            is Category.Songs -> cat.results.send(repository.searchSongs(query))
        }
        return result
    }

    fun onSearchAction(query: String, force: Boolean = false) {
        if (force || query != prevQuery) {
            prevQuery = query
            searchJob?.cancel()
            results?.isRefreshing = true
            searchJob = async(Dispatchers.IO) {
                doSearch(query, category)
            }
        }
    }

    companion object {
        fun newInstance(category: Category<*>) = SearchFragment().apply {
            this.category = category
        }
    }
}
