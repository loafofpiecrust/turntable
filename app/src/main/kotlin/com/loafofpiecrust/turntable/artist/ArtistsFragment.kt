package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import android.support.v7.widget.CardView
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.evernote.android.state.State
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.loadPalette
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.song.imageTransition
import com.loafofpiecrust.turntable.model.song.nameTransition
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.dimen
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.act

class ArtistsFragment : BaseFragment() {
    private sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class RelatedTo(val id: ArtistId): Category()
        @Parcelize class Custom: Category()
    }

    private var category: Category by arg(Category.Custom())
    private var detailsMode by arg(ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE)
    private var columnCount: Int by arg(0)
    @State var listState: Parcelable? = null

    lateinit var artists: BroadcastChannel<List<Artist>>

    // TODO: Use Category!!
    companion object {
        fun all() = ArtistsFragment().apply {
            category = Category.All()
            detailsMode = ArtistDetailsFragment.Mode.LIBRARY
        }
        fun relatedTo(artistId: ArtistId) = ArtistsFragment().apply {
            category = Category.RelatedTo(artistId)
        }
        fun fromChan(channel: ReceiveChannel<List<Artist>>) = ArtistsFragment().apply {
            artists = channel.replayOne(BG_POOL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::artists.isInitialized) {
            val cat = category
            artists = when (cat) {
                is Category.All -> Library.instance.artists
                is Category.RelatedTo -> produce(BG_POOL) {
                    send(Spotify.similarTo(cat.id))
                }.replayOne()
                // Should be an unreachable case.
                is Category.Custom -> broadcast { send(emptyList()) }
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem(R.string.search, R.drawable.ic_search, showIcon = true) {
            onClick {
                act.replaceMainContent(
                    SearchFragment.newInstance(SearchFragment.Category.Artists()),
                    true
                )
            }
        }

        menu.subMenu(R.string.set_grid_size) {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick { UserPrefs.artistGridColumns puts idx }
                    }
                }

                UserPrefs.artistGridColumns.consumeEach(UI) { cols ->
                    items.forEach { it.isChecked = false }
                    items[cols - 1].isChecked = true
                }
            }
        }
    }

    override fun ViewManager.createView(): View = with(this) {
        val cat = category

        val recycler = if (cat is Category.All) {
            fastScrollRecycler {
                turntableStyle()
            }
        } else {
            recyclerView {
                lparams(height = matchParent, width = matchParent)
                turntableStyle()
            }
        }

        recycler.apply {
            adapter = ArtistsAdapter { holder, artists, idx ->
                listState = layoutManager.onSaveInstanceState()
                // smoothly transition the cover image!
                holder.itemView.context.replaceMainContent(
                    ArtistDetailsFragment.fromArtist(artists[idx], ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE), true,
                    holder.transitionViews
                )
            }.apply {
                subscribeData(artists.openSubscription())
            }

            layoutManager = GridLayoutManager(context, 3).also { grid ->
                if (listState != null) {
                    grid.onRestoreInstanceState(listState)
                }
                if (columnCount > 0) {
                    grid.spanCount = columnCount
                } else {
                    UserPrefs.artistGridColumns.consumeEach(UI) {
                        (adapter as ArtistsAdapter).apply {
                            gridSize = it
//                            notifyDataSetChanged()
                        }
                        grid.spanCount = it
                    }
                }
            }

            padding = dimen(R.dimen.grid_gutter)
            addItemDecoration(ItemOffsetDecoration(dimen(R.dimen.grid_gutter)))
        }
    }
}


class ArtistsAdapter(
    private val listener: (RecyclerItem, List<Artist>, Int) -> Unit
): RecyclerAdapter<Artist, RecyclerItem>(), FastScrollRecyclerView.SectionedAdapter {
    var gridSize: Int = 3

    override fun getSectionName(position: Int)
        = data[position].id.sortChar.toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
        if (gridSize == 1) {
            RecyclerListItem(parent, 3)
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
        }
//        holder.card.backgroundColor = UserPrefs.primaryColor.value

        if (holder.coverImage != null) {
            val job = async(UI) {
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
        imageJobs.forEach { (holder, job) -> job.cancel() }
        imageJobs.clear()
    }

    override fun itemsSame(a: Artist, b: Artist, aIdx: Int, bIdx: Int) =
        a.id == b.id

    override fun contentsSame(a: Artist, b: Artist, aIdx: Int, bIdx: Int) =
        a.id == b.id /*&& a.artworkUrl == b.artworkUrl && a.remote == b.remote*/
}
