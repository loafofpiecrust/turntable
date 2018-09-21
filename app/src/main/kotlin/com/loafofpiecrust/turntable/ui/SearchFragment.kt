package com.loafofpiecrust.turntable.ui

import android.os.Parcelable
import android.view.ViewManager
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistsAdapter
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.util.searchBar
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.arg
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.isActive
import org.jetbrains.anko.*
import kotlin.coroutines.experimental.coroutineContext


//@EActivity
open class SearchFragment : BaseFragment() {

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

    private var prevQuery = ""
    private var searchJob: Job? = null


    override fun onCreate() {
        super.onCreate()
//        enterTransition = Slide().apply {
//            slideEdge = Gravity.BOTTOM
//        }
        allowEnterTransitionOverlap = true
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

        frameLayout {
            id = R.id.results
            topPadding = dip(64)

            val cat = category
            when (cat) {
                is Category.Artists -> fragment {
                    ArtistsFragment.fromChan(cat.results.openSubscription())
                }
                is Category.Albums -> fragment { AlbumsFragment.fromChan(cat.results.openSubscription()) }
                is Category.Songs -> fragment { SongsFragment.all().apply { songs = cat.results } }
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
        }.lparams(width = matchParent, height = wrapContent)
    }


    private suspend fun doSearch(query: String, cat: Category<*>): ReceiveChannel<*> {
        lateinit var result: ReceiveChannel<*>
        when (cat) {
            is Category.Albums -> cat.results.send(SearchApi.searchAlbums(query))
            is Category.Artists -> cat.results.send(SearchApi.searchArtists(query))
            is Category.Songs -> cat.results.send(SearchApi.searchSongs(query))
        }
        return result
    }

    fun onSearchAction(query: String) {
        if (query != prevQuery) {
//                        task(UI) { loadCircle.start() }
            prevQuery = query
            searchJob?.cancel()
            searchJob = async(BG_POOL + jobs) { doSearch(query, category) }
//                            .always(UI) { loadCircle.progressiveStop() }
        }
    }

    companion object {
        fun newInstance(category: Category<*>) = SearchFragment().apply {
            this.category = category
        }
    }
}
