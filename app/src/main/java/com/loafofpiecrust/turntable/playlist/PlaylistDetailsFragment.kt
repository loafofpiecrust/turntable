package com.loafofpiecrust.turntable.playlist

import android.os.Parcelable
import android.support.transition.Slide
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.first
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView
import kotlin.random.Random


@Parcelize
class PlaylistDetailsUI(
    val playlistId: PlaylistId
): UIComponent(), Parcelable {
    val playlist = runBlocking {
        Library.instance.findPlaylist(playlistId.uuid)
            .first() as CollaborativePlaylist
    }

    override fun Fragment.onCreate() {
        Slide().let {
            enterTransition = it
            exitTransition = it
        }
    }

    override fun onPause() {
    }

    override fun onResume() {
        if (playlist.isPublished) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (playlist.isPublished) {
            playlist.desync()
        }
    }

    override fun ViewContext.render() = verticalLayout {
        playlist.sync()
        backgroundColor = colorAttr(android.R.attr.windowBackground)

        if (playlist.isPublished) {
            playlist.sync()
        }

        appBarLayout {
            //                fitsSystemWindows = true
            playlist.color?.let { backgroundColor = it }
            topPadding = dimen(R.dimen.statusbar_height)

            toolbar {
                standardStyle()
//                    fitsSystemWindows = true
                title = playlistId.displayName
                transitionName = playlistId.toString()

                // TODO: can't find local playlist _or_ I'm not the owner
                menuItem(R.string.playlist_rename, showIcon = false).setOnMenuItemClickListener {
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
                    context.alert("Delete playlist '${playlist.id.name}'") {
                        positiveButton("Delete") {
                            GlobalScope.launch {
                                UserPrefs.playlists putsMapped {
                                    it.withoutFirst { it.id.uuid == playlistId.uuid }
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
                        context.toast("Playlist has been unpublished")
                    } else {
                        context.toast("Playlist isn't published")
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
                        Message.Recommend(playlistId),
                        "Share"
                    ).show(context)
                }

                // TODO: Only show if playlist isn't already saved.
                menuItem(R.string.playlist_subscribe, showIcon = false).onClick {
                    context.library.addPlaylist(playlist)
                }

//                    menuItem("Make Completable", showIcon = false).onClick {
//                        playlist.isCompletable = true
//                    }

            }.lparams(width = matchParent) {
                //                    scrollFlags = SCROLL_FLAG_SCROLL and SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }
        }.lparams(width = matchParent)


        refreshableRecyclerView {
            channel = playlist.tracksChannel
            contentView {
                recyclerView {
                    layoutManager = LinearLayoutManager(context)
                    adapter = PlaylistTracksAdapter(coroutineContext, playlist)
                }
            }
            emptyView {
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