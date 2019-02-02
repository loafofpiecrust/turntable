package com.loafofpiecrust.turntable.artist

import android.graphics.Typeface
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.loadPalette
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.textStyle
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.views.RecyclerGridItem
import com.loafofpiecrust.turntable.views.RecyclerItem
import com.loafofpiecrust.turntable.views.RecyclerListItem
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.imageResource
import kotlin.coroutines.CoroutineContext

class ArtistsAdapter(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<Artist>>,
    private val listener: (RecyclerItem, List<Artist>, Int) -> Unit
): RecyclerAdapter<Artist, RecyclerItem>(parentContext, channel), FastScrollRecyclerView.SectionedAdapter {
    var gridSize: Int = 1

    override fun getSectionName(position: Int) =
        data[position].id.sortChar.toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
        if (gridSize == 1) {
            RecyclerListItem(parent, 3, fullSizeImage = true)
        } else RecyclerGridItem(parent, 3)

    private val imageJobs = mutableMapOf<RecyclerItem, Job>()
    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
        val item = data[position]

        holder.coverImage?.transitionName = item.id.imageTransition
        holder.header.transitionName = item.id.nameTransition

        holder.mainLine.text = item.id.displayName
        holder.mainLine.textStyle = Typeface.BOLD

        item.id.altName?.let {
            holder.subLine.text = it
        }
        (holder as? RecyclerListItem)?.menu?.visibility = View.GONE

        holder.card.setOnClickListener {
            listener.invoke(holder, data, holder.adapterPosition)
        }

//        (holder.card as? CardView)?.let { card ->
//            card.setCardBackgroundColor(UserPrefs.primaryColor.value)
//        } ?: run {
//            holder.card.backgroundColor = UserPrefs.primaryColor.value
//        }

        (holder.coverImage)?.let { cover ->
            val job = launch(Dispatchers.Default) {
                item.loadThumbnail(Glide.with(holder.card.context)).consumeEach { req ->
                    if (req != null) {
                        val req = req.transition(DrawableTransitionOptions().crossFade(150))

                        withContext(Dispatchers.Main) {
//                            req.listener(item.loadPalette(holder.card, holder.mainLine, holder.subLine))
                            req.into(cover)
                        }
                    } else withContext(Dispatchers.Main) {
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
        a.id == b.id
}