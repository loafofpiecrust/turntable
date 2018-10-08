package com.loafofpiecrust.turntable.album

import android.arch.lifecycle.ViewModel
import android.graphics.Color.TRANSPARENT
import android.graphics.Typeface.BOLD
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
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
import com.loafofpiecrust.turntable.song.songsList
import com.loafofpiecrust.turntable.style.detailsStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
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


class AlbumsViewModel: ViewModel() {
    val category = ConflatedBroadcastChannel<AlbumsFragment.Category>()
//    val albums = category.openSubscription().switchMap { cat ->
//        when (cat) {
//            is AlbumsFragment.Category.All -> Library.instance.albums.openSubscription()
//            is AlbumsFragment.Category.ByArtist -> {
////                withContext(UI) {
////                    circleProgress.visibility = View.VISIBLE
////                    loadCircle.start()
////                }
//                when (cat.mode) {
//                    ArtistDetailsFragment.Mode.REMOTE -> produce(BG_POOL) {
//                        send(cat.artist.resolveAlbums(false))
//                    }
//                    ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE -> produce(BG_POOL) {
//                        send(cat.artist.resolveAlbums(true))
//                    }
//                    else -> Library.instance.albumsByArtist(cat.artist)
//                }
//            }
//            is AlbumsFragment.Category.Custom -> produceTask { cat.albums }
//        }
//    }.replayOne()
}

// Album or Artist or Playlist remoteInfo (?)
// Maybe split this up. Start with Album.
class DetailsFragment(): BaseFragment() {
    constructor(albumId: AlbumId, isPartial: Boolean = false): this() {
        this.albumId = albumId
        this.isPartial = isPartial
    }

    private var albumId: AlbumId by arg()
    private var isPartial: Boolean by arg()

    lateinit var album: BroadcastChannel<Album>

    override fun onCreate() {
        super.onCreate()

        if (!::album.isInitialized) {
            album = produceSingle(Dispatchers.IO) {
                Repository.find(albumId)!!
            }.broadcast(Channel.CONFLATED)
        }

        val transDur = 400L

        val trans = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())

        sharedElementEnterTransition = trans
        sharedElementReturnTransition = trans

        enterTransition = Fade().setDuration(transDur)
//        exitTransition = Fade().setDuration(transDur) // Just dissappear
//        postponeEnterTransition()
    }

    override fun ViewManager.createView() = coordinatorLayout {
        id = R.id.container
        backgroundColor = TRANSPARENT

        val coloredText = mutableListOf<TextView>()

        appBarLayout {
            fitsSystemWindows = false
            backgroundColor = TRANSPARENT

            lateinit var image: ImageView
            val collapser = collapsingToolbarLayout {
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
                    val status = textView {
                        //                        text = "Not Downloaded"
                        text = getString(R.string.album_remote)
                        textSizeDimen = R.dimen.small_text_size
//                        backgroundColor = Color.BLACK
                        backgroundResource = R.drawable.rounded_rect

                        album.consumeEachAsync {
                            text = when (it) {
                                is LocalAlbum -> if (it.hasTrackGaps) {
                                    getString(R.string.album_partial)
                                } else getString(R.string.album_local)
                                else -> getString(R.string.album_remote)
                            }
                        }
                    }.also { coloredText += it }

                    // Year
                    val year = textView {
                        album.consumeEachAsync { album ->
                            if (album.year != null) {
                                text = album.year.toString()
                            } else {
                                visibility = View.GONE
                            }
                        }
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
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }


            toolbar {
                detailsStyle()
                transitionName = albumId.nameTransition

                verticalLayout {
                    gravity = Gravity.CENTER_VERTICAL

                    val mainLine = textView(albumId.displayName) {
                        maxLines = 2
                        textStyle = BOLD
                        textSizeDimen = R.dimen.subtitle_text_size
                    }
                    val subLine = textView(albumId.artist.displayName + albumId.artist.featureList) {
                        maxLines = 1
                    }

                    album.openSubscription()
                        .switchMap(Dispatchers.IO) { album ->
                            album.loadCover(Glide.with(image))
                                .map { album to it }
                        }
                        .consumeEachAsync { (album, req) ->
                            req?.transition(DrawableTransitionOptions().crossFade(200))
                                ?.listener(album.loadPalette(this@toolbar, mainLine, subLine, collapser))
                                ?.into(image)
                                ?: run { image.imageResource = R.drawable.ic_default_album }
                        }
                }.lparams(height = matchParent)

                album.consumeEachAsync {
                    menu.clear()
                    it.optionsMenu(context, menu)
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
    }


    companion object {
        fun fromAlbum(album: Album, isPartial: Boolean = false) = DetailsFragment().apply {
            this.albumId = album.id
            this.isPartial = isPartial
            this.album = ConflatedBroadcastChannel(album)
        }
    }
}
