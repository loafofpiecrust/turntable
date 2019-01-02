package com.loafofpiecrust.turntable.playlist

import android.view.View
import android.view.ViewGroup
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.views.RecyclerListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk27.coroutines.onClick
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.min

class PlaylistTracksAdapter(
    parentContext: CoroutineContext,
    private val playlist: CollaborativePlaylist
): RecyclerBroadcastAdapter<Song, RecyclerListItem>(parentContext, playlist.tracksChannel) {

    override val moveEnabled get() = true
    override val dismissEnabled get() = true
    override fun canMoveItem(index: Int) = true


    override fun onItemMove(fromIdx: Int, toIdx: Int) {
        println("playlist: moving from $fromIdx to $toIdx")
        playlist.move(fromIdx, toIdx)

        // Refresh the items shifted to reflect their new index numbers.
        val earliest = min(fromIdx, toIdx)
        val dist = abs(fromIdx - toIdx) + 1
        notifyItemRangeChanged(earliest, dist)
    }

    override fun onItemDismiss(idx: Int) {
        playlist.remove(idx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItem {
        return RecyclerListItem(parent)
    }

    override fun RecyclerListItem.onBind(item: Song, position: Int, job: Job) {
        card.onClick {
            MusicService.offer(PlayerAction.PlaySongs(data, position))
        }

        mainLine.text = item.id.displayName
        subLine.text = item.id.artist.displayName
        if (playlist.isCompletable) {
            track.visibility = View.INVISIBLE
            coverImage?.visibility = View.VISIBLE
            launch(Dispatchers.Main + job) {
                UserPrefs.history.consumeEach { history ->
                    val entry = history.find { it.song.id == item.id }
                    if (entry != null && entry.timestamp > playlist.createdTime.time) {
                        coverImage?.imageResource = R.drawable.ic_check_box
                    } else {
                        coverImage?.imageResource = R.drawable.ic_check_box_outline_blank
                    }
                }
            }
        } else {
            track.visibility = View.VISIBLE
            coverImage?.visibility = View.INVISIBLE
            track.text = (position + 1).toString()
        }
    }
}