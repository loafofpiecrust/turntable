package com.loafofpiecrust.turntable.artist

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.CardView
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.Menu
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.loadPalette
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.*
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView

sealed class ArtistsUI(
    private val detailsMode: ArtistDetailsUI.Mode = ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE,
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

    override fun CoroutineScope.render(ui: AnkoContext<Any>) = ui.refreshableRecyclerView {
        if (startRefreshing) {
            isRefreshing = true
        }

        channel = artists.openSubscription()

        contentView {
            if (this@ArtistsUI is All) {
                fastScrollRecycler {
                    turntableStyle()
                }
            } else {
                recyclerView {
                    turntableStyle()
                }
            }.apply {
                adapter = ArtistsAdapter(artists.openSubscription()) { holder, artists, idx ->
                    // smoothly transition the cover image!
                    holder.itemView.context.replaceMainContent(
                        ArtistDetailsUI.Resolved(artists[idx], detailsMode).createFragment(),
//                        ArtistDetailsFragment.fromArtist(artists[idx], detailsMode),
                        true,
                        holder.transitionViews
                    )
                }
                layoutManager = GridLayoutManager(context, 3)

                val gutter = dimen(R.dimen.grid_gutter)
                padding = gutter
                addItemDecoration(ItemOffsetDecoration(gutter))
            }
        }

        emptyView {
            verticalLayout {
                gravity = Gravity.CENTER
                textView("Sorry no artists :(")
            }
        }
    }

    @Parcelize
    class All: ArtistsUI(
        ArtistDetailsUI.Mode.LIBRARY
    ), Parcelable {
        override val artists = Library.instance.artistsMap
            .openSubscription()
            .map { it.values.sortedBy { it.id } }
            .replayOne()
    }

    class Custom(
        override val artists: BroadcastChannel<List<Artist>>,
        startRefreshing: Boolean = false
    ): ArtistsUI(startRefreshing = startRefreshing)
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

        (holder.card as? CardView)?.let { card ->
            card.setCardBackgroundColor(UserPrefs.primaryColor.value)
        } ?: run {
            holder.card.backgroundColor = UserPrefs.primaryColor.value
        }

        (holder.coverImage)?.let { cover ->
            val job = launch(Dispatchers.Main) {
                item.loadThumbnail(Glide.with(holder.card.context)).consumeEach {
                    if (it != null) {
                        it.apply(RequestOptions().placeholder(R.drawable.ic_default_album))
                            .transition(DrawableTransitionOptions().crossFade(200))
                            .listener(item.loadPalette(holder.card, holder.mainLine, holder.subLine))
                            .into(cover)
                    } else {
                        cover.imageResource = R.drawable.ic_default_album
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
