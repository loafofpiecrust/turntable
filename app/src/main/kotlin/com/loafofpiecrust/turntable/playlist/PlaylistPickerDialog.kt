package com.loafofpiecrust.turntable.playlist

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.PartialAlbum
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.add
import com.loafofpiecrust.turntable.model.song.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.util.arg
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.customView
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.wrapContent


class PlaylistPickerDialog: BaseDialogFragment() {
    companion object {
        fun forItem(item: SaveableMusic) = PlaylistPickerDialog().apply {
            this.item = item
        }
    }

    private var item: SaveableMusic by arg()

    override fun onStart() {
        super.onStart()
        dialog.window.setLayout(matchParent, wrapContent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        alert("Add to playlist") {
            customView { createView() }

            positiveButton("New Playlist") {
                AddPlaylistDialog.withItems(listOf(item)).show(requireContext())
            }
            negativeButton("Cancel") {}
        }.build()

    override fun ViewManager.createView() = recyclerView {
        fitsSystemWindows = true
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter = PlaylistsFragment.Adapter { selected ->
            val item = item
            when (item) {
                is Song -> {
                    when (selected) {
                        is MixTape -> selected.add(context, item)
                        is CollaborativePlaylist -> toast(
                            if (selected.add(item)) context.getString(R.string.playlist_added_track, selected.name)
                            else context.getString(R.string.playlist_is_full, selected.name)
                        )
                    }
                }
                is PartialAlbum -> {
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
                        is PartialAlbum -> it is AlbumCollection || it is CollaborativePlaylist
                        else -> TODO("Can't add Artist to playlist")
                    }
                }
            })
        }
    }
}