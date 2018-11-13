package com.loafofpiecrust.turntable.playlist

import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.GeneralPlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsOnDiscAdapter
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.universal.ParcelableComponent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView

@Parcelize
class GeneralPlaylistDetails(
    private val playlistId: PlaylistId
): UIComponent(), ParcelableComponent {
    private val playlist = runBlocking {
        Library.findPlaylist(playlistId.uuid)
            .first()
    }

    private val playlistUI: UIComponent = when (playlist) {
        is MixTape -> MixtapeDetailsUI(playlistId)
        is CollaborativePlaylist -> PlaylistDetailsUI(playlistId)
        is GeneralPlaylist -> NewPlaylistDetails(playlist)
        else -> AlbumCollectionDetails(playlistId)
    }

    override fun ViewContext.render(): View =
        renderChild(playlistUI)
}

class NewPlaylistDetails(
    val playlist: GeneralPlaylist
): UIComponent() {
    override fun ViewContext.render() = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            toolbar {
                standardStyle()
                title = playlist.id.displayName
                backgroundColor = playlist.color
                    ?: UserPrefs.primaryColor.value

                playlistOptions(context, playlist)
            }
        }

        refreshableRecyclerView {
            channel = playlist.tracksChannel
            contents {
                recyclerView {
                    layoutManager = LinearLayoutManager(context)
                    adapter = SongsOnDiscAdapter(
                        coroutineContext,
                        playlist.sides.openSubscription().map {
                            var index = -1
                            it.map { it.map { it.song } }.associateBy {
                                index += 1
                                playlist.sideName(index)
                            }
                        },
                        R.string.mixtape_side,
                        formatSubtitle = { it.id.artist.displayName }
                    ) { song ->
                        val songs = playlist.tracks
                        val idx = songs.indexOf(song)
                        MusicService.offer(
                            PlayerAction.PlaySongs(songs, idx)
                        )
                    }
                }
            }
            emptyState {
                verticalLayout {
                    gravity = Gravity.CENTER
                    padding = dimen(R.dimen.empty_state_padding)
                    textView(R.string.playlist_empty) {
                        textSizeDimen = R.dimen.title_text_size
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        bottomPadding = dip(8)
                    }
                    textView(R.string.playlist_empty_details) {
                        textSizeDimen = R.dimen.subtitle_text_size
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    }
                }
            }
        }
    }
}