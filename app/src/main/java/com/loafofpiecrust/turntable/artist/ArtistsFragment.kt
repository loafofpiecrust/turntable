package com.loafofpiecrust.turntable.artist

import android.content.Context
import android.os.Parcelable
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.CardView
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.loadPalette
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.refreshRecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel.Factory.CONFLATED
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.swipeRefreshLayout

sealed class ArtistsUI(
    private val detailsMode: ArtistDetailsFragment.Mode = ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE,
    private val columnCount: Int = 0,
    private val startRefreshing: Boolean = true
) : UIComponent() {
    abstract val artists: BroadcastChannel<List<Artist>>

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.search, R.drawable.ic_search, showIcon = true).onClick {
            context.replaceMainContent(
                SearchFragment.newInstance(SearchFragment.Category.Artists())
            )
        }

        subMenu(R.string.set_grid_size) {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick { UserPrefs.artistGridColumns puts idx }
                    }
                }

                launch {
                    UserPrefs.artistGridColumns.consumeEach { cols ->
                        items.forEach { it.isChecked = false }
                        items[cols - 1].isChecked = true
                    }
                }
            }
        }
    }

    override fun AnkoContext<Any>.render() = refreshRecyclerView {
        if (startRefreshing) {
            isRefreshing = true
        }

        recycler = if (this@ArtistsUI is All) {
            fastScrollRecycler {
                turntableStyle()
            }
        } else {
            recyclerView {
                lparams(height = matchParent, width = matchParent)
                turntableStyle()
            }
        }.apply {
            val gutter = dimen(R.dimen.grid_gutter)
            padding = gutter
            addItemDecoration(ItemOffsetDecoration(gutter))
        }

        adapter = ArtistsAdapter(artists.openSubscription()) { holder, artists, idx ->
            // smoothly transition the cover image!
            holder.itemView.context.replaceMainContent(
                ArtistDetailsFragment.fromArtist(artists[idx], detailsMode),
                true,
                holder.transitionViews
            )
        }

        layoutManager = GridLayoutManager(context, 3).also { grid ->
            if (columnCount > 0) {
                grid.spanCount = columnCount
            } else launch {
                UserPrefs.artistGridColumns.consumeEach {
                    (adapter as ArtistsAdapter).apply {
                        gridSize = it
//                            notifyDataSetChanged()
                    }
                    grid.spanCount = it
                }
            }
        }
    }

    @Parcelize
    class All: ArtistsUI(
        ArtistDetailsFragment.Mode.LIBRARY
    ), Parcelable {
        override val artists = Library.instance.artistsMap
            .openSubscription()
            .map { it.values.sortedBy { it.id } }
            .broadcast(CONFLATED)
    }

    class Custom(
        override val artists: BroadcastChannel<List<Artist>>
    ): ArtistsUI(startRefreshing = false)
}

class ArtistsAdapter(
    channel: ReceiveChannel<List<Artist>>,
    private val listener: (RecyclerItem, List<Artist>, Int) -> Unit
): RecyclerAdapter<Artist, RecyclerItem>(channel), FastScrollRecyclerView.SectionedAdapter {
    var gridSize: Int = 3

    override fun getSectionName(position: Int)
        = data[position].id.sortChar.toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
        if (gridSize == 1) {
            RecyclerListItemOptimized(parent, 3)
        } else RecyclerGridItem(parent, 3)

    private val imageJobs = mutableMapOf<RecyclerItem, Job>()
    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
        val item = data[position]

        holder.coverImage?.transitionName = item.id.imageTransition
        holder.header.transitionName = item.id.nameTransition

        holder.mainLine.text = item.id.displayName
        item.id.altName?.let {
            holder.subLine.text = it
        }

        holder.card.setOnClickListener {
            listener.invoke(holder, data, position)
        }

        if (holder.card is CardView) {
            holder.card.setCardBackgroundColor(UserPrefs.primaryColor.value)
        } else {
            holder.card.backgroundColor = UserPrefs.primaryColor.value
        }

        if (holder.coverImage != null) {
            val job = launch(Dispatchers.Main) {
                item.loadThumbnail(Glide.with(holder.card.context)).consumeEach {
                    if (it != null) {
                        it.apply(RequestOptions().placeholder(R.drawable.ic_default_album))
                            .transition(DrawableTransitionOptions().crossFade(200))
                            .listener(item.loadPalette(holder.card, holder.mainLine, holder.subLine))
                            .into(holder.coverImage)
                    } else {
                        holder.coverImage.imageResource = R.drawable.ic_default_album
                    }
                }
            }
            imageJobs.put(holder, job)?.cancel()
        }

    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        imageJobs.clear()
    }

    override fun itemsSame(a: Artist, b: Artist, aIdx: Int, bIdx: Int) =
        a.id == b.id

    override fun contentsSame(a: Artist, b: Artist, aIdx: Int, bIdx: Int) =
        a.id == b.id /*&& a.artworkUrl == b.artworkUrl && a.remote == b.remote*/
}
