package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.evernote.android.state.State
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.cancelSafely
import com.loafofpiecrust.turntable.util.consumeEach
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.act

class ArtistsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class RelatedTo(val id: ArtistId): Category()
//        @Parcelize class Custom(val artists: List<Artist>): Category()
    }

    @Arg lateinit var category: Category
    @Arg(optional=true) var columnCount: Int? = null
    @State var listState: Parcelable? = null

    lateinit var artists: BroadcastChannel<List<Artist>>

    // TODO: Use Category!!
    companion object {
        fun relatedTo(artist: Artist): ArtistsFragment {
            return ArtistsFragmentStarter.newInstance(Category.RelatedTo(artist.id)).apply {
                artists = broadcast(capacity = Channel.CONFLATED) { send(Spotify.similarTo(artist.id)) }
            }
        }
        fun fromChan(channel: ReceiveChannel<List<Artist>>): ArtistsFragment {
            return ArtistsFragmentStarter.newInstance(Category.All()).apply {
                artists = channel.broadcast(Channel.CONFLATED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::artists.isInitialized) {
            artists = Library.instance.artists
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem("Search", R.drawable.ic_search, showIcon = true) {
            onClick {
                act.replaceMainContent(
                    SearchFragmentStarter.newInstance(SearchFragment.Category.ARTISTS),
                    true
                )
            }
        }

        menu.subMenu("Grid size") {
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
                    ArtistDetailsFragment.fromArtist(artists[idx]), true,
                    holder.transitionViews
                )
            }.apply {
                subscribeData(artists.openSubscription())
            }

            layoutManager = GridLayoutManager(context, 3).also { grid ->
                if (columnCount != null) {
                    grid.spanCount = columnCount!!
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
) : RecyclerAdapter<Artist, RecyclerItem>(
    itemsSame = { a, b, aIdx, bIdx -> a.id == b.id },
    contentsSame = { a, b, aIdx, bIdx -> a.id == b.id /*&& a.artworkUrl == b.artworkUrl && a.remote == b.remote*/ }
),
    FastScrollRecyclerView.SectionedAdapter
{
    var gridSize: Int = 3

    override fun getSectionName(position: Int)
        = data[position].id.sortName.first().toUpperCase().toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
        if (gridSize == 1) {
            RecyclerListItem(parent, 3)
        } else RecyclerGridItem(parent, 3)

    private val imageJobs = hashMapOf<RecyclerItem, Job>()
    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
        val item = data[position]

        holder.coverImage?.transitionName = item.id.imageTransition
        holder.header.transitionName = item.id.nameTransition

        holder.mainLine.text = item.id.displayName
        given(item.id.altName) {
            holder.subLine.text = it
        }

        holder.card.setOnClickListener {
            listener.invoke(holder, data, position)
        }

        holder.card.backgroundColor = UserPrefs.primaryColor.value

        if (holder.coverImage != null) {
            val job = async(UI) {
                item.loadThumbnail(Glide.with(holder.card.context)).consumeEach {
                    if (it != null) {
                        it.apply(RequestOptions().placeholder(R.drawable.ic_default_album))
                            .listener(item.loadPalette(holder.card, holder.mainLine, holder.subLine))
                            .transition(DrawableTransitionOptions().crossFade(200))
                            .into(holder.coverImage)
                    } else {
                        holder.coverImage.imageResource = R.drawable.ic_default_album
                    }
                }
            }
            imageJobs.put(holder, job)?.cancelSafely()
        }

    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        imageJobs.forEach { (holder, job) -> job.cancel() }
        imageJobs.clear()
    }
}
