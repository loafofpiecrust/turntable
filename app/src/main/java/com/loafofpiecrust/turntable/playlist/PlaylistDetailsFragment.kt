package com.loafofpiecrust.turntable.playlist

import android.support.transition.Slide
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewManager
import android.widget.EditText
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast
import java.util.*


class PlaylistDetailsFragment: BaseFragment() {
    companion object {
        fun newInstance(id: UUID, title: String) = PlaylistDetailsFragment().apply {
            this.playlistId = id
            this.playlistTitle = title
        }
    }

    private var playlistId: UUID by arg()
    private var playlistTitle: String by arg()

    lateinit var playlist: CollaborativePlaylist

    override fun onDestroy() {
        super.onDestroy()
        playlist.desync()
    }

    override fun onPause() {
        super.onPause()
        playlist.desync()
    }

    override fun onResume() {
        super.onResume()
        if (playlist.isPublished) {
            playlist.sync()
        }
    }

    override fun onCreate() {
        Slide().let {
            enterTransition = it
            exitTransition = it
        }
    }

    override fun ViewManager.createView(): View = linearLayout {
        backgroundColorResource = R.color.background
        orientation = LinearLayout.VERTICAL
//        fitsSystemWindows = true

        playlist = runBlocking {
            context.library.findPlaylist(playlistId).first()
                ?: context.library.findCachedPlaylist(playlistId).first()
        } as CollaborativePlaylist

        if (playlist.isPublished) {
            playlist.sync()
        }

            appBarLayout {
                //                fitsSystemWindows = true
                given(playlist.color) { backgroundColor = it }
                topPadding = dimen(R.dimen.statusbar_height)

                toolbar {
                    standardStyle()
//                    fitsSystemWindows = true
                    title = playlistTitle
                    transitionName = playlistId.toString()

                    // TODO: can't find local playlist _or_ I'm not the owner
                    menuItem(R.string.playlist_rename, showIcon = false).setOnMenuItemClickListener {
                        alert("Rename playlist '${playlist.name}'") {
                            lateinit var editor: EditText
                            customView {
                                editor = editText(playlist.name) {
                                    lines = 1
                                    maxLines = 1
                                }
                            }
                            positiveButton("Rename") {
                                val name = editor.text.toString()
                                playlist.rename(name)
                                this@toolbar.title = name
                            }
                            negativeButton("Cancel") {}
                        }.show()
                        true
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
                        alert("Delete playlist '${playlist.name}'") {
                            positiveButton("Delete") {
                                GlobalScope.launch {
                                    UserPrefs.playlists putsMapped {
                                        it.withoutFirst { it.uuid == playlistId }
                                    }
                                }
                                ctx.popMainContent()
                            }
                            negativeButton("Cancel") {}
                        }.show()
                    }

                    menuItem(R.string.playlist_unpublish, showIcon = false).onClick {
                        if (playlist.isPublished) {
                            playlist.unpublish()
                            toast("Playlist has been unpublished")
                        } else {
                            toast("Playlist isn't published")
                        }
                    }
                    menuItem(R.string.playlist_generate_similar, showIcon = false).onClick(Dispatchers.Default) {
                        val r = Random()
                        val tracks = playlist.tracksChannel.first()
                        val tracksToUse = (0..minOf(5, tracks.size)).lazy
                            .map { r.nextInt(tracks.size) }
                            .mapNotNull { tracks.getOrNull(it)?.id }
                            .toList()
                        Spotify.openRecommendationsPlaylist(context, songs = tracksToUse)
                    }

                    menuItem(R.string.share, showIcon = false).onClick {
                        FriendPickerDialog(
                            Message.Playlist(playlistId),
                            "Share"
                        ).show(ctx)
                    }

                    // TODO: Only show if playlist isn't already saved.
                    menuItem(R.string.playlist_subscribe, showIcon = false).onClick {
                        ctx.library.addPlaylist(playlist)
                    }

//                    menuItem("Make Completable", showIcon = false).onClick {
//                        playlist.isCompletable = true
//                    }

                }.lparams(width = matchParent, height = dimen(R.dimen.toolbar_height)) {
                    //                    scrollFlags = SCROLL_FLAG_SCROLL and SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                }
            }.lparams(width= matchParent)

//            frameLayout {
//                uuid = R.uuid.songs
//                songsList(
//                    SongsFragment.Category.Playlist(playlistId),
//                    playlist.tracks.broadcast(CONFLATED)
//                )
//            }.lparams(width = matchParent, height = matchParent) {
////                behavior = AppBarLayout.ScrollingViewBehavior()
//            }

            recyclerView {
                layoutManager = LinearLayoutManager(context)
                adapter = PlaylistTracksAdapter(playlist)
            }
        }

}