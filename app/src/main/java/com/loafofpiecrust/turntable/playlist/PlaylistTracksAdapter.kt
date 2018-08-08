package com.loafofpiecrust.turntable.playlist

import android.view.View
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.android.UI
import org.jetbrains.anko.imageResource

class PlaylistTracksAdapter(
    private val playlist: Playlist,
    private val category: SongsFragment.Category? = null,
    private val listener: (List<Song>, Int) -> Unit
): SongsAdapter(category, listener) {
    override fun onBindViewHolder(holder: RecyclerListItemOptimized, position: Int) {
        val subs = progressSubs[holder]!!

        val item = data[position]
        holder.mainLine.text = item.id.displayName
        holder.subLine.text = item.id.artist.displayName
        if (playlist.isCompletable) {
            holder.track.visibility = View.INVISIBLE
            holder.statusIcon.visibility = View.VISIBLE
            UserPrefs.history.consumeEach(UI + subs) { history ->
                val entry = history.find { it.song.id == item.id }
                if (entry != null && entry.timestamp > playlist.createdTime.time) {
                    holder.statusIcon.imageResource = R.drawable.ic_check_box
                } else {
                    holder.statusIcon.imageResource = R.drawable.ic_check_box_outline_blank
                }
            }
        } else {
            holder.track.visibility = View.VISIBLE
            holder.statusIcon.visibility = View.INVISIBLE
            holder.track.text = (position + 1).toString()
        }
    }
}