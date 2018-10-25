package com.loafofpiecrust.turntable.playlist

import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewGroup
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.playlist.AbstractPlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.MutableMixtape
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.SongsOnDiscAdapter
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.ui.SectionedAdapter
import com.loafofpiecrust.turntable.ui.UIComponent
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.SimpleHeaderViewHolder
import com.mcxiaoke.koi.ext.toast
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView

@Parcelize
open class MixtapeDetailsUI(
    private val playlistId: PlaylistId
): UIComponent(), Parcelable {
    protected open val playlist by lazy {
        Library.instance.findPlaylist(playlistId.uuid).switchMap {
            if (it == null) {
                AbstractPlaylist.findChannel(playlistId.uuid)
            } else produceSingle(it)
        }.map { it as? MixTape }.replayOne()
    }

    class Resolved(
        id: PlaylistId,
        playlist: ReceiveChannel<MixTape?>
    ): MixtapeDetailsUI(id) {
        override val playlist = playlist.replayOne()
    }

    override fun CoroutineScope.render(ui: AnkoContext<Any>) = ui.verticalLayout {
//        backgroundColorResource = R.color.background

        appBarLayout {
            playlist.openSubscription().consumeEachAsync {
                backgroundColor = it?.color ?: UserPrefs.primaryColor.value
            }
            topPadding = dimen(R.dimen.statusbar_height)

            toolbar {
                standardStyle()
                title = playlistId.displayName
                transitionName = playlistId.toString()

                menuItem(R.string.download, R.drawable.ic_cloud_download, showIcon = true).onClick(Dispatchers.Default) {
                    //                    playlist.tracks.first()
//                        .filter { ctx.library.findSong(it.uuid).first() == null }
//                        .forEach { it.download() }
                }

                menuItem(R.string.share).onClick {
                    FriendPickerDialog(
                        Message.Recommend(playlistId),
                        "Share"
                    ).show(context)
                }

                menuItem(R.string.playlist_publish).onClick(coroutineContext) {
                    context.alert("Publish this mixtape?") {
                        positiveButton("Publish") {
                            val mutable = playlist.value as? MutableMixtape
                            if (mutable != null) {
                                mutable.publish()
                                toast("Mixtape published.")
                            } else {
                                toast("You don't have permission.")
                            }
                        }
                        negativeButton("Cancel") {}
                    }.show()
                }
            }.lparams(width = matchParent)

//            tabs = tabLayout()
        }.lparams(width = matchParent, height = wrapContent)

        recyclerView {
            layoutManager = LinearLayoutManager(context)
            adapter = SongsOnDiscAdapter(
                playlist.openSubscription().switchMap { pl ->
                    pl!!.sides.openSubscription().map {
                        pl to it
                    }
                }.map { (mixtape, sides) ->
                    var index = 0
                    sides.associateBy { mixtape.type.sideNames[index++] + " Side" }
                }
            ) { song ->
                val allTracks = playlist.value!!.tracks
                val idx = allTracks.indexOf(song)
                MusicService.offer(PlayerAction.PlaySongs(allTracks, idx))
            }
        }

//        val pager = viewPager {
//            id = R.id.container
//            adapter = object : FragmentPagerAdapter(childFragmentManager) {
//                override fun getPageTitle(position: Int) = ('A' + position) + " Side"
//
//                override fun getItem(idx: Int) = SongsFragment(
//                    SongsFragment.Category.Playlist(playlist.uuid, idx),
//                    playlist.tracksOnSide(idx).replayOne()
//                )
//
//                override fun getCount(): Int = playlist.type.sideCount
//            }
//        }
//        tabs.setupWithViewPager(pager)
    }

}

class MixTapeTracksAdapter(
    val mixtape: ConflatedBroadcastChannel<MixTape?>
): SectionedAdapter<Song, Int, RecyclerListItemOptimized, SimpleHeaderViewHolder>(
    mixtape.openSubscription().switchMap {
        it!!.sides.openSubscription()
    }.map {
        var index = 0
        it.associateBy { index++ }
    }
) {
    override fun onCreateItemViewHolder(parent: ViewGroup) =
        RecyclerListItemOptimized(parent)

    override fun onCreateHeaderViewHolder(parent: ViewGroup) =
        SimpleHeaderViewHolder(parent)

    override fun RecyclerListItemOptimized.onBindItem(item: Song, position: Int, job: Job) {
        mainLine.text = item.id.displayName
        subLine.text = item.id.artist.displayName
    }

    override fun SimpleHeaderViewHolder.onBindHeader(key: Int, job: Job) {
        val context = itemView.context
        mainLine.text = context.getString(R.string.mixtape_side, mixtape.value!!.type.sideNames[key])
    }
}