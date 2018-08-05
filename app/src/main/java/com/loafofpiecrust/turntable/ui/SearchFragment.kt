package com.loafofpiecrust.turntable.ui

import activitystarter.Arg
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.transition.Slide
import android.view.Gravity
import android.view.ViewManager
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.DetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.ArtistsAdapter
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.searchBar
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.util.task
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.experimental.Job
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView


//@EActivity
open class SearchFragment : BaseFragment(), FloatingSearchView.OnSearchListener, Search.OnQueryTextListener {

    enum class Category {
        ARTISTS,
        ALBUMS,
        SONGS,
    }

    @Arg lateinit var category: Category

    private lateinit var recycler : RecyclerView
    private var albumsGrid : AlbumsAdapter? = null
    private var artistsGrid : ArtistsAdapter? = null
    private var songsList : SongsAdapter? = null

    private var prevQuery = ""
    private var searchJob: Job? = null


    override fun onCreate() {
        super.onCreate()
        enterTransition = Slide().apply {
            slideEdge = Gravity.BOTTOM
        }
        allowEnterTransitionOverlap = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
    }

    override fun makeView(ui: ViewManager) = ui.frameLayout {
        id = R.id.content_layout
        topPadding = dimen(R.dimen.statusbar_height)
        clipToPadding = false

        recycler = recyclerView {
            itemAnimator = SlideInUpAnimator()
            layoutManager = GridLayoutManager(context, 3)
            topPadding = dimen(R.dimen.toolbar_height)
            clipToPadding = false
            when (category) {
                Category.ALBUMS -> {
                    albumsGrid = AlbumsAdapter(true) { view, album ->

                        // go to details?
                        // First, let's see if we already have the album.
                        // Search _case-insensitively_ for the album name and artist
                        val allAlbums = Library.instance.albums.value
                        val existing = allAlbums.find { it.id == album.id }

                        // Open the album!
                        context.replaceMainContent(
                            DetailsFragmentStarter.newInstance(existing ?: album),
                            true,
                            view.transitionViews
                        )
                    }

                    adapter = albumsGrid
                }
                Category.ARTISTS -> {
                    artistsGrid = ArtistsAdapter { view, artists, idx ->
                        val artist = artists[idx]

                        context.replaceMainContent(
                            ArtistDetailsFragmentStarter.newInstance(artist, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE),
                            true,
                            view.transitionViews
                        )
                    }

                    adapter = artistsGrid
                }
                Category.SONGS -> songsList = SongsAdapter(SongsFragment.Category.All()) { songs, idx ->
                    MusicService.enact(SyncService.Message.PlaySongs(songs, idx))
                }
            }
        }

        searchBar {
            setHint("Search...")
            setOnQueryTextListener(this@SearchFragment)
//            queryHint = "Search..."
//            onQueryTextListener {
//                onQueryTextSubmit {
//                    onSearchAction(it!!)
//                    true
//                }
//            }
        }.lparams(width = matchParent, height = wrapContent)

//        floatingSearchView {
//            id = View.generateViewId()
//            fitsSystemWindows = true
//            setSearchHint("Search...")
//            setCloseSearchOnKeyboardDismiss(true)
//            setLeftActionMode(FloatingSearchView.LEFT_ACTION_MODE_SHOW_HOME)
//            setOnHomeActionClickListener {
//                fragmentManager?.popBackStack()
//            }
//            setQueryTextColor(Color.WHITE)
//            setHintTextColor(Color.DKGRAY)
//            setDimBackground(true)
//
//            setOnSearchListener(this@SearchFragment)
//
//        }.lparams(height=matchParent, width=matchParent)
    }


    private suspend fun doSearch(query: String, cat: Category) {
        when (cat) {
            Category.ALBUMS -> SearchApi.searchAlbums(query).also {
                albumsGrid?.updateData(it)
            }
            Category.ARTISTS -> SearchApi.searchArtists(query).also {
                artistsGrid?.updateData(it)
            }
            Category.SONGS -> SearchApi.searchSongs(query).also {
                songsList?.updateData(it)
            }
        }
    }

    override fun onQueryTextSubmit(query: CharSequence?): Boolean {
        onSearchAction(query.toString())
        return true
    }

    override fun onQueryTextChange(newText: CharSequence?) {
    }

    override fun onSearchAction(query: String) {
        if (query != prevQuery) {
//                        task(UI) { loadCircle.start() }
            prevQuery = query
            searchJob?.cancel()
            searchJob = task { doSearch(query, category) }
//                            .always(UI) { loadCircle.progressiveStop() }
        }
    }

    override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
    }
}
