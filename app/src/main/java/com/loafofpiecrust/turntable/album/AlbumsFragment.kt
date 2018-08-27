package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.view.*
import com.evernote.android.state.State
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.*
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.distinctSeq
import com.loafofpiecrust.turntable.util.replayOne
import fr.castorflex.android.circularprogressbar.CircularProgressDrawable
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.act

class AlbumsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        abstract val channel: BroadcastChannel<List<Album>>

        @Parcelize class All: Category() {
            override val channel get() = Library.instance.albums
        }
        @Parcelize data class ByArtist(
            val artist: ArtistId,
            val mode: ArtistDetailsFragment.Mode
        ): Category() {
            var artistChannel: ReceiveChannel<Artist>? = null

            override val channel get() =
                (artistChannel ?: Library.instance.findArtist(artist)
                    .map(BG_POOL) {
                        if (it is LocalArtist) {
                            when (mode) {
                                ArtistDetailsFragment.Mode.LIBRARY -> it
                                ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE ->
                                    given (SearchApi.find(artist)) { remote -> MergedArtist(it, remote) } ?: it
                                ArtistDetailsFragment.Mode.REMOTE -> it // use case?
                            }
                        } else {
                            when (mode) {
                                ArtistDetailsFragment.Mode.LIBRARY -> it
                                ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE ->
                                    given (Library.instance.findArtist(artist).firstOrNull()) { remote -> MergedArtist(it!!, remote) } ?: it
                                ArtistDetailsFragment.Mode.REMOTE -> it // use case?
                            }
                        }
                    })
                    .map(BG_POOL) { it?.albums ?: emptyList() }
                    .replayOne()
        }
    }

    enum class SortBy {
        NONE,
        YEAR,
        TITLE
    }

    // TODO: Customize parameters
    private var category: Category by arg()
    private var sortBy: SortBy by arg(SortBy.NONE)
    private var columnCount: Int by arg(3)
    @State var listState: Parcelable? = null

    lateinit var albums: BroadcastChannel<List<Album>>

    companion object {
        fun all() = AlbumsFragment().apply {
            category = Category.All()
        }

        fun fromChan(
            channel: ReceiveChannel<List<Album>>,
            sortBy: SortBy? = null,
            columnCount: Int? = null
        ) = AlbumsFragment().apply {
            category = Category.All()
            if (sortBy != null) {
                this.sortBy = sortBy
            }
            if (columnCount != null) {
                this.columnCount = columnCount
            }
            albums = channel.replayOne()//.broadcast(Channel.CONFLATED, start = CoroutineStart.DEFAULT)
        }

        fun byArtist(artistId: ArtistId, artist: ReceiveChannel<Artist>) = AlbumsFragment().apply {
            category = Category.ByArtist(artistId, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE).apply {
                artistChannel = artist
            }
        }


//        fun fromArtist(artist: ReceiveChannel<Artist>, mode: ReceiveChannel<ArtistDetailsFragment.Mode>): AlbumsFragment {
//            val cat = Category.ByArtist(artist.id, mode)
//            return AlbumsFragmentStarter.newInstance(cat).apply {
//                albums = mode.switchMap { mode ->
//                    when (cat) {
//                        is Category.All -> Library.instance.albums.openSubscription()
//                        is Category.ByArtist -> {
////                            withContext(UI) {
////                                circleProgress.visibility = View.VISIBLE
////                                loadCircle.start()
////                            }
//
//                            when (cat.mode) {
//                                ArtistDetailsFragment.Mode.REMOTE -> produceTask(BG_POOL) {
//                                    artist.resolveAlbums(false)
//                                }
//                                ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE -> produceTask(BG_POOL) {
//                                    artist.resolveAlbums(true)
//                                }
//                                else -> Library.instance.albumsByArtist(cat.artist)
//                            }
//                        }
//                        // is Category.Custom -> produceSingle(cat.albums)
//                    }
//                }
//            }
//        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::albums.isInitialized) {
            albums = category.channel
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) = menu.run {
        menuItem("Search", R.drawable.ic_search, showIcon=true).onClick(UI) {
            act.replaceMainContent(
                SearchFragmentStarter.newInstance(SearchFragment.Category.Albums()),
                true
            )
        }

        subMenu("Grid size") {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick(UI) { UserPrefs.albumGridColumns puts idx }
                    }
                }

                UserPrefs.albumGridColumns.consumeEach(UI) { count ->
                    items.forEach { it.isChecked = false }
                    items[count - 1].isChecked = true
                }
            }
        }
    }

    fun byArtist(vm: ViewManager, artistId: ArtistId, artist: ReceiveChannel<Artist>) {
        category = Category.ByArtist(artistId, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE).apply {
            this.artistChannel = artist
        }
        albums = category.channel
        vm.createView()
    }

    override fun ViewManager.createView() = frameLayout {
        id = R.id.container
        val cat = category
        val grid = GridLayoutManager(context, 3)

        val adapter = /*if (cat is Category.ByArtist) {
            AlbumSectionAdapter { view, album ->
                listState = grid.onSaveInstanceState()
                view.itemView.context.replaceMainContent(
                    DetailsFragment.fromAlbum(album),
                    true,
                    view.transitionViews
                )
            }.apply {
                setLayoutManager(grid)
            }
        } else {*/
            AlbumsAdapter(false) { view, album ->
                listState = grid.onSaveInstanceState()
                view.itemView.context.replaceMainContent(
                    DetailsFragment.fromAlbum(album),
                    true,
                    view.transitionViews
                )
            }
        //}

        val recycler = if (cat is Category.All) {
            fastScrollRecycler().apply {
                turntableStyle(UI)
            }
        } else recyclerView().apply {
            turntableStyle(UI)
        }

        val loadCircle = CircularProgressDrawable.Builder(context)
            .color(UserPrefs.primaryColor.value)
            .rotationSpeed(3f)
            .strokeWidth(dip(8).toFloat())
            .build()
            .apply { start() }

        val circleProgress = circularProgressBar {
            visibility = View.VISIBLE
            isIndeterminate = true
            indeterminateDrawable = loadCircle
        }.lparams(dip(64), dip(64)) {
            topMargin = dip(16)
            gravity = Gravity.CENTER_HORIZONTAL
        }


        recycler.apply {
            id = R.id.gridRecycler
            this.adapter = adapter

            layoutManager = grid
            padding = dimen(R.dimen.grid_gutter)

            if (listState != null) {
                grid.onRestoreInstanceState(listState)
            }

            if (columnCount > 0) {
                grid.spanCount = columnCount
            } else launch(UI) {
                UserPrefs.albumGridColumns.openSubscription()
                    .distinctSeq()
                    .consumeEach { grid.spanCount = it }
            }

            addItemDecoration(ItemOffsetDecoration(dimen(R.dimen.grid_gutter)))

            val sub = albums.openSubscription()//.map {
//                if (cat !is Category.All) {
//                    when (sortBy) {
//                        SortBy.NONE -> it
//                        SortBy.TITLE -> it.sortedWith(compareByIgnoreCase({ it.id.sortTitle }))
//                        SortBy.YEAR -> it.sortedByDescending {
//                            it.year ?: 0
//                        }.sortedBy { it.type.ordinal }
//                    }
//                } else it
//            }

//            if (adapter is AlbumsAdapter) {
                adapter.subscribeData(sub)
//            } else if (adapter is AlbumSectionAdapter) {
//                sub.consumeEach(UI) {
//                    adapter.replaceData(it)
//                }
//            }

            launch(UI, CoroutineStart.UNDISPATCHED) {
                albums.consume {
                    while(receive().isEmpty()) {}
                    loadCircle.progressiveStop()
                    circleProgress.visibility = View.INVISIBLE
                }
            }
        }
    }
}
