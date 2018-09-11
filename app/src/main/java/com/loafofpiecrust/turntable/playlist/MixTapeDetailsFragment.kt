package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.support.design.widget.TabLayout
import android.support.v4.app.FragmentPagerAdapter
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.SongsFragment
import com.loafofpiecrust.turntable.song.SongsFragmentStarter
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.ALT_BG_POOL
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.tabLayout
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.viewPager
import java.util.*


class MixTapeDetailsFragment: BaseFragment() {
    @Arg lateinit var playlistId: UUID
    @Arg lateinit var playlistTitle: String

    private lateinit var tabs: TabLayout
    private lateinit var playlist: MixTape

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
    }

    override fun ViewManager.createView(): View = verticalLayout {
        playlist = runBlocking {
            Library.instance.findPlaylist(playlistId).first()
                ?: ctx.library.findCachedPlaylist(playlistId).first()
        } as MixTape

        appBarLayout {
            backgroundColor = playlist.color ?: UserPrefs.primaryColor.value
            topPadding = dimen(R.dimen.statusbar_height)

            toolbar {
                standardStyle()
                title = playlistTitle
                transitionName = playlistId.toString()

                menuItem(R.string.download, R.drawable.ic_cloud_download, showIcon = true).onClick(ALT_BG_POOL) {
//                    playlist.tracks.first()
//                        .filter { ctx.library.findSong(it.id).first() == null }
//                        .forEach { it.download() }
                }

                menuItem(R.string.share).onClick {
                    FriendPickerDialog(
                        SyncService.Message.Playlist(playlistId),
                        "Share"
                    ).show(ctx)
                }

                menuItem(R.string.playlist_publish).onClick {
                    alert("Publish this mixtape?") {
                        positiveButton("Publish") {
                            playlist.publish()
                        }
                        negativeButton("Cancel") {}
                    }.show()
                }
            }.lparams(width = matchParent)

            tabs = tabLayout()
        }.lparams(width = matchParent, height = wrapContent)

        val pager = viewPager {
            id = R.id.container
            adapter = object : FragmentPagerAdapter(childFragmentManager) {
                override fun getPageTitle(position: Int) = ('A' + position) + " Side"

                override fun getItem(idx: Int) = SongsFragmentStarter.newInstance(
                    SongsFragment.Category.Playlist(playlist.id, idx)
                ).apply {
//                    songs = playlist.tracksOnSide(idx).replayOne()
                }

                override fun getCount(): Int = playlist.type.sideCount
            }
        }
        tabs.setupWithViewPager(pager)
    }
}