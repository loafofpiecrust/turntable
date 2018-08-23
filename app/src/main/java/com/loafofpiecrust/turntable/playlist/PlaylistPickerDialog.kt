package com.loafofpiecrust.turntable.playlist

import activitystarter.ActivityStarter
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.wrapContent


class PlaylistPickerDialog: BaseDialogFragment() {
    companion object {
        fun forItem(item: Music) = PlaylistPickerDialog().apply {
            this.item = item
        }
    }

    lateinit var item: Music

    override fun onStart() {
        super.onStart()
        dialog.window.setLayout(matchParent, wrapContent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        ActivityStarter.fill(this)
        val builder = AlertDialog.Builder(activity)
        builder.setMessage("Add to playlist")
            .setPositiveButton("New Playlist") { _, _ ->
                val item = this.item
                val id = when (item) {
                    is Song -> item.id
                    is Album -> item.id
                    is Artist -> item.id
                    else -> error("Music can only be song, album, artist")
                }
                AddPlaylistActivityStarter.start(ctx, AddPlaylistActivity.TrackList(listOf(id)))
                dismiss()
            }
            .setNegativeButton("Cancel") { dialog, id ->
                // User cancelled the dialog
                dismiss()
            }
            .setView(AnkoContext.create(ctx, this).createView())
        // Create the AlertDialog object and return it
        return builder.create()
    }

    override fun ViewManager.createView() = recyclerView {
        fitsSystemWindows = true
        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
        adapter = PlaylistsFragment.Adapter { selected ->
            val item = item
            when (item) {
                is Song -> {
                    when (selected) {
                        is MixTape -> selected.add(ctx, item)
                        is CollaborativePlaylist -> toast(
                            if (selected.add(item)) ctx.getString(R.string.playlist_added_track, selected.name)
                            else ctx.getString(R.string.playlist_is_full, selected.name)
                        )
                    }
                }
                is Album -> {
                    when (selected) {
                        is AlbumCollection -> selected.add(item)
                    }
                }
            }
            dismiss()
        }.also {
            it.subscribeData(UserPrefs.playlists.openSubscription().map {
                it.filter {
                    when (item) {
                        is Song -> it is MixTape || it is CollaborativePlaylist
                        is Album -> it is AlbumCollection || it is CollaborativePlaylist
                        else -> throw IllegalStateException("Can't add Artist to playlist")
                    }
                }
            })
        }
    }
}