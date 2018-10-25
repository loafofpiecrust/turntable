package com.loafofpiecrust.turntable.ui

import activitystarter.MakeActivityStarter
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.view.View
import android.view.ViewManager
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsUI
import com.loafofpiecrust.turntable.artist.ArtistsUI
import com.loafofpiecrust.turntable.browse.BrowseFragment
import com.loafofpiecrust.turntable.playlist.PlaylistsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.SongsUI
import com.loafofpiecrust.turntable.sync.SyncTabFragment
import com.loafofpiecrust.turntable.util.combineLatest
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.support.v4.onPageChangeListener
import org.jetbrains.anko.support.v4.viewPager
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import kotlin.collections.List
import kotlin.collections.getOrNull
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.toList

@MakeActivityStarter
class LibraryFragment: BaseFragment() {
    private val tabs = UserPrefs.libraryTabs.openSubscription()
        .map { it.toList() }.replayOne()
//    private val tabs = ConflatedBroadcastChannel(listOf("Artists", "Albums"))

    private val currentTabIdx = ConflatedBroadcastChannel<Int>()

    val currentTab get() = combineLatest(currentTabIdx.openSubscription(), tabs.openSubscription()) { idx, tabs -> tabs[idx] }

    val fragments = mutableMapOf<String, Reference<Fragment>>()

//    private val currentTabFragment get() = currentTabIdx.openSubscription()
//        .combineLatest(tabFragments.openSubscription()) { idx, tabs -> tabs[idx] }

    inner class TabsPager(
        private val tabs: List<String>,
        private val fragments: (String) -> Fragment
    ) : FragmentPagerAdapter(childFragmentManager) {
        override fun getCount() = tabs.size

        // TODO: Map the index to the correct tab. For now, hardcode.
        override fun getItem(position: Int) = fragments.invoke(tabs[position])

        override fun getPageTitle(position: Int) = tabs.getOrNull(position) ?: ""
    }


    override fun ViewManager.createView(): View = linearLayout {
        orientation = LinearLayout.VERTICAL

        val tabFragments = { key: String -> when (key) {
            "Albums" -> AlbumsUI.All().createFragment()
            "Songs" -> SongsUI.All().createFragment() // all songs yo
            "Artists" -> ArtistsUI.All().createFragment() // artists
            "Playlists" -> PlaylistsFragment() // playlists!
            "Friends" -> SyncTabFragment()
            "Recommendations" -> BrowseFragment()
            else -> throw Error("Unrecognized Library tab \'$key\'")
        }.also { fragments[key] = WeakReference(it) } }

        var tabs: TabLayout? = null

        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)

            UserPrefs.primaryColor.consumeEachAsync {
                backgroundColor = it
            }

            toolbar {
                title = getString(R.string.app_name)
                popupTheme = R.style.AppTheme_PopupOverlay
                navigationIconResource = R.drawable.ic_menu
                setNavigationOnClickListener {
                    (activity as? MainActivity)?.toggleDrawer()
                }
                currentTab.consumeEachAsync {
                    menu.clear()
                    fragments[it]?.get()?.onCreateOptionsMenu(menu, null)
                }
            }

            tabs = tabLayout {
                tabMode = TabLayout.MODE_SCROLLABLE
            }
        }


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