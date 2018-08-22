package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumsFragment
import com.loafofpiecrust.turntable.album.AlbumsFragmentStarter
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
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx


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

    override fun makeView(ui: ViewManager): View = ui.recyclerView {
        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
        adapter = Adapter { playlist ->
            println("playlist: opening '${playlist.name}'")
            ctx.replaceMainContent(
                when(playlist) {
                    is MixTape -> MixTapeDetailsFragmentStarter.newInstance(playlist.id, playlist.name)
//                    is AlbumCollection -> AlbumsFragmentStarter.newInstance(AlbumsFragment.Category.Custom(runBlocking { playlist.albums.first() }))
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
            holder.subLine.text = ctx.getString(R.string.playlist_author, item.owner, item.typeName)
            holder.track.visibility = View.GONE
            holder.header.transitionName = "playlistHeader${item.name}"

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