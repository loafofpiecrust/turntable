package com.loafofpiecrust.turntable.album

import android.content.ClipData
import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.graphics.Typeface.BOLD
import android.graphics.drawable.Drawable
import android.net.Uri
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.collapsingToolbarlparams
import com.loafofpiecrust.turntable.model.album.*
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.msToTimeString
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.AddToPlaylistDialog
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.SongsOnDiscAdapter
import com.loafofpiecrust.turntable.song.SongsUI
import com.loafofpiecrust.turntable.style.detailsStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.ui.universal.show
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.launch
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
        override val album = ConflatedBroadcastChannel(album)
    }

    open val album: BroadcastChannel<Album> by lazy(LazyThreadSafetyMode.NONE) {
        Library.findAlbum(albumId).map {
            it ?: Repositories.findOnline(albumId)!!
        }.replayOne()
    }

    override fun Fragment.onCreate() {
        val trans = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())

        sharedElementEnterTransition = trans
        sharedElementReturnTransition = trans

        enterTransition = Fade()
    }

    override fun ViewContext.render() = coordinatorLayout {
        id = R.id.container
        backgroundColor = colorAttr(android.R.attr.windowBackground)

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

                album.consumeEachAsync {
                    menu.clear()
                    menu.prepareOptions(this@render, context, it)
                }
            }.lparams {
                minimumHeight = dimen(R.dimen.details_toolbar_height)
                width = matchParent
            }

        }.lparams(width = matchParent)


        // Display tracks on this album.
        AlbumTracksUI(album.openSubscription())
            .createView(this)
            .lparams {
                behavior = AppBarLayout.ScrollingViewBehavior()
            }

        // data binding
        launch(start = CoroutineStart.UNDISPATCHED) {
            album.consumeEach { album ->
                if (album.year > 0) {
                    year.text = album.year.toString()
                } else {
                    year.visibility = View.INVISIBLE
                }
            }
        }

        val req = Glide.with(image)
        album.openSubscription()
            .switchMap(Dispatchers.IO) { album ->
                album.loadCover(req)
                    .map { album to it }
            }
            .consumeEachAsync { (album, req) ->
                req?.transition(DrawableTransitionOptions().crossFade(200))
                    ?.addListener(album.loadPalette(appBar, mainLine, subLine, collapser))
                    ?.addListener(object: RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            appBar.backgroundColor = UserPrefs.primaryColor.value
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            return false
                        }
                    })
                    ?.into(image)
                    ?: run {
                        image.imageResource = R.drawable.ic_default_album
                        appBar.backgroundColor = UserPrefs.primaryColor.value
                    }
            }
    }

}

private fun Menu.prepareOptions(scope: CoroutineScope, context: Context, album: Album) {
    menuItem(R.string.album_shuffle, R.drawable.ic_shuffle, showIcon =false).onClick(Dispatchers.Default) {
        if (album.tracks.isNotEmpty()) {
            MusicService.offer(PlayerAction.PlaySongs(album.tracks, mode = MusicPlayer.OrderMode.SHUFFLE))
        }
    }

    menuItem(R.string.recommend).onClick {
        FriendPickerDialog(
            Message.Recommend(album.id),
            context.getString(R.string.recommend)
        ).show(context)
    }

    menuItem("Share Link").onClick {
        val link = FirebaseDynamicLinks.getInstance()
            .createDynamicLink()
            .setDomainUriPrefix("https://turntable.page.link")
            .setLink(Uri.parse("https://loafofpiecrust.com/turntable/album?name=${album.id.displayName}&artist=${album.id.artist.displayName}"))
            .buildDynamicLink()

        val clip = ClipData.newRawUri("Check out ${album.id.displayName}", link.uri)
        context.clipboardManager.primaryClip = clip
        context.toast("Uri copied to clipboard")
    }

    menuItem(R.string.add_to_playlist).onClick {
        AddToPlaylistDialog(album.id).show(context)
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

        menuItem(R.string.add_to_library, R.drawable.ic_turned_in_not) {
            scope.launch {
                Library.findAlbum(album.id).consumeEach { existing ->
                    if (existing != null) {
                        setIcon(R.drawable.ic_turned_in)
                        onClick {
                            // Remove remote album from library
                            Library.removeRemoteAlbum(existing)
                            context.toast(R.string.album_removed_library)
                        }
                    } else {
                        setIcon(R.drawable.ic_turned_in_not)
                        onClick {
                            Library.addRemoteAlbum(album)
                            context.toast(R.string.album_added_library)
                        }
                    }
                }
            }
        }
    } else if (album is LocalAlbum) {
        // Downloaded status
        menuItem(R.string.add_to_library, R.drawable.ic_turned_in).onClick {
            context.toast(R.string.album_already_downloaded)
        }

        menuItem(R.string.album_edit_metadata).onClick {
            AlbumEditorActivityStarter.start(context, album.id)
        }
    }
}


private class AlbumTracksUI(
    album: ReceiveChannel<Album>
): SongsUI() {
    override val songs = album
        .map(Dispatchers.IO) { it.tracks }
        .broadcast(CONFLATED)

    override fun makeAdapter() = SongsOnDiscAdapter(
        coroutineContext,
        songs.openSubscription().map { it.groupBy { it.disc.toString() } },
        R.string.disc_number,
        formatSubtitle = { song ->
            if (song.duration > 0) {
                msToTimeString(song.duration)
            } else ""
        },
        formatTrack = { it.track.toString() }
    ) { song ->
        val songs = songs.openSubscription().first()
        val idx = songs.indexOf(song)
        MusicService.offer(PlayerAction.PlaySongs(songs, idx))
    }
}