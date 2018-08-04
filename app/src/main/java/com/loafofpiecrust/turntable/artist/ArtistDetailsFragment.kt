package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.graphics.Color
import android.os.Build
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.TabLayout
import android.transition.*
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.AlbumsFragmentStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.task
import com.mcxiaoke.koi.ext.onClick
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.themedToolbar
import org.jetbrains.anko.design.collapsingToolbarLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.themedAppBarLayout
import org.jetbrains.anko.support.v4.selector

class ArtistDetailsFragment: BaseFragment() {
    @Arg lateinit var artist: Artist
    @Arg(optional = true) var initialMode = Mode.LIBRARY

    enum class Mode {
        LIBRARY, REMOTE, LIBRARY_AND_REMOTE
    }

    private val currentMode by lazy { ConflatedBroadcastChannel(initialMode) }
    private lateinit var albums: AlbumsFragment

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
        lateinit var tabs: TabLayout
        themedAppBarLayout(R.style.AppTheme_AppBarOverlay) {
            backgroundColor = Color.TRANSPARENT

            lateinit var image: ImageView
            collapsingToolbarLayout {
                // setContentScrimColor(resources.getColor(R.color.colorPrimary))
                collapsedTitleGravity = Gravity.BOTTOM
                expandedTitleGravity = Gravity.BOTTOM

                relativeLayout {
                    image = imageView {
                        scaleType = ImageView.ScaleType.CENTER_CROP

                        onApi(Build.VERSION_CODES.LOLLIPOP) {
                            transitionName = artist.id.imageTransition
                        }
                    }.lparams(height=matchParent, width=matchParent)

                    // Years of the artist
                    if (artist.startYear != null) {
                        textView {
                            backgroundResource = R.drawable.rounded_rect
                            textSizeDimen = R.dimen.small_text_size
                            text = getString(
                                R.string.artist_date_range,
                                artist.startYear.toString(),
                                artist.endYear ?: "Now"
                            )
                        }.lparams {
                            alignParentBottom()
                            alignParentLeft()
                            margin = dip(4)
                        }
                    }

                    // Current display mode
                    textView {
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
                                albums.category.send(
                                    AlbumsFragment.Category.ByArtist(artist.copy(albums = listOf()), mode)
                                )
                            }
                        }

                        onClick {
                            selector("Choose Display Mode", choices.map { it.second }) { dialog, idx ->
                                val (choice, _) = choices[idx]
                                currentMode puts choice
                            }
                        }
                    }.lparams {
                        alignParentBottom()
                        alignParentRight()
                        margin = dip(4)
                    }
                }.collapsingToolbarlparams {
                    collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_OFF
                    height = dip(280)
                    width = matchParent
                }
            }.lparams {
                width = matchParent
                height = matchParent
            }

            val toolbar = themedToolbar(R.style.AppTheme_DetailsToolbar) {
                fitsSystemWindows = true
                UserPrefs.primaryColor.consumeEach(UI) {
                    backgroundColor = it
                }
                title = artist.id.displayName
                transitionName = artist.id.nameTransition

                artist.optionsMenu(menu)
            }.lparams {
                height = dip(72)
                width = matchParent
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }

            artist.loadArtwork(Glide.with(image)).consumeEach(UI) {
                it?.listener(artist.loadPalette(toolbar))
                    ?.into(image) ?: run {
                        image.imageResource = R.drawable.ic_default_album
                    }
            }

        }.lparams(width = matchParent, height = wrapContent)


        frameLayout {
            id = View.generateViewId()
            fragment(fragmentManager, AlbumsFragmentStarter.newInstance(
                AlbumsFragment.Category.ByArtist(artist.copy(albums = listOf()), currentMode.value),
                AlbumsFragment.SortBy.YEAR,
                3
            ).also { albums = it })
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