package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.graphics.Color
import android.support.annotation.StringRes
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.albumList
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


/**
 * We want to be able to open Artist details with either:
 * 1. full artist
 * 2. artist id (from Song/Album)
 * 3. artist id (from saved state)
 */
class ArtistDetailsFragment: BaseFragment() {
    enum class Mode(@StringRes val titleRes: Int) {
        LIBRARY_AND_REMOTE(R.string.artist_content_all),
        LIBRARY(R.string.artist_content_library),
        REMOTE(R.string.artist_content_remote),
    }

    @Arg lateinit var artistId: ArtistId
    // Procedural creation:
    // init: artistId is known
    //       create channel of found artist from id (maybe local or remote)
    // onResume: start receiving events on the UI (reopen channels)
    // onPause: stop receiving events to the UI (cancel channels)
    // onResume: resume receiving events on the UI (reopen channels)
    // onDestroy: get rid of any trace of channels, we don't care anymore

    // Restoration:
    // onCreate: load artistId from bundle and find it via a channel
    private lateinit var artist: BroadcastChannel<Artist>
    private lateinit var albums: BroadcastChannel<List<Album>>

    @Arg(optional = true) var initialMode = Mode.LIBRARY

    private val currentMode by lazy { ConflatedBroadcastChannel(initialMode) }

    companion object {
        fun fromId(id: ArtistId): ArtistDetailsFragment {
            return ArtistDetailsFragmentStarter.newInstance(id)
        }

        fun fromArtist(artist: Artist, mode: Mode = Mode.LIBRARY): ArtistDetailsFragment {
            return ArtistDetailsFragment().also {
                it.initialMode = mode
                it.artistId = artist.id
                it.artist = ConflatedBroadcastChannel(artist)
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        // TODO: Generalize this some more
        if (!::artist.isInitialized) {
            artist = Library.instance.findArtist(artistId).map {
                it ?: SearchApi.find(artistId)!!
            }.broadcast(Channel.CONFLATED)
        }

        if (!::albums.isInitialized) {
            albums = artist.openSubscription()
                .map { it.albums }
                .broadcast(Channel.CONFLATED)
        }

        val trans = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())

        sharedElementEnterTransition = trans
        sharedElementReturnTransition = trans

        enterTransition = Fade()
//        exitTransition = Fade().setDuration(transDur / 3)
    }

    override fun ViewManager.createView() = coordinatorLayout {
        appBarLayout {
            backgroundColor = Color.TRANSPARENT

            lateinit var image: ImageView
            collapsingToolbarLayout {
                collapsedTitleGravity = Gravity.BOTTOM
                expandedTitleGravity = Gravity.BOTTOM

                constraintLayout {
                    image = imageView {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        transitionName = artistId.imageTransition
                    }

                    // Active year range of the artist
                    val year = textView {
                        backgroundResource = R.drawable.rounded_rect
                        textSizeDimen = R.dimen.small_text_size
                        artist.consumeEach(UI) { artist ->
                            if (artist.startYear != null) {
                                visibility = View.VISIBLE
                                text = getString(
                                    R.string.artist_date_range,
                                    artist.startYear,
                                    artist.endYear ?: getString(R.string.artist_active_now)
                                )
                            } else {
                                visibility = View.GONE
                            }
                        }
                    }

                    // Current display mode
                    val mode = textView {
                        backgroundResource = R.drawable.rounded_rect
                        textSizeDimen = R.dimen.small_text_size

                        currentMode.consumeEach(UI) { mode ->
                            text = getString(
                                R.string.artist_content_source,
                                getString(mode.titleRes)
                            )
                        }

                        onClick(UI) {
                            currentMode puts context.selector(
                                getString(R.string.artist_pick_source),
                                Mode.values().toList(),
                                format = { getString(it.titleRes) }
                            )
                        }
                    }

                    generateChildrenIds()
                    applyConstraintSet {
                        val padBy = dimen(R.dimen.details_image_padding)
                        image {
                            connect(
                                TOP to TOP of PARENT_ID,
                                BOTTOM to BOTTOM of PARENT_ID,
                                START to START of PARENT_ID,
                                END to END of PARENT_ID
                            )
                            width = matchConstraint
                            height = matchConstraint
                            dimensionRation = "H,2:1"
                        }
                        year {
                            connect(
                                BOTTOM to BOTTOM of PARENT_ID margin padBy,
                                START to START of PARENT_ID margin padBy
                            )
                        }
                        mode {
                            connect(
                                BOTTOM to BOTTOM of PARENT_ID margin padBy,
                                END to END of PARENT_ID margin padBy
                            )
                        }
                    }
                }.collapsingToolbarlparams {
                    collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_OFF
                    width = matchParent
                }
            }.lparams {
                width = matchParent
                height = matchParent
            }

            val toolbar = toolbar {
                standardStyle()
                title = artistId.displayName
                transitionName = artistId.nameTransition
                artist.consumeEach(UI) {
                    menu.clear()
                    it.optionsMenu(ctx, menu)
                }
            }.lparams(width = matchParent) {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }

            artist.openSubscription().switchMap { artist ->
                artist.loadArtwork(Glide.with(image)).map {
                    it?.listener(artist.loadPalette(toolbar))
                }
            }.consumeEach(UI) {
                it?.into(image) ?: run {
                    image.imageResource = R.drawable.ic_default_album
                }
            }

        }.lparams(width = matchParent, height = wrapContent)


        albumList(
            albums,
            AlbumsFragment.Category.ByArtist(artistId, currentMode.value),
            AlbumsFragment.SortBy.YEAR,
            3
        ) {
            id = R.id.gridRecycler
        }.lparams(width = matchParent) {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
    }
}
