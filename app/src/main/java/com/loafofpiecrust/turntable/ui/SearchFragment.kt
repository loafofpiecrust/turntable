package com.loafofpiecrust.turntable.ui

import activitystarter.Arg
import android.os.Parcelable
import android.view.ViewManager
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistsAdapter
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.util.searchBar
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.util.BG_POOL
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*


//@EActivity
open class SearchFragment : BaseFragment() {

//    enum class Category {
//        ARTISTS,
//        ALBUMS,
//        SONGS,
//    }

    sealed class Category<T>: Parcelable {
        @Transient
        val results = ConflatedBroadcastChannel<List<T>>()

        @Parcelize class Artists: Category<Artist>()
        @Parcelize class Albums: Category<Album>()
        @Parcelize class Songs: Category<Song>()
    }

    @Arg lateinit var category: Category<*>

    // TODO: Save the stream of results instead!
    private var albumsGrid : AlbumsAdapter? = null
    private var artistsGrid : ArtistsAdapter? = null
    private var songsList : SongsAdapter? = null

    private var prevQuery = ""
    private var searchJob = Job()


    override fun onCreate() {
        super.onCreate()
//        enterTransition = Slide().apply {
//            slideEdge = Gravity.BOTTOM
//        }
        allowEnterTransitionOverlap = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob.cancelChildren()
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
            is Category.Albums -> cat.results puts SearchApi.searchAlbums(query)
            is Category.Artists -> cat.results puts SearchApi.searchArtists(query)
            is Category.Songs -> cat.results puts SearchApi.searchSongs(query)
        }
        return result
    }

    fun onSearchAction(query: String) {
        if (query != prevQuery) {
//                        task(UI) { loadCircle.start() }
            prevQuery = query
            searchJob.cancelChildren()
            async(BG_POOL, parent = searchJob) { doSearch(query, category) }
//                            .always(UI) { loadCircle.progressiveStop() }
        }
    }
}
