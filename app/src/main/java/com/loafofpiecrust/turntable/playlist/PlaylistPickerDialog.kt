package com.loafofpiecrust.turntable.playlist

import activitystarter.ActivityStarter
import activitystarter.Arg
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.album.Album
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
    @Arg lateinit var item: Music

    override fun onStart() {
        super.onStart()
        dialog.window.setLayout(matchParent, wrapContent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        ActivityStarter.fill(this)
        val builder = AlertDialog.Builder(activity)
        builder.setMessage("Add to playlist")
            .setPositiveButton("New Playlist") { dialog, id ->
                AddPlaylistActivityStarter.start(ctx, AddPlaylistActivity.TrackList(listOf(item)))
                dismiss()
            }
            .setNegativeButton("Cancel") { dialog, id ->
                // User cancelled the dialog
                dismiss()
            }
            .setView(makeView(null, AnkoContext.create(ctx, this)))
        // Create the AlertDialog object and return it
        return builder.create()
    }

    override fun makeView(parent: ViewGroup?, manager: ViewManager) = manager.recyclerView {
        fitsSystemWindows = true
        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
        adapter = PlaylistsFragment.Adapter { selected ->
            val item = item
            when (item) {
                is Song -> {
                    when (selected) {
                        is MixTape -> selected.add(0, item)
                        is CollaborativePlaylist -> selected.add(item)
                        else -> false
                    }
                }
                is Album -> {
                    when (selected) {
                        is AlbumCollection -> selected.add(item)
                        else -> false
                    }
                }
                else -> false
            }.also { wasAdded ->
                toast(
                    if (wasAdded) "Added to '${selected.name}'"
                    else "'${selected.name}' is full or already has that song"
                )
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