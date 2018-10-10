package com.loafofpiecrust.turntable.album

import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.graphics.Typeface.BOLD
import android.os.Parcelable
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.AppBarLayout
import android.support.design.widget.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
import android.support.design.widget.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
import android.support.v4.app.Fragment
import android.transition.*
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.model.album.*
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.PlaylistPicker
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.songsList
import com.loafofpiecrust.turntable.style.detailsStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.AlbumEditorActivityStarter
import com.loafofpiecrust.turntable.ui.UIComponent
import com.loafofpiecrust.turntable.ui.showDialog
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel.Factory.CONFLATED
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout

@Parcelize
open class AlbumDetailsUI(
    val albumId: AlbumId
): UIComponent(), Parcelable {
    class Resolved(album: Album): AlbumDetailsUI(album.id) {
        override val album = produceSingle { album }.replayOne()
    }

    open val album by lazy {
        async(Dispatchers.IO) {
            Repository.find(albumId)!!
        }.broadcast()
    }

    override fun Fragment.onCreate() {
        val transDur = 400L

        val trans = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())

        sharedElementEnterTransition = trans
        sharedElementReturnTransition = trans

        enterTransition = Fade().setDuration(transDur)
    }


    override fun AnkoContext<Any>.render() = coordinatorLayout {
        id = R.id.container
        backgroundColor = TRANSPARENT

        val coloredText = mutableListOf<TextView>()
        lateinit var status: TextView
        lateinit var year: TextView
        lateinit var mainLine: TextView
        lateinit var subLine: TextView
        lateinit var image: ImageView
        lateinit var collapser: CollapsingToolbarLayout

        val appBar = appBarLayout {
            fitsSystemWindows = false
            backgroundColor = TRANSPARENT

            collapser = collapsingToolbarLayout {
                id = R.id.collapser
                fitsSystemWindows = false
                collapsedTitleGravity = Gravity.BOTTOM
                expandedTitleGravity = Gravity.BOTTOM
                title = " "
                clipToPadding = false
                clipToOutline = false

                constraintLayout {
                    clipToPadding = false
                    clipToOutline = false

                    image = imageView {
                        fitsSystemWindows = false
                        adjustViewBounds = false
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        transitionName = albumId.imageTransition
                    }

                    // Downloaded status
                    status = textView {
                        //                        text = "Not Downloaded"
                        text = context.getString(R.string.album_remote)
                        textSizeDimen = R.dimen.small_text_size
//                        backgroundColor = Color.BLACK
                        backgroundResource = R.drawable.rounded_rect
                    }.also { coloredText += it }

                    // Year
                    year = textView {
                        textSizeDimen = R.dimen.small_text_size
                        backgroundResource = R.drawable.rounded_rect
                    }.also { coloredText += it }

                    generateChildrenIds()
                    applyConstraintSet {
                        val padBy = dimen(R.dimen.details_image_padding)
                        image {
                            connect(
                                TOP to TOP of PARENT_ID,
                                START to START of PARENT_ID,
                                BOTTOM to BOTTOM of PARENT_ID,
                                END to END of PARENT_ID
                            )
                            size = matchConstraint
                            dimensionRation = "H,1:1"
                        }
                        status {
                            connect(
                                BOTTOM to BOTTOM of PARENT_ID margin padBy,
                                END to END of PARENT_ID margin padBy
                            )
                        }
                        year {
                            connect(
                                BOTTOM to BOTTOM of PARENT_ID margin padBy,
                                START to START of PARENT_ID margin padBy
                            )
                        }
                    }
                }.collapsingToolbarlparams {
                    collapseMode = COLLAPSE_MODE_PIN
                    width = matchParent
                }

                // Status bar buffer for the lower toolbar
                toolbar().collapsingToolbarlparams {
                    height = dimen(R.dimen.statusbar_height)
                    collapseMode = COLLAPSE_MODE_PIN
                }
            }.lparams {
                size = matchParent
                scrollFlags = SCROLL_FLAG_SCROLL or SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }


            toolbar {
                detailsStyle()
                transitionName = albumId.nameTransition

                verticalLayout {
                    gravity = Gravity.CENTER_VERTICAL

                    mainLine = textView(albumId.displayName) {
                        maxLines = 2
                        textStyle = BOLD
                        textSizeDimen = R.dimen.subtitle_text_size
                    }
                    subLine = textView(albumId.artist.displayName + albumId.artist.featureList) {
                        maxLines = 1
                    }
                }.lparams(height = matchParent)

                launch {
                    album.consumeEach {
                        menu.clear()
//                        it.optionsMenu(context, menu)
                        menu.prepareOptions(context, it)
                    }
                }

            }.lparams {
                minimumHeight = dimen(R.dimen.details_toolbar_height)
                width = matchParent
            }

        }.lparams(width = matchParent)


        // Display tracks on this album.
        songsList(
            SongsFragment.Category.OnAlbum(albumId),
            album.openSubscription()
                .map(Dispatchers.IO) { it.tracks }
                .broadcast(CONFLATED)
        ) {
            id = R.id.songs
        }.lparams(width = matchParent) {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }



        // data binding
        launch {
            album.openSubscription()
                .map(Dispatchers.Default) {
                    when (it) {
                        is LocalAlbum -> if (it.hasTrackGaps) {
                            context.getString(R.string.album_partial)
                        } else context.getString(R.string.album_local)
                        else -> context.getString(R.string.album_remote)
                    }
                }
                .consumeEach { status.text = it }
        }
        launch {
            album.consumeEach { album ->
                if (album.year != null) {
                    year.text = album.year.toString()
                } else {
                    visibility = View.GONE
                }
            }
        }
        launch {
            album.openSubscription()
                .switchMap(Dispatchers.IO) { album ->
                    album.loadCover(Glide.with(image))
                        .map { album to it }
                }
                .consumeEach { (album, req) ->
                    req?.transition(DrawableTransitionOptions().crossFade(200))
                        ?.listener(album.loadPalette(appBar, mainLine, subLine, collapser))
                        ?.into(image)
                        ?: run { image.imageResource = R.drawable.ic_default_album }
                }
        }
    }

    private fun Menu.prepareOptions(context: Context, album: Album) {
        menuItem(R.string.album_shuffle, R.drawable.ic_shuffle, showIcon =false).onClick(Dispatchers.Default) {
            if (album.tracks.isNotEmpty()) {
                MusicService.enact(PlayerAction.PlaySongs(album.tracks, mode = MusicPlayer.OrderMode.SHUFFLE))
            }
        }

        menuItem(R.string.recommend).onClick {
            FriendPickerDialog(
                Message.Recommendation(album.toPartial()),
                context.getString(R.string.recommend)
            ).show(context)
        }

        menuItem(R.string.add_to_playlist).onClick {
            PlaylistPicker(album.toPartial()).showDialog(context)
        }

        if (album is RemoteAlbum) {
            menuItem(R.string.download, R.drawable.ic_cloud_download, showIcon = false).onClick(Dispatchers.Default) {
                if (App.instance.hasInternet) {
//                tracks.filter {
//                    LocalApi.find(it.uuid) == null
//                }.forEach { it.download() }
                } else {
                    context.toast(R.string.no_internet)
                }
            }

            menuItem(R.string.add_to_library, R.drawable.ic_turned_in_not, showIcon = true) {
                context.library.findAlbum(album.id).consumeEach(Dispatchers.Main) { existing ->
                    if (existing != null) {
                        setIcon(R.drawable.ic_turned_in)
                        onClick {
                            // Remove remote album from library
                            context.library.removeRemoteAlbum(existing)
                            context.toast(R.string.album_removed_library)
                        }
                    } else {
                        setIcon(R.drawable.ic_turned_in_not)
                        onClick {
                            context.library.addRemoteAlbum(album)
                            context.toast(R.string.album_added_library)
                        }
                    }
                }
            }
        } else if (album is LocalAlbum) {
            menuItem(R.string.album_edit_metadata).onClick {
                AlbumEditorActivityStarter.start(context, album.id)
            }
        }
    }
}