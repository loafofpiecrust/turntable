package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import activitystarter.Arg
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.view.*
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import fr.castorflex.android.circularprogressbar.CircularProgressDrawable
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.withContext
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

class AlbumsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class ByArtist(
            val artist: Artist,
            val mode: ArtistDetailsFragment.Mode
        ): Category()
        @Parcelize data class Custom(val albums: List<Album>): Category()
    }

    enum class SortBy {
        NONE,
        YEAR,
        TITLE
    }

    // TODO: Customize parameters
    @Arg lateinit var initialCategory: Category
    @Arg(optional=true) var sortBy: SortBy = SortBy.NONE
    @Arg(optional=true) var columnCount: Int = 0

    val category by lazy { ConflatedBroadcastChannel(initialCategory) }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) = menu.run {
        menuItem("Search", R.drawable.ic_search, showIcon=true).onClick {
            MainActivity.replaceContent(
                SearchFragmentStarter.newInstance(SearchFragment.Category.ALBUMS),
                true
            )
        }

        subMenu("Grid size") {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick { UserPrefs.albumGridColumns puts idx }
                    }
                }

                UserPrefs.albumGridColumns.consumeEach(UI) { count ->
                    items.forEach { it.isChecked = false }
                    items[count - 1].isChecked = true
                }
            }
        }
    }

    override fun makeView(ui: ViewManager) = ui.frameLayout {
        val cat = category.value
        val grid = GridLayoutManager(context, 3)

        val adapter = if (cat is Category.ByArtist) {
            AlbumSectionAdapter { view, album ->
                ctx.replaceMainContent(
                    DetailsFragmentStarter.newInstance(album),
                    true,
                    view.transitionViews
                )
            }.apply {
                setLayoutManager(grid)
            }
        } else {
            AlbumsAdapter(false) { view, album ->
                ctx.replaceMainContent(
                    DetailsFragmentStarter.newInstance(album),
                    true,
                    view.transitionViews
                )
            }
        }

        val recycler = if (cat is Category.All) {
            fastScrollRecycler().apply {
                turntableStyle(jobs)
            }
        } else recyclerView().apply {
            turntableStyle(jobs)
        }

        val loadCircle = CircularProgressDrawable.Builder(ctx)
            .color(UserPrefs.primaryColor.value)
            .rotationSpeed(3f)
            .strokeWidth(dip(8).toFloat())
            .build()

        val circleProgress = circularProgressBar {
            visibility = View.INVISIBLE
            isIndeterminate = true
            indeterminateDrawable = loadCircle
        }.lparams(dip(64), dip(64)) {
            topMargin = dip(16)
            gravity = Gravity.CENTER_HORIZONTAL
        }


        recycler.apply {
            this.adapter = adapter

            layoutManager = grid
            padding = dimen(R.dimen.grid_gutter)

            if (columnCount > 0) {
                grid.spanCount = columnCount
            } else {
                UserPrefs.albumGridColumns.openSubscription()
                    .distinctSeq()
                    .consumeEach(UI) { grid.spanCount = it }
            }

            addItemDecoration(ItemOffsetDecoration(dimen(R.dimen.grid_gutter)))


            category.openSubscription().switchMap { cat ->
                when (cat) {
                    is Category.All -> Library.instance.albums.openSubscription()
                    is Category.ByArtist -> {
                        withContext(UI) {
                            circleProgress.visibility = View.VISIBLE
                            loadCircle.start()
                        }
                        when (cat.mode) {
                            ArtistDetailsFragment.Mode.REMOTE -> produce(BG_POOL) {
                                send(cat.artist.resolveAlbums(false))
                            }
                            ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE -> produce(BG_POOL) {
                                send(cat.artist.resolveAlbums(true))
                            }
                            else -> Library.instance.albumsByArtist(cat.artist.id)
                        }
                    }
                    is Category.Custom -> produceTask { cat.albums }
                }
            }.consumeEach(BG_POOL + jobs) {
                withContext(UI) {
                    loadCircle.progressiveStop()
                    circleProgress.visibility = View.INVISIBLE
                }

                val data = if (cat !is Category.All) {
                    when (sortBy) {
                        SortBy.NONE -> it
                        SortBy.TITLE -> it.sortedWith(compareByIgnoreCase({ it.id.sortTitle }))
                        SortBy.YEAR -> it.sortedByDescending {
                            it.year ?: 0
                        }.sortedBy { it.type.ordinal }
                    }
                } else it

                if (adapter is AlbumsAdapter) {
                    adapter.updateData(data)
                } else if (adapter is AlbumSectionAdapter) {
                    task(UI) { adapter.replaceData(data) }
                }
            }
                /*.combineLatest(
                    App.instance.internetStatus.openSubscription()
                )*/
        }
    }
}