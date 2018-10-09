package com.loafofpiecrust.turntable.playlist

import android.content.res.ColorStateList
import android.support.v7.graphics.Palette
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.RecentMixTapesFragment
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.sync.User
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Job
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.textColor


class PlaylistsFragment: BaseFragment() {
    companion object {
        fun newInstance(
            user: User? = null,
            columnCount: Int = 0
        ) = PlaylistsFragment().apply {
            user?.let { this.user = it }
            this.columnCount = columnCount
        }
    }
    private var user: User by arg { SyncService.selfUser }
    private var columnCount: Int by arg()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.menuItem(R.string.mixtapes_recent).onClick {
            context?.replaceMainContent(RecentMixTapesFragment(), true)
        }

        menu.menuItem(R.string.playlist_new, R.drawable.ic_add, showIcon =true).onClick {
            AddPlaylistDialog().show(requireContext(), fullscreen = true)
        }
    }

    override fun ViewManager.createView() = recyclerView {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter = Adapter { playlist ->
            println("playlist: opening '${playlist.name}'")
            activity?.supportFragmentManager?.replaceMainContent(
                when(playlist) {
                    is MixTape -> MixTapeDetailsFragment.newInstance(playlist.uuid, playlist.name)
//                    is AlbumCollection -> AlbumsUI.Custom(playlist.albums.broadcast(CONFLATED)).createFragment()
                    else -> PlaylistDetailsFragment.newInstance(playlist.uuid, playlist.name)
                },
                true
            )
        }.also { adapter ->
            adapter.subscribeData(if (user === SyncService.selfUser) {
                UserPrefs.playlists.openSubscription()
            } else produceSingle {
                Playlist.allByUser(user)
            })
        }
    }


    class Adapter(
        val listener: (Playlist) -> Unit
    ): RecyclerBroadcastAdapter<Playlist, RecyclerListItemOptimized>() {
        override val dismissEnabled: Boolean
            get() = false

        override val moveEnabled: Boolean
            get() = true

        override fun canMoveItem(index: Int) = true
        override fun onItemMove(fromIdx: Int, toIdx: Int) {
            UserPrefs.playlists putsMapped { it.shifted(fromIdx, toIdx) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItemOptimized
            = RecyclerListItemOptimized(parent, 3, useIcon = true)

        override fun RecyclerListItemOptimized.onBind(item: Playlist, position: Int, job: Job) {
            val item = data[position]
            val ctx = itemView.context
            val typeName = item.javaClass.localizedName(ctx)
            mainLine.text = item.name
            subLine.text = if (item.owner == SyncService.selfUser) {
                typeName
            } else {
                ctx.getString(R.string.playlist_author, item.owner.name, typeName)
            }
//            header.transitionName = "playlistHeader${item.name}"

            given(item.color) {
                val contrast = Palette.Swatch(it, 0).titleTextColor
                card.backgroundTintList = ColorStateList.valueOf(it)
                mainLine.textColor = contrast
                subLine.textColor = contrast
                menu.tint = contrast
            }

            statusIcon.imageResource = item.icon

            card.setOnClickListener {
                listener.invoke(item)
            }
        }

        override fun itemsSame(a: Playlist, b: Playlist, aIdx: Int, bIdx: Int): Boolean {
            return a.uuid == b.uuid
        }
    }
}