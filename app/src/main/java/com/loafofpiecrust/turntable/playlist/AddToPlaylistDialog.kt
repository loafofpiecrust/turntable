package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.playlist.SongPlaylist
import com.loafofpiecrust.turntable.model.playlist.add
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.DialogComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.show
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView

@Parcelize
class AddToPlaylistDialog(
    private val item: Recommendable
) : DialogComponent(), Parcelable {
    override fun ViewContext.render() = recyclerView {
        topPadding = dimen(R.dimen.dialog_top_padding)

        val applicablePlaylists = UserPrefs.playlists.openSubscription().map { playlists ->
            playlists.filter { p ->
                when (item) {
                    is Song -> p is SongPlaylist && p.canModify(Sync.selfUser)
                    is AlbumId -> p is AlbumCollection || p is SongPlaylist
                    is HasTracks -> p is SongPlaylist
                    else -> false
                }
            }
        }

        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter = PlaylistsFragment.Adapter(
            coroutineContext,
            applicablePlaylists,
            readOnly = true
        ) { p ->
            addToPlaylist(ctx, p)
            dismiss()
        }
    }

    override fun AlertBuilder<*>.prepare() {
        titleResource = R.string.add_to_playlist

        positiveButton(R.string.playlist_new) {
            dismiss()
            NewPlaylistDialog.withItems(listOf(item))
                .show(ctx, fullscreen = true)
        }

        cancelButton {}
    }

    private fun addToPlaylist(ctx: Context, selected: Playlist) {
        when (val item = item) {
            is Song -> when (selected) {
                is SongPlaylist -> selected.add(ctx, item)
                else -> ctx.toast("Cannot add a song to ${selected.id.name}")
            }
            is AlbumId -> when (selected) {
                is AlbumCollection -> selected.add(ctx, item)
                else -> ctx.toast("Cannot add an album to ${selected.id.name}")
            }
            else -> ctx.toast("Unrecognized music type")
        }
    }
}