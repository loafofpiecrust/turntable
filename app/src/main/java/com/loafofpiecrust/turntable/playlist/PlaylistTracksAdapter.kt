package com.loafofpiecrust.turntable.playlist

import android.view.View
import android.view.ViewGroup
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.ui.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk27.coroutines.onClick

class PlaylistTracksAdapter(
    private val playlist: CollaborativePlaylist
): RecyclerBroadcastAdapter<Song, RecyclerListItemOptimized>() {
    init {
        subscribeData(playlist.tracks)
    }

    override val moveEnabled get() = true
    override val dismissEnabled get() = true
    override fun canMoveItem(index: Int) = true


    override fun onItemMove(fromIdx: Int, toIdx: Int) {
        println("playlist: moving from $fromIdx to $toIdx")
        playlist.move(fromIdx, toIdx)
    }

    override fun onItemDismiss(idx: Int) {
        playlist.remove(idx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItemOptimized {
        return RecyclerListItemOptimized(parent)
    }

    override fun RecyclerListItemOptimized.onBind(item: Song, position: Int, job: Job) {
        card.onClick {
            MusicService.enact(SyncService.Message.PlaySongs(data, position))
        }

        mainLine.text = item.id.displayName
        subLine.text = item.id.artist.displayName
        if (playlist.isCompletable) {
            track.visibility = View.INVISIBLE
            statusIcon.visibility = View.VISIBLE
            UserPrefs.history.consumeEach(UI + job) { history ->
                val entry = history.find { it.song.id == item.id }
                if (entry != null && entry.timestamp > playlist.createdTime.time) {
                    statusIcon.imageResource = R.drawable.ic_check_box
                } else {
                    statusIcon.imageResource = R.drawable.ic_check_box_outline_blank
                }
            }
        } else {
            track.visibility = View.VISIBLE
            statusIcon.visibility = View.INVISIBLE
            track.text = (position + 1).toString()
        }
    }
}