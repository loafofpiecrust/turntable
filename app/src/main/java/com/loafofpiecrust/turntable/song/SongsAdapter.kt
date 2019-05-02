package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.support.annotation.StringRes
import android.support.v4.graphics.ColorUtils
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.msToTimeString
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.popupMenu
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.combineLatest
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.views.RecyclerListItem
import com.loafofpiecrust.turntable.views.SectionedAdapter
import com.loafofpiecrust.turntable.views.SimpleHeaderViewHolder
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.textColor
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

open class SongsOnDiscAdapter(
    parentContext: CoroutineContext,
    originalChannel: ReceiveChannel<Map<String, List<Song>>>,
    @StringRes private val headerRes: Int,
    private val formatSubtitle: (Song) -> String = { msToTimeString(it.duration) },
    private val formatTrack: ((Song) -> String)? = null,
    private val onClickItem: suspend (Song) -> Unit
): SectionedAdapter<Song, String, RecyclerListItem, SimpleHeaderViewHolder>(
    parentContext,
    originalChannel
) {
    override fun onCreateItemViewHolder(parent: ViewGroup) =
        RecyclerListItem(parent)

    override fun onCreateHeaderViewHolder(parent: ViewGroup) =
        SimpleHeaderViewHolder(parent)

    override fun RecyclerListItem.onBindItem(item: Song, position: Int, job: Job) {
        bindSong(
            item,
            position,
            job,
            formatSubtitle,
            formatTrack,
            onClickItem = {
                onClickItem(item)
            }
        )
    }

    override fun SimpleHeaderViewHolder.onBindHeader(key: String, job: Job) {
        mainLine.text = itemView.context.getString(headerRes, key)
    }
}

class SongsAdapter(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<Song>>,
    private val formatSubtitle: (Song) -> String = { it.id.artist.displayName },
    private val formatTrack: ((Song) -> String)? = null,
    private val onClickItem: suspend (List<Song>, Int) -> Unit
): RecyclerBroadcastAdapter<Song, RecyclerListItem>(parentContext, channel), FastScrollRecyclerView.SectionedAdapter {
    override fun getSectionName(position: Int) =
        data[position].id.displayName.take(1)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RecyclerListItem(parent, fullSizeImage = true)

    override fun RecyclerListItem.onBind(item: Song, position: Int, job: Job) {
        bindSong(
            item,
            position,
            job,
            formatSubtitle,
            formatTrack,
            onClickItem = {
                onClickItem(data, position)
            }
        )
        track.visibility = View.INVISIBLE
        coverImage?.let { imageView ->
            launch(job) {
                item.loadCover(Glide.with(imageView)).consumeEach { req ->
                    withContext(Dispatchers.Main) {
                        if (req != null) {
                            req.into(imageView)
                        } else {
                            imageView.imageResource = R.drawable.ic_default_album
                        }
                    }
                }
            }
        }
    }
}


fun RecyclerListItem.bindSong(
    item: Song,
    position: Int,
    job: Job,
    formatSubtitle: (Song) -> String,
    formatTrack: ((Song) -> String)?,
    onClickItem: suspend (Song) -> Unit
) {
    val context = itemView.context

    track.text = formatTrack?.invoke(item) ?: (position + 1).toString()
    mainLine.text = item.id.displayName
    subLine.text = formatSubtitle(item)

    menu.onClick { v ->
        v?.popupMenu {
            queueOptions(v.context, item)
            songOptions(v.context, item)
        }
    }

    card.onClick {
        val isLocal = Library.sourceForSong(item.id) != null
        val internet = App.currentInternetStatus.value
        if (internet == App.InternetStatus.OFFLINE && !isLocal) {
            context.toast(R.string.no_internet)
        } else {
            onClickItem(item)
        }
    }

    CoroutineScope(job).launch(Dispatchers.Main) {
        MusicService.instance.switchMap {
            it?.let { music ->
                combineLatest(music.player.queue, App.internetStatus)
            }
        }.consumeEach { (q, internet) ->
            if (item.id == q.current?.id) {
                // This is the currently playing song.
//                holder.playingIcon.visibility = View.VISIBLE
//                    holder.track.visibility = View.INVISIBLE

                val c = UserPrefs.accentColor.value
                mainLine.textColor = c
                subLine.textColor = c
                track.textColor = c
            } else {
//                    holder.track.visibility = View.VISIBLE
//                holder.playingIcon.visibility = View.GONE

                // This is any other song
//                if (song.local != null) {
//                    holder.statusIcon.visibility = View.VISIBLE
//                } else {
//                    holder.statusIcon.visibility = View.GONE
//                }
                val isLocal = Library.sourceForSong(item.id) != null
                val c = if (internet == App.InternetStatus.OFFLINE && !isLocal) {
                    ColorUtils.setAlphaComponent(
                        context.colorAttr(android.R.attr.textColor),
                        100
                    )
                } else {
                    context.colorAttr(android.R.attr.textColor)
                }

                mainLine.textColor = c
                subLine.textColor = c
                track.textColor = c
            }
        }
    }
}