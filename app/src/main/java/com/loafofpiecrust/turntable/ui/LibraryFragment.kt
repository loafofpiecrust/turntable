package com.loafofpiecrust.turntable.ui

import activitystarter.MakeActivityStarter
import android.support.design.widget.TabLayout
import android.support.v4.app.FragmentPagerAdapter
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.AlbumsFragmentStarter
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.artist.ArtistsFragmentStarter
import com.loafofpiecrust.turntable.bindCurrentPage
import com.loafofpiecrust.turntable.browse.BrowseFragment
import com.loafofpiecrust.turntable.playlist.PlaylistsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.song.SongsFragmentStarter
import com.loafofpiecrust.turntable.sync.SyncTabFragment
import com.loafofpiecrust.turntable.util.combineLatest
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.zip
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.design.themedAppBarLayout
import org.jetbrains.anko.support.v4.viewPager

@MakeActivityStarter
class LibraryFragment: BaseFragment() {
    private val tabs = UserPrefs.libraryTabs.openSubscription()
        .map { it.toList() }.replayOne()

    private val tabFragments = tabs.openSubscription().map { it.map { when (it) {
        "Albums" -> AlbumsFragmentStarter.newInstance(AlbumsFragment.Category.All(), AlbumsFragment.SortBy.TITLE) // albums
        "Songs" -> SongsFragmentStarter.newInstance(SongsFragment.Category.All()) // all songs yo
        "Artists" -> ArtistsFragmentStarter.newInstance(ArtistsFragment.Category.All()) // artists
        "Playlists" -> PlaylistsFragment() // playlists!
        "Friends" -> SyncTabFragment()
        "Recommendations" -> BrowseFragment()
        else -> throw IllegalStateException()
    } } }.replayOne()
    lateinit var tabsAdapter: TabsPager

    private val currentTabIdx = ConflatedBroadcastChannel<Int>()

    val currentTab get() = currentTabIdx.openSubscription()
        .combineLatest(tabs.openSubscription()) { idx, tabs -> tabs[idx] }

    private val currentTabFragment get() = currentTabIdx.openSubscription()
        .combineLatest(tabFragments.openSubscription()) { idx, tabs -> tabs[idx] }

    inner class TabsPager(
        private val tabs: List<String>,
        private val fragments: List<BaseFragment>
    ) : FragmentPagerAdapter(childFragmentManager) {
        override fun getCount() = maxOf(tabs.size, fragments.size)

        // TODO: Map the index to the correct tab. For now, hardcode.
        override fun getItem(position: Int) = fragments.getOrNull(position)
//
//        override fun getPageTitle(position: Int) = when(position) {
//            0 -> "Albums"
//            1 -> "Songs"
//            2 -> "Artists"
//            3 -> "Playlists"
//            4 -> "Friends"
//            else -> "Recommendations"
//        }
        override fun getPageTitle(position: Int) = tabs.getOrNull(position) ?: ""
    }


    override fun makeView(ui: ViewManager): View = ui.verticalLayout {
        var tabs: TabLayout? = null

        themedAppBarLayout(R.style.AppTheme_AppBarOverlay) {
            topPadding = dimen(R.dimen.statusbar_height)

            UserPrefs.primaryColor.consumeEach(UI) {
                backgroundColor = it
            }
            toolbar {
                MainActivity.latest.setSupportActionBar(this)
//                fitsSystemWindows = true
                UserPrefs.primaryColor.consumeEach(UI) {
                    backgroundColor = it
                }

                title = "Turntable"
                popupTheme = R.style.AppTheme_PopupOverlay
                currentTabFragment.consumeEach(UI) {
                    menu.clear()
                    it.onCreateOptionsMenu(menu, null)
                    Unit
                }

//                menuItem("Search", R.drawable.ic_search, showIcon = true) {
//                    onClick {
//                        val cat = when (this@LibraryFragment.tabs.blockingFirst()[pager?.currentItem!!]) {
//                            "Albums" -> SearchFragment.Category.ALBUMS
//                            "Songs" -> SearchFragment.Category.SONGS
//                            "Artists" -> SearchFragment.Category.ARTISTS
//                            else -> null
//                        }
//                        if (cat != null) {
//                            activity?.supportFragmentManager?.replaceMainContent(
//                                SearchFragmentStarter.newInstance(cat),
//                                true
//                            )
//                        }
//                    }
//                }
//
//                subMenu("Grid size") {
//                    setOnMenuItemClickListener {
//                        if (it.name != "Grid size") {
//                            val size = it.name.toString().toInt()
//                            val tab = this@LibraryFragment.tabs.blockingFirst()[pager?.currentItem!!]
//                            when (tab) {
//                                "Albums" -> UserPrefs.albumGridColumns
//                                "Artists" -> UserPrefs.artistGridColumns
//                                else -> error("This is not a grid tab")
//                            } puts size
//                            // TODO: Refresh
//                            true
//                        } else false
//                    }
//                    group(0, true, true) {
//                        val one = menuItem("1") {}
//                        val two = menuItem("2") {}
//                        val three = menuItem("3") {}
//                        subscriptions += Observables.combineLatest(
//                            currentTab,
//                            UserPrefs.albumGridColumns,
//                            UserPrefs.artistGridColumns
//                        ).subscribe { (tab, albumCols, artistCols) ->
//                            one.isChecked = false
//                            two.isChecked = false
//                            three.isChecked = false
//
//                            when(when (tab) {
//                                "Albums" -> albumCols
//                                "Artists" -> artistCols
//                                else -> artistCols
//                            }) {
//                                1 -> one
//                                2 -> two
//                                3 -> three
//                                else -> one
//                            }.isChecked = true
//                        }
//                    }
//                }
            }.lparams {
                topMargin = dip(8)
                width = matchParent
                height = dip(48)
            }

            tabs = tabLayout {
                tabMode = TabLayout.MODE_SCROLLABLE
            }
        }.lparams(width=matchParent, height=wrapContent)


        val pager = viewPager {
            id = R.id.container
            tabFragments.openSubscription().zip(
                this@LibraryFragment.tabs.openSubscription()
            ).consumeEach(UI) { (frags, tabs) ->
                adapter = TabsPager(tabs, frags)
                invalidate()
                requestLayout()
            }
//            adapter = TabsPager(childFragmentManager).also { adapter ->
//                tabsAdapter = adapter
//                tabFragments.consumeEach(UI) {
//                    adapter.notifyDataSetChanged()
//                    invalidate()
//                    requestLayout()
//                }
//            }

            bindCurrentPage(currentTabIdx, jobs)
            offscreenPageLimit = 3
        }.lparams {
            width = matchParent
            height = matchParent
        }

        tabs?.setupWithViewPager(pager)
    }
}