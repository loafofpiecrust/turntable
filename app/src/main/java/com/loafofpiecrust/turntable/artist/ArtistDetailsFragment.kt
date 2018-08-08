package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.graphics.Color
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.TabLayout
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.ImageView
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.AlbumsFragmentStarter
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.util.task
import com.mcxiaoke.koi.ext.onClick
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.themedAppBarLayout
import org.jetbrains.anko.support.v4.selector

/**
 * We want to be able to open Artist details with either:
 * 1. full artist
 * 2. artist id (from Song/Album)
 * 3. artist id (from saved state)
 */
class ArtistDetailsFragment: BaseFragment() {

    @Arg lateinit var artistId: ArtistId
    private lateinit var artist: ReceiveChannel<Artist>

//    @Arg lateinit var artistId: ArtistId
    @Arg(optional = true) var initialMode = Mode.LIBRARY

    enum class Mode {
        LIBRARY, REMOTE, LIBRARY_AND_REMOTE
    }

    private val currentMode by lazy { ConflatedBroadcastChannel(initialMode) }
    private lateinit var albums: AlbumsFragment

    companion object {
        fun fromId(id: ArtistId): ArtistDetailsFragment? {
            val frag = ArtistDetailsFragmentStarter.newInstance(id).apply {
                setupArtist(id)
            }
            return frag
        }

        fun fromArtist(artist: Artist, mode: Mode = Mode.LIBRARY): ArtistDetailsFragment {
            return ArtistDetailsFragmentStarter.newInstance(artist.id, mode).also {
                it.artist = produceSingle(artist)
            }
        }
    }

    // TODO: Generalize this some more
    private fun setupArtist(id: ArtistId) {
        artist = Library.instance.findArtist(id).map {
            it ?: SearchApi.find(id)!!
        }
    }

    override fun onCreate() {
        super.onCreate()

        val transDur = 250L
        onApi(21) {
            val trans = TransitionSet().setDuration(transDur)
                .addTransition(ChangeBounds().setDuration(transDur))
//                    .addTransition(ChangeImageTransform().setDuration(1500))
                .addTransition(ChangeTransform().setDuration(transDur))
                .addTransition(ChangeClipBounds().setDuration(transDur))

            sharedElementEnterTransition = trans
            sharedElementReturnTransition = trans
        }

        enterTransition = Fade().setDuration(transDur)
//        exitTransition = Fade().setDuration(transDur / 3)
    }

    override fun makeView(ui: ViewManager): View = ui.coordinatorLayout {
//        val artist = if (artist != null) {
//            produceSingle(artist!!)
//        } else {
//            ctx.library.findArtist(artistId)
//        }

        lateinit var tabs: TabLayout
        themedAppBarLayout(R.style.AppTheme_AppBarOverlay) {
            backgroundColor = Color.TRANSPARENT

            lateinit var image: ImageView
            collapsingToolbarLayout {
                // setContentScrimColor(resources.getColor(R.color.colorPrimary))
                collapsedTitleGravity = Gravity.BOTTOM
                expandedTitleGravity = Gravity.BOTTOM

                constraintLayout {
                    image = imageView {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        transitionName = artistId.imageTransition
                    }

                    // Years of the artist
//                    val year = if (artist.startYear != null) {
//                        textView {
//                            backgroundResource = R.drawable.rounded_rect
//                            textSizeDimen = R.dimen.small_text_size
//                            text = getString(
//                                R.string.artist_date_range,
//                                artist.startYear.toString(),
//                                artist.endYear ?: "Now"
//                            )
//                        }
//                    } else null

                    // Current display mode
                    val mode = textView {
                        backgroundResource = R.drawable.rounded_rect
                        textSizeDimen = R.dimen.small_text_size

                        val choices = listOf(
                            Mode.LIBRARY to "Only in Library",
                            Mode.REMOTE to "Only Not in Library",
                            Mode.LIBRARY_AND_REMOTE to "All"
                        )

                        task(UI) {
                            currentMode.consumeEach { mode ->
                                text = getString(
                                    R.string.artist_content_source,
                                    choices.find { it.first == mode }!!.second
                                )
//                                albums.category.send(
//                                    AlbumsFragment.Category.ByArtist(artist.copy(albums = listOf()), mode)
//                                )
                            }
                        }

                        onClick {
                            selector("Choose Display Mode", choices.map { it.second }) { dialog, idx ->
                                val (choice, _) = choices[idx]
                                currentMode puts choice
                            }
                        }
                    }

                    generateChildrenIds()
                    applyConstraintSet {
                        val padBy = dimen(R.dimen.details_image_padding)
                        image {
                            connect(
                                TOP to TOP of this@constraintLayout,
                                BOTTOM to BOTTOM of this@constraintLayout,
                                START to START of this@constraintLayout,
                                END to END of this@constraintLayout
                            )
                            width = matchConstraint
                            height = matchConstraint
                            dimensionRation = "H,2:1"
                        }
//                        if (year != null) {
//                            year {
//                                connect(
//                                    BOTTOM to BOTTOM of this@constraintLayout margin padBy,
//                                    START to START of this@constraintLayout margin padBy
//                                )
//                            }
//                        }
                        mode {
                            connect(
                                BOTTOM to BOTTOM of this@constraintLayout margin padBy,
                                END to END of this@constraintLayout margin padBy
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
                standardStyle(UI)
                title = artistId.displayName
                transitionName = artistId.nameTransition
//                artist.optionsMenu(menu)
            }.lparams {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }

//            artist.loadArtwork(Glide.with(image)).consumeEach(UI) {
//                it?.listener(artist.loadPalette(toolbar))
//                    ?.into(image) ?: run {
//                        image.imageResource = R.drawable.ic_default_album
//                    }
//            }

        }.lparams(width = matchParent, height = wrapContent)


        frameLayout {
            id = View.generateViewId()
            fragment(fragmentManager, AlbumsFragmentStarter.newInstance(
                AlbumsFragment.Category.ByArtist(artistId, currentMode.value),
                AlbumsFragment.SortBy.YEAR,
                3
            ).also {
                it.albums = 
            })
        }.lparams(width = matchParent) {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
//        viewPager {
//            id = View.generateViewId()
//            offscreenPageLimit = 1
//
//            adapter = object: FragmentPagerAdapter(childFragmentManager) {
//                override fun getCount() = 2
//                override fun getItem(pos: Int) = when(pos) {
//                    0 -> AlbumsFragmentStarter.newInstance(
//                        AlbumsFragment.Category.ByArtist(artist.copy(albums = listOf()), currentMode.value),
//                        AlbumsFragment.SortBy.YEAR,
//                        3
//                    ).also { albums = it }
//                    1 -> RelatedArtistsFragmentStarter.newInstance(artist)
//                    // TODO: Add artist biography
//                    // TODO: Add songs tab?
//                    else -> kotlin.error("Shouldn't be able to go past page 2.")
//                }
//
//                override fun getPageTitle(pos: Int) = when(pos) {
//                    0 -> "Albums"
//                    1 -> "Similar"
//                    else -> kotlin.error("Shouldn't be able to go past page 2.")
//                }
//            }
//        }.lparams(width = matchParent) {
//            behavior = AppBarLayout.ScrollingViewBehavior()
//        }
//        }.also {
//            tabs.setupWithViewPager(it)
//        }
    }
}
