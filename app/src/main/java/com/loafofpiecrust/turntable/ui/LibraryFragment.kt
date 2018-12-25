package com.loafofpiecrust.turntable.ui

import activitystarter.MakeActivityStarter
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v7.widget.Toolbar
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsUI
import com.loafofpiecrust.turntable.artist.ArtistsUI
import com.loafofpiecrust.turntable.browse.RecommendationsUI
import com.loafofpiecrust.turntable.playlist.PlaylistsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.serialize.page
import com.loafofpiecrust.turntable.song.ShufflableSongsUI
import com.loafofpiecrust.turntable.song.SongsUI
import com.loafofpiecrust.turntable.sync.SyncTabFragment
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.replayOne
import io.paperdb.Paper
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.support.v4.onPageChangeListener
import org.jetbrains.anko.support.v4.viewPager
import kotlin.collections.set

class LibraryFragment: BaseFragment() {
    private val fragments = mutableMapOf<String, Fragment>()
    private val createFragment = { key: String ->
        fragments.getOrPut(key) {
            when (key) {
                "Albums" -> AlbumsUI.All().createFragment()
                "Songs" -> ShufflableSongsUI().createFragment() // all songs yo
                "Artists" -> ArtistsUI.All().createFragment() // artists
                "Playlists" -> PlaylistsFragment() // playlists!
                "Friends" -> SyncTabFragment()
                "Recommendations" -> RecommendationsUI().createFragment()
                else -> error("Unrecognized Library tab \'$key\'")
            }
        }
    }

    inner class TabsPager(
        var tabs: List<String>
    ) : FragmentStatePagerAdapter(childFragmentManager) {
        override fun getCount() = tabs.size

        override fun getItem(position: Int) = createFragment(tabs[position])

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val frag = super.instantiateItem(container, position)
            fragments[tabs[position]] = frag as Fragment
            return frag
        }

        override fun getPageTitle(position: Int) = tabs.getOrNull(position) ?: ""
    }


    override fun ViewManager.createView(): View = linearLayout {
        orientation = LinearLayout.VERTICAL

        var tabs: TabLayout? = null
        lateinit var toolbar: Toolbar

        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)

            UserPrefs.primaryColor.consumeEachAsync {
                backgroundColor = it
            }

            toolbar = toolbar {
                title = getString(R.string.app_name)
                popupTheme = R.style.AppTheme_PopupOverlay
                navigationIconResource = R.drawable.ic_menu
                setNavigationOnClickListener {
                    (activity as? MainActivity)?.toggleDrawer()
                }
            }

            tabs = tabLayout {
                tabMode = TabLayout.MODE_SCROLLABLE
                UserPrefs.accentColor.consumeEachAsync {
                    setSelectedTabIndicatorColor(it)
                }
            }
        }


        val pager = viewPager {
            id = R.id.container
            val tabsAdapter = TabsPager(LibraryFragment.tabs.value)
            adapter = tabsAdapter

            LibraryFragment.tabs.consumeEachAsync { tabs ->
                tabsAdapter.tabs = tabs
                tabsAdapter.notifyDataSetChanged()
            }

            tabsAdapter.getItem(currentItem)
                .onCreateOptionsMenu(toolbar.menu, null)

            onPageChangeListener {
                onPageSelected { idx ->
                    toolbar.menu.clear()

                    val frag = tabsAdapter.getItem(idx)
//                    if (frag.context != null) {
                        frag.onCreateOptionsMenu(toolbar.menu, null)
//                    }
                }
            }
            offscreenPageLimit = 2
        }.lparams {
            width = matchParent
            height = matchParent
        }

        tabs?.setupWithViewPager(pager)
    }

    companion object {
        val tabs by Paper.page("libraryTabs") {
            listOf("Albums", "Artists", "Playlists", "Friends", "Recommendations")
        }
    }
}