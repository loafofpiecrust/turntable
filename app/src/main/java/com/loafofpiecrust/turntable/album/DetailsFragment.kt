package com.loafofpiecrust.turntable.album

import activitystarter.Arg
import android.arch.lifecycle.ViewModel
import android.graphics.Color.*
import android.graphics.Typeface.*
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout.LayoutParams.*
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.song.SongsFragmentStarter
import com.loafofpiecrust.turntable.style.detailsStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast


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

// Album or Artist or Playlist details (?)
// Maybe split this up. Start with Album.
class DetailsFragment: BaseFragment() {
    @Arg lateinit var albumId: AlbumId
    @Arg(optional=true) var isPartial = false

    lateinit var album: ReceiveChannel<Album>

    companion object {
        fun fromAlbum(album: Album, isPartial: Boolean = false): DetailsFragment {
            return DetailsFragmentStarter.newInstance(album.id, isPartial).also {
                it.album = produceSingle(album)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val transDur = 200L

        val trans = TransitionSet().setDuration(transDur)
            .addTransition(ChangeBounds().setDuration(transDur))
//                    .addTransition(ChangeImageTransform().setDuration(1500))
            .addTransition(ChangeTransform().setDuration(transDur))
            .addTransition(ChangeClipBounds().setDuration(transDur))

        sharedElementEnterTransition = trans
        sharedElementReturnTransition = trans

        enterTransition = Fade().setDuration(transDur)
        exitTransition = Fade().setDuration(1) // Just dissappear
//        exitTransition = Fade().setDuration(transDur / 3)
//        postponeEnterTransition()
//        vm = MusicModelProviders.of(this, album.id.toString(), true).get(AlbumsViewModel::class.java)
//        System.out.println("vm: ${vm.category.valueOrNull}")
    }

    override fun makeView(ui: ViewManager): View = ui.coordinatorLayout {
//        fitsSystemWindows = true

//        val album = if (album.local !is Album.LocalDetails.Downloaded) {
//            val existing = Library.instance.findAlbum(album)
//            existing.blockingFirst().toNullable() ?: album
//        } else album

        val album = ctx.library.findCachedAlbum(albumId).replayOne()


        val coloredText = mutableListOf<TextView>()

        appBarLayout {
            fitsSystemWindows = false
            backgroundColor = TRANSPARENT

            lateinit var image: ImageView
            collapsingToolbarLayout {
                fitsSystemWindows = false
                setContentScrimColor(UserPrefs.primaryColor.value)
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
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        transitionName = albumId.transitionFor("art")
                    }

                    // Downloaded status
                    val status = textView {
                        //                        text = "Not Downloaded"
                        text = getString(R.string.album_remote)
                        textSizeDimen = R.dimen.small_text_size
//                        backgroundColor = Color.BLACK
                        backgroundResource = R.drawable.rounded_rect

                        album.consumeEach(UI) {
                            text = given(it) {
                                when {
                                    it is LocalAlbum -> if (it.hasTrackGaps) {
                                        getString(R.string.album_partial)
                                    } else getString(R.string.album_local)
                                    else -> getString(R.string.album_remote)
                                }
                            } ?: getString(R.string.album_remote)
                        }
                    }.also { coloredText += it }

                    // Year
                    val year = textView {
                        album.consumeEach(UI) { album ->
                            if (album?.year != null) {
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
                                TOP to TOP of this@constraintLayout,
                                START to START of this@constraintLayout,
                                BOTTOM to BOTTOM of this@constraintLayout,
                                END to END of this@constraintLayout
                            )
                            width = matchConstraint
                            height = matchConstraint
                            dimensionRation = "H,1:1"
                        }
                        status {
                            connect(
                                BOTTOM to BOTTOM of this@constraintLayout margin padBy,
                                END to END of this@constraintLayout margin padBy
                            )
                        }
                        year {
                            connect(
                                BOTTOM to BOTTOM of this@constraintLayout margin padBy,
                                START to START of this@constraintLayout margin padBy
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
                width = matchParent
                height = matchParent
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
//                topPadding = -dimen(R.dimen.statusbar_height)
            }


            toolbar {
                detailsStyle(UI)
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

                    album.consumeEach(UI) { album ->
                        album?.loadCover(Glide.with(image))?.consumeEach(UI) {
                            it?.transition(DrawableTransitionOptions().crossFade(200))
                                ?.listener(album.loadPalette(this@toolbar, mainLine, subLine))
                                ?.into(image)
                                ?: run { image.imageResource = R.drawable.ic_default_album }
                        }
                    }
                }.lparams(height = matchParent)


                if (album is RemoteAlbum) { // is remote album
                    // Option to mark the album for offline listening
                    // First, see if it's already marked
                    menuItem("Favorite", R.drawable.ic_turned_in_not, showIcon=true) {
                        ctx.library.findAlbum(album.id).consumeEach(UI) { existing ->
                            if (existing != null) {
                                icon = ctx.getDrawable(R.drawable.ic_turned_in)
                                setOnMenuItemClickListener {
                                    // Remove remote album from library
                                    ctx.library.removeRemoteAlbum(existing)
                                    toast("Removed album to library")
                                    true
                                }
                            } else {
                                icon = ctx.getDrawable(R.drawable.ic_turned_in_not)
                                setOnMenuItemClickListener {
                                    ctx.library.addRemoteAlbum(album)
                                    toast("Added album to library")
                                    true
                                }
                            }
                        }
                    }
                }

//                album.optionsMenu(menu)

            }.lparams {
                minimumHeight = dimen(R.dimen.details_toolbar_height)
//                height = dimen(R.dimen.details_toolbar_height)
                width = matchParent
            }


        }.lparams(width = matchParent, height = wrapContent)



        verticalLayout {
            id = R.id.container
            val fragment = SongsFragmentStarter.newInstance(SongsFragment.Category.OnAlbum(albumId))
            fragment(
                childFragmentManager, fragment
            )
        }.lparams(width = matchParent, height = wrapContent) {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
    }
}