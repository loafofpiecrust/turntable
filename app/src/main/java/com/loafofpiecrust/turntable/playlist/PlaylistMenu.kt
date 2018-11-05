package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.widget.EditText
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.GeneralPlaylist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import kotlin.random.Random

fun Toolbar.playlistOptions(
    context: Context,
    playlist: GeneralPlaylist
) {
    val scope = ViewScope(this)

    // TODO: can't find local playlist _or_ I'm not the owner
    menuItem(R.string.playlist_rename, showIcon = false).onClick {
        context.alert("Rename playlist '${playlist.id.name}'") {
            lateinit var editor: EditText
            customView {
                editor = editText(playlist.id.name) {
                    lines = 1
                    maxLines = 1
                }
            }
            positiveButton("Rename") {
                val name = editor.text.toString()
                playlist.rename(name)
                this@playlistOptions.title = name
            }
            negativeButton("Cancel") {}
        }.show()
    }

//                    menuItem("Change Color", showIcon = false).onClick {
//                        ColorPickerDialog().apply {
//                            setColorPickerDialogListener(object: ColorPickerDialogListener {
//                                override fun onDialogDismissed(dialogId: Int) {
//
//                                }
//
//                                override fun onColorSelected(dialogId: Int, color: Int) {
//                                    playlist.color = color
//                                    this@appBarLayout.backgroundColor = color
//                                }
//                            })
//                        }.show(activity!!.supportFragmentManager, "colors")
//                    }

    menuItem(R.string.playlist_delete, showIcon = false).onClick {
        context.alert("Delete playlist '${playlist.id.name}'") {
            positiveButton("Delete") {
                GlobalScope.launch {
                    UserPrefs.playlists putsMapped {
                        it.withoutFirst { it.id.uuid == playlist.id.uuid }
                    }
                }
                ctx.popMainContent()
            }
            negativeButton("Cancel") {}
        }.show()
    }

    val isPublic = ConflatedBroadcastChannel(playlist.isPublic)
    menuItem(R.string.playlist_unpublish, showIcon = false) {
        scope.launch {
            isPublic.consumeEach {
                if (it) {
                    title = context.getString(R.string.playlist_unpublish)
                    onClick {
                        playlist.unpublish()
                        context.toast("Playlist unpublished")
                        isPublic.offer(false)
                    }
                } else {
                    title = context.getString(R.string.playlist_publish)
                    onClick {
                        playlist.publish()
                        context.toast("Playlist published")
                        isPublic.offer(true)
                    }
                }
            }
        }
    }

    menuItem(R.string.playlist_generate_similar, showIcon = false).onClick(Dispatchers.Default) {
        val tracks = playlist.tracksChannel.first()
        val tracksToUse = (0..minOf(5, tracks.size)).lazy
            .map { Random.nextInt(tracks.size) }
            .mapNotNull { tracks.getOrNull(it)?.id }
            .toList()
        Spotify.openRecommendationsPlaylist(context, songs = tracksToUse)
    }

    menuItem(R.string.share, showIcon = false).onClick {
        FriendPickerDialog(
            Message.Recommend(playlist.id),
            "Share"
        ).show(context)
    }

    // TODO: Only show if playlist isn't already saved.
    menuItem(R.string.playlist_subscribe, showIcon = false).onClick {
        context.library.addPlaylist(playlist)
    }
}