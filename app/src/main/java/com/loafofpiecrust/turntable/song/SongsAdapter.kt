package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.support.annotation.StringRes
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.ui.SectionedAdapter
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.views.SimpleHeaderViewHolder
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.image
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.textColor
import kotlin.coroutines.CoroutineContext

class SongsOnDiscAdapter(
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
        RecyclerListItem(parent)

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
        onClickItem(item)
    }


    val context = itemView.context
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
                    context.getColorCompat(R.color.text_unavailable)
                } else context.colorAttr(android.R.attr.textColor)
                mainLine.textColor = c
                subLine.textColor = c
                track.textColor = c
            }
        }
    }
}