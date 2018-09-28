package com.loafofpiecrust.turntable.ui

import activitystarter.MakeActivityStarter
import android.support.design.widget.TabLayout
import android.support.v4.app.FragmentPagerAdapter
import android.view.View
import android.view.ViewManager
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.browse.BrowseFragment
import com.loafofpiecrust.turntable.playlist.PlaylistsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.sync.SyncTabFragment
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.design.themedAppBarLayout
import org.jetbrains.anko.support.v4.onPageChangeListener
import org.jetbrains.anko.support.v4.viewPager
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference

@MakeActivityStarter
class LibraryFragment: BaseFragment() {
    private val tabs = UserPrefs.libraryTabs.openSubscription()
        .map { it.toList() }.replayOne()
//    private val tabs = ConflatedBroadcastChannel(listOf("Artists", "Albums"))

    private val currentTabIdx = ConflatedBroadcastChannel<Int>()

    val currentTab get() = combineLatest(currentTabIdx.openSubscription(), tabs.openSubscription()) { idx, tabs -> tabs[idx] }

    val fragments = mutableMapOf<String, Reference<BaseFragment>>()

//    private val currentTabFragment get() = currentTabIdx.openSubscription()
//        .combineLatest(tabFragments.openSubscription()) { idx, tabs -> tabs[idx] }

    inner class TabsPager(
        private val tabs: List<String>,
        private val fragments: (String) -> BaseFragment
    ) : FragmentPagerAdapter(childFragmentManager) {
        override fun getCount() = tabs.size

        // TODO: Map the index to the correct tab. For now, hardcode.
        override fun getItem(position: Int) = fragments.invoke(tabs[position])

        override fun getPageTitle(position: Int) = tabs.getOrNull(position) ?: ""
    }


    override fun ViewManager.createView(): View = linearLayout {
        orientation = LinearLayout.VERTICAL

        val tabFragments = { key: String -> when (key) {
            "Albums" -> AlbumsFragment.all()
            "Songs" -> SongsFragment.all() // all songs yo
            "Artists" -> ArtistsFragment.all() // artists
            "Playlists" -> PlaylistsFragment() // playlists!
            "Friends" -> SyncTabFragment()
            "Recommendations" -> BrowseFragment()
            else -> throw IllegalStateException()
        }.also { fragments[key] = WeakReference(it) } }

        var tabs: TabLayout? = null

        themedAppBarLayout(R.style.AppTheme_AppBarOverlay) {
            topPadding = dimen(R.dimen.statusbar_height)

            UserPrefs.primaryColor.bindTo(::backgroundColor)

            toolbar {
                title = "Turntable"
                popupTheme = R.style.AppTheme_PopupOverlay
                currentTab.consumeEachAsync {
                    menu.clear()
                    fragments[it]?.get()?.onCreateOptionsMenu(menu, null)
                }
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
                width = matchParent
                height = dimen(R.dimen.toolbar_height)
            }

            tabs = tabLayout {
                tabMode = TabLayout.MODE_SCROLLABLE
//                onTabSelectedListener {
//                    onTabSelected { it?. }
//                }
            }
        }.lparams(width=matchParent, height=wrapContent)


        val pager = viewPager {
            id = R.id.container

            this@LibraryFragment.tabs.consumeEachAsync { tabs ->
                adapter = TabsPager(tabs, tabFragments)
                invalidate()
                requestLayout()
                currentTabIdx.offer(currentItem)
            }

            onPageChangeListener {
                onPageSelected {
                    currentTabIdx.offer(it)
                }
            }
            offscreenPageLimit = 3
        }.lparams {
            width = matchParent
            height = matchParent
        }

        tabs?.setupWithViewPager(pager)
    }
}