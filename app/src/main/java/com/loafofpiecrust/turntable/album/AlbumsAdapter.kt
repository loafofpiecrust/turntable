package com.loafofpiecrust.turntable.album

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.views.RecyclerGridItem
import com.loafofpiecrust.turntable.ui.SectionedAdapter
import com.loafofpiecrust.turntable.util.pluralResource
import com.loafofpiecrust.turntable.views.SimpleHeaderViewHolder
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.textColor
import kotlin.coroutines.CoroutineContext

private fun RecyclerGridItem.bindAlbum(
    album: Album,
    byArtist: Boolean
) {

    mainLine.text = album.id.displayName
    subLine.text = if (byArtist) {
        if (album.year > 0) {
            album.year.toString()
        } else ""
    } else {
        album.id.artist.name
    }

    // TODO: Generalize transition elements between list and grid layouts
    mainLine.transitionName = album.id.nameTransition
    coverImage.transitionName = album.id.imageTransition

    card.setCardBackgroundColor(UserPrefs.primaryColor.value)
    val textColor = itemView.context.colorAttr(android.R.attr.textColor)
    mainLine.textColor = textColor
    subLine.textColor = textColor
}

// Album categories: All, ByArtist
// Song categories: All, ByArtist, Album, Playlist
// ByArtist categories: All

/**
 * [RecyclerView.Adapter] that can display a [Album]
 */
class AlbumsAdapter(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<Album>>,
    private val byArtist: Boolean,
    private val listener: ((RecyclerGridItem, Album) -> Unit)?
) : RecyclerAdapter<Album, RecyclerGridItem>(parentContext, channel), FastScrollRecyclerView.SectionedAdapter {
    override fun itemsSame(a: Album, b: Album, aIdx: Int, bIdx: Int) =
        a.id == b.id

    override fun getSectionName(position: Int): String
        = data[position].id.sortChar.toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        = RecyclerGridItem(parent, 3)

    private val imageJobs = HashMap<RecyclerGridItem, Job>()
    override fun onBindViewHolder(holder: RecyclerGridItem, position: Int) = with(holder) {
        // Runs as you re-scroll
        // So, could grab directly from a stream of the albums rather than a fixed list passed in
        val album = data[position]
        bindAlbum(album, byArtist)

        val opts = RequestOptions()
//            .placeholder(R.drawable.ic_default_album)

        val job = Job(supervisor)
        imageJobs.put(holder, job)?.cancel()

        launch(job) {
            album.loadThumbnail(Glide.with(coverImage)).consumeEach {
                val req = it?.apply(opts)
                    ?.transition(DrawableTransitionOptions().crossFade(200))
                    ?.listener(album.loadPalette(card, mainLine, subLine))

                withContext(Dispatchers.Main) {
                    req?.into(coverImage) ?: run {
                        coverImage.imageResource = R.drawable.ic_default_album
                    }
                }
            }
        }


        card.setOnClickListener {
            listener?.invoke(holder, album)
        }

        Unit
    }

    override fun onViewRecycled(holder: RecyclerGridItem) {
        imageJobs.remove(holder)?.cancel()
        Glide.with(holder.card.context)
            .clear(holder.coverImage)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        imageJobs.forEach { (holder, job) -> job.cancel() }
        imageJobs.clear()
    }
}


class AlbumAdapterSectioned(
    parentContext: CoroutineContext,
    originalChannel: ReceiveChannel<List<Album>>,
    private val listener: (RecyclerGridItem, Album) -> Unit?
): SectionedAdapter<Album, Album.Type, RecyclerGridItem, SimpleHeaderViewHolder>(
    parentContext,
    originalChannel.map { albums ->
        albums.groupBy { it.type }
    }
) {
    override val moveEnabled get() = false
    override val dismissEnabled get() = false

    override fun onCreateItemViewHolder(parent: ViewGroup) =
        RecyclerGridItem(parent)

    override fun onCreateHeaderViewHolder(parent: ViewGroup) =
        SimpleHeaderViewHolder(parent)

    override fun RecyclerGridItem.onBindItem(item: Album, position: Int, job: Job) {
        bindAlbum(item, true)

        val opts = RequestOptions()
//            .placeholder(R.drawable.ic_default_album)

        launch(job) {
            item.loadThumbnail(Glide.with(coverImage)).consumeEach {
                val req = it?.apply(opts)
                    ?.transition(DrawableTransitionOptions().crossFade(200))
                    ?.listener(item.loadPalette(card, mainLine, subLine))

                withContext(Dispatchers.Main) {
                    req?.into(coverImage) ?: run {
                        coverImage.imageResource = R.drawable.ic_default_album
                    }
                }
            }
        }

        card.onClick {
            listener(this@onBindItem, item)
        }
    }

    override fun SimpleHeaderViewHolder.onBindHeader(key: Album.Type, job: Job) {
        val context = itemView.context
        val title = context.resources.getQuantityString(key.pluralResource(context), 10)
        mainLine.text = title
    }

    override fun dataItemsSame(a: Album, b: Album) = a.id == b.id
    override fun dataContentsSame(a: Album, b: Album) =
        a.id == b.id && a.year == b.year && a.type == b.type


}