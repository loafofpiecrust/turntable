package com.loafofpiecrust.turntable.artist

import android.support.annotation.StringRes
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.albumList
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.MergedArtist
import com.loafofpiecrust.turntable.model.artist.loadPalette
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.imageTransition
import com.loafofpiecrust.turntable.model.nameTransition
import com.loafofpiecrust.turntable.style.standardStyle
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
class ArtistDetailsFragment: BaseFragment() {
    private var artistId: ArtistId by arg()

    enum class Mode(@StringRes val resource: Int) {
        LIBRARY_AND_REMOTE(R.string.artist_content_all),
        LIBRARY(R.string.artist_content_library),
        REMOTE(R.string.artist_content_remote),
    }
    private var initialMode: Mode by arg(Mode.LIBRARY)

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


    private val currentMode by lazy { ConflatedBroadcastChannel(initialMode) }


//    private var localArtist: ReceiveChannel<Artist>? = null
//    private var remoteArtist: ReceiveChannel<Artist>? = null

    init {
        val trans = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeClipBounds())

        sharedElementEnterTransition = trans
//        sharedElementReturnTransition = trans

        enterTransition = Fade()
        exitTransition = Fade()
    }

    override fun onCreate() {
        super.onCreate()

        // TODO: Generalize this some more
        if (!::artist.isInitialized) {
            artist = Library.instance.findArtist(artistId).map {
                it ?: SearchApi.findOnline(artistId)!!
            }.broadcast(CONFLATED)
        }

        if (!::albums.isInitialized) {
            albums = currentMode.openSubscription()
                .switchMap { mode ->
                    when (mode) {
                        // TODO: Do some caching of remotes here. Do this inside SearchApi :)
                        Mode.LIBRARY -> Library.instance.findArtist(artistId)
                        Mode.REMOTE -> produceSingle { SearchApi.findOnline(artistId) }
                        Mode.LIBRARY_AND_REMOTE -> Library.instance.findArtist(artistId).map { local ->
                            val remote = SearchApi.findOnline(artistId)
                            if (local != null && remote != null) {
                                MergedArtist(local, remote)
                            } else local ?: remote
                        }
                    }
                }
                .map(Dispatchers.IO) { it?.albums ?: emptyList() }
                .broadcast(CONFLATED)
        }

//        exitTransition = Fade()
    }

    override fun ViewManager.createView() = coordinatorLayout {
        id = R.id.container

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
                        backgroundResource = R.drawable.rounded_rect
                        textSizeDimen = R.dimen.small_text_size
                        artist.consumeEachAsync { artist ->
                            val start = artist.startYear
                            val end = artist.endYear
                            if (start != null && start != end) {
                                visibility = View.VISIBLE
                                text = getString(
                                    R.string.artist_date_range,
                                    start,
                                    end ?: getString(R.string.artist_active_now)
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

                        currentMode.consumeEachAsync { mode ->
                            text = getString(
                                R.string.artist_content_source,
                                getString(mode.resource)
                            )
                        }

                        onClick {
                            currentMode puts context.selector(
                                getString(R.string.artist_pick_source),
                                Mode.values().toList(),
                                format = { getString(it.resource) }
                            )
                        }
                    }

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
                        }
                        mode {
                            connect(
                                START to START of year,
                                TOP to BOTTOM of year margin padBy,
                                BOTTOM to BOTTOM of image margin padBy
                            )
                        }
                    }
                }.collapsingToolbarlparams(width = matchParent) {
                    collapseMode = COLLAPSE_MODE_PIN
                }

            }.lparams {
                width = matchParent
                height = matchParent
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }

            val toolbar = toolbar {
                standardStyle()
                title = artistId.displayName
                transitionName = artistId.nameTransition
                artist.consumeEachAsync {
                    menu.clear()
                    it.optionsMenu(context, menu)
                }
            }.lparams(width = matchParent) {
                gravity = Gravity.TOP
            }

            artist.openSubscription().switchMap { artist ->
                artist.loadArtwork(Glide.with(image)).map {
                    it?.addListener(artist.loadPalette(toolbar, collapser, this@appBarLayout))
                }
            }.consumeEachAsync {
                it?.into(image) ?: run {
                    image.imageResource = R.drawable.ic_default_album
                }
            }

        }.lparams(width = matchParent, height = wrapContent)


        // Albums by this artist
        albumList(
            albums,
            AlbumsFragment.Category.ByArtist(artistId, currentMode.value),
            AlbumsFragment.SortBy.YEAR,
            3
        ) {
            id = R.id.albums
        }.lparams(width = matchParent) {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
    }


    companion object {
        fun fromId(id: ArtistId) = ArtistDetailsFragment().apply {
            this.artistId = id
        }

        fun fromArtist(artist: Artist, mode: Mode) = ArtistDetailsFragment().apply {
            initialMode = mode
            artistId = artist.id
            this.artist = ConflatedBroadcastChannel(artist)
        }
    }
}
