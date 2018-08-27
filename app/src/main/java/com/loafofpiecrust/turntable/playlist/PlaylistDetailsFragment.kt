package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.support.transition.Slide
import android.view.View
import android.view.ViewManager
import android.widget.EditText
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.song.SongsFragmentStarter
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialogStarter
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.replayOne
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast
import java.util.*


class PlaylistDetailsFragment: BaseFragment() {
    @Arg lateinit var playlistId: UUID
    @Arg lateinit var playlistTitle: String

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
        enterTransition = Slide()
        exitTransition = Slide()
    }

    override fun ViewManager.createView(): View = verticalLayout {
//        fitsSystemWindows = true

        playlist = runBlocking {
            ctx.library.findPlaylist(playlistId).first()
                ?: ctx.library.findCachedPlaylist(playlistId).first()
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
                    menuItem("Rename", showIcon = false).setOnMenuItemClickListener {
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

                    menuItem("Delete", showIcon = false).onClick {
                        alert("Delete playlist '${playlist.name}'") {
                            positiveButton("Delete") {
                                task {
                                    UserPrefs.playlists putsMapped {
                                        it.withoutFirst { it.id == playlistId }
                                    }
                                }
                                ctx.popMainContent()
                            }
                            negativeButton("Cancel") {}
                        }.show()
                    }

                    menuItem("Unpublish", showIcon = false).onClick {
                        if (playlist.isPublished) {
                            playlist.unpublish()
                            toast("Playlist has been unpublished")
                        } else {
                            toast("Playlist isn't published")
                        }
                    }
                    menuItem("Get recommendation", showIcon = false).onClick(BG_POOL) {
                        val r = Random()
                        val tracks = playlist.tracks.first()
                        val tracksToUse = (0..minOf(5, tracks.size)).map { r.nextInt(tracks.size) }
                            .mapNotNull { tracks.getOrNull(it)?.id }
                        Spotify.openRecommendationsPlaylist(ctx, songs = tracksToUse)
                    }

                    menuItem("Share", showIcon = false).onClick {
                        FriendPickerDialogStarter.newInstance(
                            SyncService.Message.Playlist(playlistId),
                            "Share"
                        ).show(ctx)
                    }

                    // TODO: Only show if playlist isn't already saved.
                    menuItem("Subscribe", showIcon = false).onClick {
                        ctx.library.addPlaylist(playlist)
                    }

                    menuItem("Make Completable", showIcon = false).onClick {
                        playlist.isCompletable = true
                    }

                }.lparams(width = matchParent, height = dimen(R.dimen.toolbar_height)) {
                    //                    scrollFlags = SCROLL_FLAG_SCROLL and SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                }
            }.lparams(width= matchParent)

            frameLayout {
                fragment(
                    SongsFragmentStarter.newInstance(
                        SongsFragment.Category.Playlist(playlistId)
                    ).apply {
                        songs = playlist.tracks.replayOne()
                    }
                )
            }.lparams(width = matchParent, height = matchParent) {
//                behavior = AppBarLayout.ScrollingViewBehavior()
            }

//            songList(
//                SongsFragment.Category.Playlist(playlistId),
//                subscriptions
//            ).apply {
//            }
        }

}