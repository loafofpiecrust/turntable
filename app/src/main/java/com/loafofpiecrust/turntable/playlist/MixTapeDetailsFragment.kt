package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.support.design.widget.TabLayout
import android.support.v4.app.FragmentPagerAdapter
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.song.SongsFragmentStarter
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.ALT_BG_POOL
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.themedToolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.viewPager
import java.util.*


class MixTapeDetailsFragment: BaseFragment() {
    @Arg lateinit var playlistId: UUID
    @Arg lateinit var playlistTitle: String

    private lateinit var tabs: TabLayout

    override fun makeView(ui: ViewManager): View = ui.relativeLayout {
        val playlist = runBlocking {
            Library.instance.findPlaylist(playlistId).first()
                ?: ctx.library.findCachedPlaylist(playlistId).first()
        } as MixTape

//        MixTape.queryMostRecent(TimeUnit.DAYS.toMillis(10)).success(UI) {
//            println("mixtape: recents... $it")
//        }

        verticalLayout {
            appBarLayout {
                backgroundColor = playlist.color ?: UserPrefs.primaryColor.value

                themedToolbar(R.style.AppTheme_DetailsToolbar) {
                    title = playlistTitle
                    transitionName = playlistId.toString()

                    menuItem("Download", R.drawable.ic_cloud_download, showIcon=true).onClick(ALT_BG_POOL) {
                        playlist.tracks.first()
                            .filter { ctx.library.findSong(it.id).first()?.local == null }
                            .forEach { it.download() }
                    }

                    menuItem("Share", showIcon=false).onClick {
                        FriendPickerDialog().apply {
                            onAccept = {
                                SyncService.send(
                                    SyncService.Message.Playlist(playlist.id),
                                    SyncService.Mode.OneOnOne(it)
                                )
                            }
                        }.show(MainActivity.latest.supportFragmentManager, "friends")
                    }
                }.lparams(width = matchParent, height = dip(72))

                tabs = tabLayout()
            }.lparams(width = matchParent, height = wrapContent)

            val pager = viewPager {
                id = R.id.container
                adapter = object : FragmentPagerAdapter(childFragmentManager) {
                    override fun getPageTitle(position: Int) = ('A' + position) + " Side"

                    override fun getItem(idx: Int) = SongsFragmentStarter.newInstance(
                        SongsFragment.Category.Playlist(playlist.id, idx)
                    )

                    override fun getCount(): Int = playlist.type.sideCount
                }
            }
            tabs.setupWithViewPager(pager)
        }

        floatingActionButton {
            imageResource = R.drawable.ic_publish
            setOnClickListener {
                alert("Publish this mixtape?") {
                    positiveButton("Publish") {
                        playlist.publish()
                    }
                    negativeButton("Cancel") {}
                }.show()
            }
        }.lparams {
            alignParentBottom()
            alignParentRight()
            margin = dimen(R.dimen.fullscreen_card_margin)
        }
    }

}