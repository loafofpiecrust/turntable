package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import activitystarter.Arg
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.view.*
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.distinctSeq
import com.loafofpiecrust.turntable.util.replayOne
import fr.castorflex.android.circularprogressbar.CircularProgressDrawable
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consume
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx

class AlbumsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class ByArtist(
            val artist: ArtistId,
            val mode: ArtistDetailsFragment.Mode
        ): Category()
        // @Parcelize data class Custom(val albums: List<Album>): Category()
    }

    enum class SortBy {
        NONE,
        YEAR,
        TITLE
    }

    // TODO: Customize parameters
    @Arg lateinit var category: Category
    @Arg(optional=true) var sortBy: SortBy = SortBy.NONE
    @Arg(optional=true) var columnCount: Int = 3

    // TODO: Make this cold by using a function returning the channel
    // This would be to prevent any filtering, resolution, and merging from happening while the view is invisible!
    // This also allows us to drop the channel entirely onDetach and make a new one in onAttach (if we want that??)
    // private lateinit var albums: ReceiveChannel<List<Album>>
    lateinit var albums: BroadcastChannel<List<Album>>
//    private val category by lazy { ConflatedBroadcastChannel(initialCategory) }

    companion object {
        fun all(): AlbumsFragment {
            return AlbumsFragmentStarter.newInstance(Category.All())
        }
        fun fromChan(channel: ReceiveChannel<List<Album>>): AlbumsFragment {
            return AlbumsFragmentStarter.newInstance(Category.All()).apply {
                albums = channel.replayOne()//.broadcast(Channel.CONFLATED, start = CoroutineStart.DEFAULT)
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
        if (!::albums.isInitialized && category is Category.All) {
            albums = Library.instance.albums
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) = menu.run {
        menuItem("Search", R.drawable.ic_search, showIcon=true).onClick(UI) {
            act.replaceMainContent(
                SearchFragmentStarter.newInstance(SearchFragment.Category.ALBUMS),
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

    override fun ViewManager.createView() = frameLayout {
        val cat = category
        val grid = GridLayoutManager(context, 3)

        val adapter = if (cat is Category.ByArtist) {
            AlbumSectionAdapter { view, album ->
                view.itemView.context.replaceMainContent(
                    DetailsFragment.fromAlbum(album),
                    true,
                    view.transitionViews
                )
            }.apply {
                setLayoutManager(grid)
            }
        } else {
            AlbumsAdapter(false) { view, album ->
                view.itemView.context.replaceMainContent(
                    DetailsFragment.fromAlbum(album),
                    true,
                    view.transitionViews
                )
            }
        }

        val recycler = if (cat is Category.All) {
            fastScrollRecycler().apply {
                turntableStyle(UI)
            }
        } else recyclerView().apply {
            turntableStyle(UI)
        }

        val loadCircle = CircularProgressDrawable.Builder(ctx)
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

            if (adapter is AlbumsAdapter) {
                adapter.subscribeData(sub)
            } else if (adapter is AlbumSectionAdapter) {
                sub.consumeEach(UI) {
                    adapter.replaceData(it)
                }
            }

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
