package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import android.support.annotation.StringRes
import android.support.constraint.ConstraintSet
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsUI
import com.loafofpiecrust.turntable.collapsingToolbarlparams
import com.loafofpiecrust.turntable.model.artist.*
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.selector
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.sdk27.coroutines.onClick


/**
 * We want to be able to open Artist remoteInfo with either:
 * 1. full artist
 * 2. artist id (from Song/Album)
 * 3. artist id (from saved state)
 */
@Parcelize
open class ArtistDetailsUI(
    private val artistId: ArtistId,
    private val initialMode: Mode = Mode.LIBRARY_AND_REMOTE
) : UIComponent(), Parcelable {
    /**
     * TODO: Save this state to the Parcel
     */
    private val currentMode = ConflatedBroadcastChannel(initialMode)

    protected open val artist by lazy(LazyThreadSafetyMode.NONE) {
        Library.findArtist(artistId).map {
            it ?: Repositories.findOnline(artistId)!!
        }.replayOne()
    }

    private val albums by lazy {
        currentMode.openSubscription()
            .switchMap { mode ->
                when (mode) {
                    // TODO: Do some caching of remotes here. Do this inside Repository :)
                    Mode.LIBRARY -> Library.findArtist(artistId)
                    Mode.REMOTE -> produceSingle { Repositories.findOnline(artistId) }
                    Mode.LIBRARY_AND_REMOTE -> Library.findArtist(artistId).map { local ->
                        val remote = Repositories.findOnline(artistId)
                        if (local != null && remote != null) {
                            MergedArtist(local, remote)
                        } else local ?: remote
                    }
                }
            }
            .map(Dispatchers.IO) { it?.albums ?: emptyList() }
            .replayOne()
    }

    private val albumsUI by lazy {
        AlbumsUI.Custom(
            albums,
            sortBy = AlbumsUI.SortBy.YEAR,
            splitByType = true,
            columnCount = 2
        )
    }

    override fun Fragment.onCreate() {
        sharedElementEnterTransition = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())
    }

    override fun ViewContext.render() = coordinatorLayout {
        backgroundColor = colorAttr(android.R.attr.windowBackground)
        id = R.id.container

        val coloredViews = mutableListOf<View>()
        appBarLayout {
            id = R.id.app_bar
            topPadding = dimen(R.dimen.statusbar_height)

            lateinit var image: ImageView
            val collapser = collapsingToolbarLayout {
                id = R.id.collapser
                collapsedTitleGravity = Gravity.TOP
                expandedTitleGravity = Gravity.TOP

                constraintLayout {
                    image = imageView {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        transitionName = artistId.imageTransition
                    }

                    // Active year range of the artist
                    val year = textView {
                        coloredViews += this
//                        backgroundResource = R.drawable.rounded_rect
//                        textSizeDimen = R.dimen.small_text_size
                        artist.consumeEachAsync { artist ->
                            val start = artist.startYear
                            val end = artist.endYear
                            if (start != null && start != end) {
                                visibility = View.VISIBLE
                                text = context.getString(
                                    R.string.artist_date_range,
                                    start,
                                    end ?: context.getString(R.string.artist_active_now)
                                )
                            } else {
                                visibility = View.GONE
                            }
                        }
                    }

                    // Current display mode
                    val mode = button {
                        currentMode.consumeEachAsync { mode ->
                            text = context.getString(
                                R.string.artist_content_source,
                                context.getString(mode.resource)
                            )
                        }

                        onClick {
                            currentMode puts context.selector(
                                context.getString(R.string.artist_pick_source),
                                Mode.values().toList(),
                                format = { context.getString(it.resource) }
                            )
                        }
                    }

//                    val mode = textView {
//                        backgroundResource = R.drawable.rounded_rect
////                        textSizeDimen = R.dimen.small_text_size
//
//                        currentMode.consumeEachAsync { mode ->
//                            text = context.getString(
//                                R.string.artist_content_source,
//                                context.getString(mode.resource)
//                            )
//                        }
//
//                        onClick {
//                            currentMode puts context.selector(
//                                context.getString(R.string.artist_pick_source),
//                                Mode.values().toList(),
//                                format = { context.getString(it.resource) }
//                            )
//                        }
//                    }

                    generateChildrenIds()
                    applyConstraintSet {
                        val padBy = dimen(R.dimen.details_image_padding)
                        image {
                            val inset = dip(16)
                            connect(
                                TOP to TOP of PARENT_ID margin inset,
                                BOTTOM to BOTTOM of PARENT_ID margin inset,
                                START to START of PARENT_ID margin inset
                            )
                            size = dip(128)
                        }
                        year {
                            connect(
                                TOP to TOP of image margin padBy,
                                START to END of image margin padBy
                            )
                            verticalChainStyle = ConstraintSet.CHAIN_PACKED
                        }
                        mode {
                            connect(
                                START to END of image margin padBy,
                                TOP to BOTTOM of year margin padBy,
                                BOTTOM to BOTTOM of image margin padBy
                            )
                        }
                    }
                }.collapsingToolbarlparams(width = matchParent) {
                    collapseMode = COLLAPSE_MODE_PIN
                }
            }.lparams {
                size = matchParent
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }

            val toolbar = toolbar {
                standardStyle()
                title = artistId.displayName
                transitionName = artistId.nameTransition
                artist.consumeEachAsync {
                    menu.clear()
                    menu.artistOptions(context, it)
//                    it.optionsMenu(context, menu)
                }
            }.lparams(width = matchParent) {
                gravity = Gravity.TOP
            }


            artist.openSubscription().switchMap { artist ->
                artist.loadArtwork(Glide.with(image)).map {
                    it?.addListener(artist.loadPalette(
                        toolbar, collapser, this@appBarLayout,
                        *coloredViews.toTypedArray()
                    ))
                }
            }.consumeEachAsync {
                if (it != null) {
                    it.into(image)
                } else {
                    image.imageResource = R.drawable.ic_default_album
                }
            }
        }.lparams(width = matchParent, height = wrapContent)


        // Albums by this artist
        val albumsView = albumsUI.createView(this)
            .lparams(width = matchParent) {
                behavior = AppBarLayout.ScrollingViewBehavior()
            }

        currentMode.openSubscription()
            .skip(1)
            .consumeEachAsync {
                (albumsView as? SwipeRefreshLayout)?.isRefreshing = true
            }
    }


    class Resolved(
        artist: Artist,
        mode: Mode
    ): ArtistDetailsUI(artist.id, mode) {
        override val artist = ConflatedBroadcastChannel(artist)
    }

    enum class Mode(@StringRes val resource: Int) {
        LIBRARY_AND_REMOTE(R.string.artist_content_all),
        LIBRARY(R.string.artist_content_library),
        REMOTE(R.string.artist_content_remote),
    }
}