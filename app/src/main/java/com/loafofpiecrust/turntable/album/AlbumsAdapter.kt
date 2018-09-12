package com.loafofpiecrust.turntable.album

import android.graphics.Typeface
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.getColorCompat
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.imageTransition
import com.loafofpiecrust.turntable.song.nameTransition
import com.loafofpiecrust.turntable.util.textStyle
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerGridItem
import com.loafofpiecrust.turntable.ui.RecyclerItem
import com.loafofpiecrust.turntable.ui.SectionedRecyclerAdapter
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.cancelSafely
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.withContext
import org.jetbrains.anko.*

// Album categories: All, ByArtist
// Song categories: All, ByArtist, Album, Playlist
// ByArtist categories: All

/**
 * [RecyclerView.Adapter] that can display a [Album]
 */
class AlbumsAdapter(
    private val byArtist: Boolean,
    private val listener: ((RecyclerItem, Album) -> Unit)?
) : RecyclerAdapter<Album, RecyclerItem>(
    itemsSame = { a, b, aIdx, bIdx -> a.id == b.id },
    contentsSame = { a, b, aIdx, bIdx -> a == b }
), FastScrollRecyclerView.SectionedAdapter {
    override fun getSectionName(position: Int): String
        = data[position].id.sortChar.toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        = RecyclerGridItem(parent, 3)

    private val imageJobs = HashMap<RecyclerItem, Job>()
    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
        // Runs as you re-scroll
        // So, could grab directly from a stream of the albums rather than a fixed list passed in
        val album = data[position]

        holder.card.setOnClickListener {
            listener?.invoke(holder, album)
        }

        holder.mainLine.text = album.id.displayName
        holder.subLine.text = if (byArtist) {
            album.year?.toString() ?: ""
        } else {
            album.id.artist.name
        }

        // TODO: Generalize transition elements between list and grid layouts
        holder.mainLine.transitionName = album.id.nameTransition
        holder.coverImage?.transitionName = album.id.imageTransition

        if (holder.card is CardView) {
            holder.card.setCardBackgroundColor(UserPrefs.primaryColor.value)
        } else {
            holder.card.backgroundColor = UserPrefs.primaryColor.value
        }
        holder.mainLine.textColor = holder.card.context.getColorCompat(R.color.text)
        holder.subLine.textColor = holder.card.context.getColorCompat(R.color.text)

        val opts = RequestOptions()
            .placeholder(R.drawable.ic_default_album)

        if (holder.coverImage != null) {
            holder.coverImage.imageResource = R.drawable.ic_default_album
            val job = async(BG_POOL) {
                album.loadThumbnail(Glide.with(holder.card.context)).consumeEach {
                    val req = it?.apply(opts)
                        ?.transition(DrawableTransitionOptions().crossFade(200))
                        ?.listener(album.loadPalette(holder.card, holder.mainLine, holder.subLine))

                    withContext(UI) {
                        req?.into(holder.coverImage) ?: run {
                            holder.coverImage.imageResource = R.drawable.ic_default_album
                        }
                    }
                }
            }
            imageJobs.put(holder, job)?.cancelSafely()
        }
    }

    override fun onViewRecycled(holder: RecyclerItem) {
        imageJobs.remove(holder)?.cancelSafely()
        if (holder.coverImage != null) {
            Glide.with(holder.card.context)
                .clear(holder.coverImage)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        imageJobs.forEach { (holder, job) -> job.cancel() }
        imageJobs.clear()
    }
}

class AlbumSectionAdapter(
    private val listener: (RecyclerItem, Album) -> Unit?
): SectionedRecyclerAdapter<Album, Album.Type, SectionedViewHolder>(
    groupBy = { it.type }
) {
    override fun onBindFooterViewHolder(holder: SectionedViewHolder?, section: Int) {
        
    }

    override fun onBindHeaderViewHolder(holder: SectionedViewHolder, section: Int, expanded: Boolean) {
        val holder = (holder as? HeaderItem) ?: return
        val type = groupedData[section].first

        holder.mainLine.text = type.name
    }

    private val imageJobs = HashMap<RecyclerItem, Job>()
    override fun onBindViewHolder(holder: SectionedViewHolder, section: Int, relativePosition: Int, absolutePosition: Int) {
        val holder = (holder as? RecyclerGridItem) ?: return
        val album = groupedData[section].second[relativePosition]

        holder.header.transitionName = album.id.nameTransition
        holder.coverImage?.transitionName = album.id.imageTransition

        holder.card.setOnClickListener {
            listener.invoke(holder, album)
        }

        holder.mainLine.text = album.id.displayName
//        if (byArtist) {
            holder.subLine.text = album.year?.toString() ?: ""
//        } else {
//            holder.subLine.text = album.artist
//        }


        holder.card.backgroundColor = UserPrefs.primaryColor.value
        holder.mainLine.textColor = holder.card.context.getColorCompat(R.color.text)
        holder.subLine.textColor = holder.card.context.getColorCompat(R.color.text)


        if (holder.coverImage != null) {
            val opts = RequestOptions()
//            .override(250)
                .placeholder(R.drawable.ic_default_album)
                .signature(ObjectKey(album.id.displayName))

            imageJobs.put(holder, async(UI) {
                album.loadThumbnail(Glide.with(holder.card.context)).consumeEach {
                    it?.apply(opts)
                        ?.transition(DrawableTransitionOptions().crossFade(200))
                        ?.listener(album.loadPalette(holder.card, holder.mainLine, holder.subLine))
                        ?.into(holder.coverImage) ?: run {
                            holder.coverImage.imageResource = R.drawable.ic_default_album
                        }
                }
            })?.cancelSafely()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionedViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderItem(parent)
            else -> RecyclerGridItem(parent, 3)
        }
    }


    inner class HeaderItem(
        parent: ViewGroup
    ) : SectionedViewHolder(AnkoContext.create(parent.context, parent).linearLayout {
        padding = dimen(R.dimen.text_content_margin)
        textView {
            id = R.id.mainLine
            textStyle = Typeface.BOLD
        }
    }) {
        val mainLine: TextView = itemView.find(R.id.mainLine)
    }

}
