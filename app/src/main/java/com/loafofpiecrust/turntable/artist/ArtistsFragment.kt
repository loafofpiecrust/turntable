package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.SearchView
import android.view.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.produceTask
import com.loafofpiecrust.turntable.util.task
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

class ArtistsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize class Custom(val artists: List<Artist>): Category()
    }

    @Arg lateinit var category: Category
    @Arg(optional=true) var columnCount: Int? = null


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem("Search", R.drawable.ic_search, showIcon = true) {
            actionView = SearchView(ctx).apply {
            }
            onClick {
                MainActivity.replaceContent(
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

                task(UI) {
                    UserPrefs.artistGridColumns.consumeEach { cols ->
                        items.forEach { it.isChecked = false }
                        items[cols - 1].isChecked = true
                    }
                }
            }
        }
    }

    override fun makeView(ui: ViewManager): View = with(ui) {
        val cat = category

        val recycler = if (cat is Category.All) {
            fastScrollRecycler {
                turntableStyle(jobs)
            }
        } else {
            recyclerView {
                lparams(height=matchParent, width=matchParent)
                turntableStyle(jobs)
            }
        }

        recycler.apply {
            this.adapter = ArtistsAdapter { holder, artists, idx ->
                // smoothly transition the cover image!
                ctx.replaceMainContent(
                    ArtistDetailsFragmentStarter.newInstance(artists[idx]), true,
                    holder.transitionViews
                )
            }.apply {
                task(UI) {
                    when (cat) {
                        is Category.All -> Library.instance.artists.openSubscription()
                        is Category.Custom -> produceTask { cat.artists }
                    }.consumeEach {
                        updateData(it)
                    }
                }
            }

            layoutManager = GridLayoutManager(context, 3).also { grid ->
                if (columnCount != null) {
                    grid.spanCount = columnCount!!
                } else task(UI) {
                    UserPrefs.artistGridColumns.consumeEach {
                        (adapter as ArtistsAdapter).apply {
                            gridSize = it
                            notifyDataSetChanged()
                        }
                        grid.spanCount = it
                    }
                }
            }
        }
    }
}


class ArtistsAdapter(
    private val listener: (RecyclerItem, List<Artist>, Int) -> Unit
) : RecyclerAdapter<Artist, RecyclerItem>(
    itemsSame = { a, b, aIdx, bIdx -> a.id == b.id },
    contentsSame = { a, b, aIdx, bIdx -> a.id == b.id && a.artworkUrl == b.artworkUrl && a.remote == b.remote }
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
        given(item.disambiguation) {
            holder.subLine.text = it
        }

        holder.card.setOnClickListener {
            listener.invoke(holder, data, position)
        }

        holder.card.backgroundColor = UserPrefs.primaryColor.value

        if (holder.coverImage != null) {
            val job = task(UI) {
                item.loadArtwork(Glide.with(holder.card.context)).consumeEach {
                    given(it) {
                        it.apply(RequestOptions().placeholder(R.drawable.ic_default_album))
                            .listener(loadPalette(item.id, holder.card))
                            .transition(DrawableTransitionOptions().crossFade(200))
//                withContext(UI) {
                            .into(holder.coverImage)
//                }
                    } ?: run {
                        holder.coverImage.imageResource = R.drawable.ic_default_album
                    }
                }
            }
            imageJobs.put(holder, job)?.cancel()
        }

    }
}