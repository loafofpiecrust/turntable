package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.support.v7.graphics.Palette
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.browse.RecentMixTapesFragment
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import kotlinx.coroutines.experimental.channels.produce
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.textColor


class PlaylistsFragment: BaseFragment() {
    @Arg(optional=true) var user: SyncService.User? = null
    @Arg(optional=true) var columnCount: Int = 0

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.menuItem("Recent Mixtapes").onClick {
            ctx.replaceMainContent(RecentMixTapesFragment(), true)
        }

        menu.menuItem("Add", R.drawable.ic_add, showIcon=true).onClick {
            AddPlaylistActivityStarter.start(ctx)
        }
    }

    override fun ViewManager.createView() = recyclerView {
        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
        adapter = Adapter { playlist ->
            println("playlist: opening '${playlist.name}'")
            ctx.replaceMainContent(
                when(playlist) {
                    is MixTape -> MixTapeDetailsFragmentStarter.newInstance(playlist.id, playlist.name)
                    is AlbumCollection -> AlbumsFragment.fromChan(playlist.albums)
                    else -> PlaylistDetailsFragmentStarter.newInstance(playlist.id, playlist.name)
                },
                true
            )
        }.also { adapter ->
            adapter.subscribeData(given(user) { user ->
                produce(BG_POOL) { send(MixTape.allFromUser(user)) }
            } ?: UserPrefs.playlists.openSubscription())
        }
    }

    class Adapter(
        val listener: (Playlist) -> Unit
    ): RecyclerAdapter<Playlist, RecyclerListItem>(
        itemsSame = { a, b, aIdx, bIdx -> a.id == b.id },
        contentsSame = { a, b, aIdx, bIdx -> a == b }
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItem
            = RecyclerListItem(parent, 3, useIcon = true)

        override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
            val item = data[position]
            val ctx = holder.itemView.context
            holder.mainLine.text = item.name
            val owner = if (item.owner == SyncService.selfUser) {
                "you"
            } else item.owner.name
            holder.subLine.text = ctx.getString(R.string.playlist_author, owner, item.typeName)
            holder.track.visibility = View.GONE
            holder.header.transitionName = "playlistHeader${item.name}"

            given(item.color) {
                val contrast = Palette.Swatch(it, 0).titleTextColor
                holder.card.backgroundColor = it
                holder.mainLine.textColor = contrast
                holder.subLine.textColor = contrast
                holder.menu.setColorFilter(contrast)
            }

            holder.coverImage?.imageResource = when (item) {
                is MixTape -> R.drawable.ic_cassette
                is CollaborativePlaylist -> R.drawable.ic_boombox_color
                else -> R.drawable.ic_album
            }

            holder.card.setOnClickListener {
                listener.invoke(item)
            }
        }
    }
}