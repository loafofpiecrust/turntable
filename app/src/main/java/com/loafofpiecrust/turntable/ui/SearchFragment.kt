package com.loafofpiecrust.turntable.ui

import activitystarter.Arg
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewManager
import com.lapism.searchview.Search
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsAdapter
import com.loafofpiecrust.turntable.album.DetailsFragment
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.artist.ArtistsAdapter
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.searchBar
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceTask
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView


//@EActivity
open class SearchFragment : BaseFragment() {

    enum class Category {
        ARTISTS,
        ALBUMS,
        SONGS,
    }

    @Arg lateinit var category: Category

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
        id = View.generateViewId()
        topPadding = dimen(R.dimen.statusbar_height)
        clipToPadding = false

        recyclerView {
            itemAnimator = SlideInUpAnimator()
            layoutManager = GridLayoutManager(context, 3)
            topPadding = dip(64)
            clipToPadding = false
            when (category) {
                Category.ALBUMS -> {
                    if (albumsGrid == null) {
                        albumsGrid = AlbumsAdapter(true) { view, album ->

                            // go to details?
                            // First, let's see if we already have the album.
                            // Search _case-insensitively_ for the album name and artist
//                        val allAlbums = Library.instance.albums.value
//                        val existing = allAlbums.find { it.id == album.id }

                            // Open the album!
                            view.itemView.context.replaceMainContent(
                                DetailsFragment.fromAlbum(album),
                                true,
                                view.transitionViews
                            )
                        }
                    }

                    adapter = albumsGrid
                }
                Category.ARTISTS -> {
                    if (artistsGrid == null) {
                        artistsGrid = ArtistsAdapter { view, artists, idx ->
                            val artist = artists[idx]

                            view.itemView.context.replaceMainContent(
                                ArtistDetailsFragment.fromArtist(artist, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE),
                                true,
                                view.transitionViews
                            )
                        }
                    }

                    adapter = artistsGrid
                }
                Category.SONGS -> {
                    if (songsList == null) {
                        songsList = SongsAdapter(SongsFragment.Category.All()) { songs, idx ->
                            MusicService.enact(SyncService.Message.PlaySongs(songs, idx))
                        }
                    }
                }
            }
        }

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


    private fun doSearch(query: String, cat: Category): ReceiveChannel<*> {
        lateinit var result: ReceiveChannel<*>
        when (cat) {
            Category.ALBUMS -> albumsGrid?.subscribeData(produceTask(BG_POOL + searchJob) { SearchApi.searchAlbums(query) }.also { result = it })
            Category.ARTISTS -> artistsGrid?.subscribeData(produceTask(BG_POOL + searchJob) { SearchApi.searchArtists(query) }.also { result = it })
            else -> songsList?.subscribeData(produceTask(BG_POOL + searchJob) { SearchApi.searchSongs(query) }.also { result = it })
        }
        return result
    }

    fun onSearchAction(query: String) {
        if (query != prevQuery) {
//                        task(UI) { loadCircle.start() }
            prevQuery = query
            try {
                searchJob.cancelChildren()
            } finally {
                doSearch(query, category)
            }
//                            .always(UI) { loadCircle.progressiveStop() }
        }
    }
}
