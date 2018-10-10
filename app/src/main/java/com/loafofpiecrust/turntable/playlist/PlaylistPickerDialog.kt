package com.loafofpiecrust.turntable.playlist

import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.album.PartialAlbum
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.model.song.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.Closable
import com.loafofpiecrust.turntable.ui.UIComponent
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView


//class PlaylistPickerDialog: BaseDialogFragment() {
//    companion object {
//        fun forItem(item: SavableMusic) = PlaylistPickerDialog().apply {
//            this.item = item
//        }
//    }
//
//    private var item: SavableMusic by arg()
//
//    override fun onStart() {
//        super.onStart()
//        dialog.window?.setLayout(matchParent, wrapContent)
//    }
//
//    override fun onCreateDialog(savedInstanceState: Bundle?) = alert {
//        title = "Add to Playlist"
//
//        customView { createView() }
//
//        positiveButton("New Playlist") {
//            AddPlaylistDialog.withItems(listOf(item)).show(requireContext(), fullscreen = true)
//        }
//        negativeButton("Cancel") {}
//    }.build() as Dialog
//
//    override fun ViewManager.createView() = recyclerView {
//        topPadding = dip(8)
//
//        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
//        adapter = PlaylistsFragment.Adapter { selected ->
//            val item = item
//            when (item) {
//                is Song -> {
//                    when (selected) {
//                        is MixTape -> selected.add(context, item)
//                        is CollaborativePlaylist -> toast(
//                            if (selected.add(item)) context.getString(R.string.playlist_added_track, selected.name)
//                            else context.getString(R.string.playlist_is_full, selected.name)
//                        )
//                    }
//                }
//                is PartialAlbum -> {
//                    when (selected) {
//                        is CollaborativePlaylist -> if (selected.addAll(item.tracks)) {
//                            toast("Added all tracks to '${selected.name}'")
//                        } else {
//                            toast("Duplicate ignored")
//                        }
//                        is AlbumCollection -> if (selected.add(item)) {
//                            toast(context.getString(R.string.playlist_added_track, selected.name))
//                        } else {
//                            toast("Duplicate ignored")
//                        }
//                    }
//                }
//            }
//            dismiss()
//        }.also {
//            it.subscribeData(UserPrefs.playlists.openSubscription().map {
//                it.filter {
//                    when (item) {
//                        is Song -> it is MixTape || it is CollaborativePlaylist
//                        is PartialAlbum -> it is AlbumCollection || it is CollaborativePlaylist
//                        is HasTracks -> it is CollaborativePlaylist
//                        else -> TODO("Can't add Artist to playlist")
//                    }
//                }
//            })
//        }
//    }
//}

@Parcelize
class PlaylistPicker(
    val item: SavableMusic
) : UIComponent(), Parcelable {
    override fun AlertBuilder<*>.prepare() {
        title = "Add to Playlist"

        positiveButton("New Playlist") {
            AddPlaylistDialog.withItems(listOf(item)).show(ctx, fullscreen = true)
        }
        negativeButton("Cancel") {}
    }
    override fun AnkoContext<Any>.render() = recyclerView {
        topPadding = dip(8)

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
                        is CollaborativePlaylist -> if (selected.addAll(item.tracks)) {
                            toast("Added all tracks to '${selected.name}'")
                        } else {
                            toast("Duplicate ignored")
                        }
                        is AlbumCollection -> if (selected.add(item)) {
                            toast(context.getString(R.string.playlist_added_track, selected.name))
                        } else {
                            toast("Duplicate ignored")
                        }
                    }
                }
            }

            (owner as? Closable)?.close()
        }.also {
            it.subscribeData(UserPrefs.playlists.openSubscription().map {
                it.filter {
                    when (item) {
                        is Song -> it is MixTape || it is CollaborativePlaylist
                        is PartialAlbum -> it is AlbumCollection || it is CollaborativePlaylist
                        is HasTracks -> it is CollaborativePlaylist
                        else -> TODO("Can't add Artist to playlist")
                    }
                }
            })
        }
    }
}