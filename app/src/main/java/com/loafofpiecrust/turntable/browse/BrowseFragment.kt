package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewManager
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.album.DetailsFragment
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.browse.ui.RecommendationsFragment
import com.loafofpiecrust.turntable.model.song.Music
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.playlist.PlaylistDetailsFragment
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.song.*
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.ui.replaceMainContent
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

/**
 * Home page for browsing recommendations, history, friends, etc.
 */
class BrowseFragment: BaseFragment() {
    override fun ViewManager.createView() = verticalLayout {
        // Recommendations
        linearLayout {
            textView(R.string.recommendations).lparams {
                weight = 1f
            }
            button(R.string.show_more).lparams {
                gravity = Gravity.END
            }.onClick {
                context.replaceMainContent(RecommendationsFragment())
            }
        }
        recyclerView {
            layoutManager = LinearLayoutManager(ctx)
            adapter = MusicAdapter(
                UserPrefs.recommendations.openSubscription()
                    .map { it.take(4) }
            )
        }

        // History
        linearLayout {
            textView("Recently Played").lparams {
                weight = 1f
            }

            button(R.string.show_more).lparams {
                gravity = Gravity.END
            }.onClick {
                context.replaceMainContent(SongsFragment(
                    SongsFragment.Category.History()
                ))
            }
        }
        recyclerView {
            layoutManager = LinearLayoutManager(ctx)
            adapter = SongsAdapter { songs, pos ->
                MusicService.enact(SyncService.Message.PlaySongs(songs, pos))
            }.apply {
                subscribeData(
                    UserPrefs.history.openSubscription()
                        .map { it.asReversed().take(4).map { it.song } }
                )
            }
        }
    }
}


class MusicAdapter(
    chan: ReceiveChannel<List<Music>>
): RecyclerAdapter<Music, RecyclerListItem>() {
    init {
        subscribeData(chan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RecyclerListItem(parent, 3, false)

    override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
        val item = data[position]

        holder.mainLine.text = item.displayName

        // TODO: Add the recommending user!
        val ctx = holder.itemView.context
        when (item) {
            is Song -> {
                holder.subLine.text = item.id.artist.displayName
                holder.coverImage?.image = null
                holder.card.onClick {
                    MusicService.enact(SyncService.Message.PlaySongs(listOf(item)))
                }
            }
            is Album -> {
                holder.subLine.text = item.id.artist.displayName
                if (holder.coverImage != null) {
                    launch {
                        item.loadCover(Glide.with(holder.coverImage)).first()
                            ?.into(holder.coverImage)
                            ?: run {
                                holder.coverImage.imageResource = R.drawable.ic_default_album
                            }
                    }
                }
                holder.card.onClick {
                    ctx.replaceMainContent(
                        DetailsFragment.fromAlbum(item)
                    )
                }
            }
            is Artist -> {
                holder.subLine.text = ""
                holder.coverImage?.image = null
                holder.card.onClick {
                    ctx.replaceMainContent(
                        ArtistDetailsFragment.fromArtist(item, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE)
                    )
                }
            }
            is Playlist -> {
                holder.subLine.text = ctx.getString(R.string.playlist_author, item.owner, null)
                holder.card.onClick {
                    ctx.replaceMainContent(
                        PlaylistDetailsFragment.newInstance(item.id, item.name)
                    )
                }
            }

        }
    }

}