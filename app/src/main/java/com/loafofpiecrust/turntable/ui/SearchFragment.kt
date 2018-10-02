package com.loafofpiecrust.turntable.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewManager
import com.evernote.android.state.State
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.albumList
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistsAdapter
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.artist.artistList
import com.loafofpiecrust.turntable.browse.LocalApi
import com.loafofpiecrust.turntable.browse.SearchApi
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

    private var searchApi: SearchApi = SearchApi.Companion


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
            when (cat) {
                is Category.Songs -> songsList(
                    SongsFragment.Category.All,
                    cat.results
                )
                is Category.Albums -> albumList(
                    cat.results,
                    AlbumsFragment.Category.All,
                    AlbumsFragment.SortBy.NONE,
                    3
                )
                is Category.Artists -> artistList(
                    cat.results,
                    ArtistsFragment.Category.All(),
                    3
                )
            }
        }.lparams(matchParent, matchParent)

        searchBar {
            setHint("Search...")
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
                    menuItem("Local").onClick {
                        toast("Searching local music")
                        searchApi = LocalApi
                    }

                    for (api in SearchApi.DEFAULT_APIS) {
                        val name = api.javaClass.simpleName
                        menuItem(name).onClick {
                            toast("Searching on $name")
                            searchApi = api
                        }
                    }
                }
            }
        }.lparams(width = matchParent, height = wrapContent)
    }


    private suspend fun doSearch(query: String, cat: Category<*>): ReceiveChannel<*> {
        lateinit var result: ReceiveChannel<*>
        when (cat) {
            is Category.Albums -> cat.results.send(searchApi.searchAlbums(query))
            is Category.Artists -> cat.results.send(searchApi.searchArtists(query))
            is Category.Songs -> cat.results.send(searchApi.searchSongs(query))
        }
        return result
    }

    fun onSearchAction(query: String) {
        if (query != prevQuery) {
//                        task(UI) { loadCircle.start() }
            prevQuery = query
            searchJob?.cancel()
            searchJob = async(Dispatchers.IO) { doSearch(query, category) }
//                            .always(UI) { loadCircle.progressiveStop() }
        }
    }

    companion object {
        fun newInstance(category: Category<*>) = SearchFragment().apply {
            this.category = category
        }
    }
}
