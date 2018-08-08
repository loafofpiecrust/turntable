package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewManager
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.DetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.browse.ui.RecommendationsFragment
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.Playlist
import com.loafofpiecrust.turntable.playlist.PlaylistDetailsFragmentStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.*
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

/**
 * Home page for browsing recommendations, history, friends, etc.
 */
class BrowseFragment: BaseFragment() {
    override fun makeView(ui: ViewManager) = ui.verticalLayout {
        // Recommendations
        linearLayout {
            textView("Recommendations").lparams {
                weight = 1f
            }
            button("More").lparams {
                gravity = Gravity.END
            }.onClick {
                ctx.replaceMainContent(RecommendationsFragment())
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

            button("More").lparams {
                gravity = Gravity.END
            }.onClick {
                ctx.replaceMainContent(SongsFragmentStarter.newInstance(
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

        holder.mainLine.text = item.simpleName

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
                    task(UI) {
                        item.loadCover(Glide.with(holder.coverImage)).first()
                            ?.into(holder.coverImage)
                            ?: run {
                                holder.coverImage.imageResource = R.drawable.ic_default_album
                            }
                    }
                }
                holder.card.onClick {
                    MainActivity.replaceContent(
                        DetailsFragmentStarter.newInstance(item.id)
                    )
                }
            }
            is Artist -> {
                holder.subLine.text = ""
                holder.coverImage?.image = null
                holder.card.onClick {
                    MainActivity.replaceContent(
                        ArtistDetailsFragment.fromArtist(item, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE)
                    )
                }
            }
            is Playlist -> {
                holder.subLine.text = ctx.getString(R.string.playlist_author, item.owner, null)
                holder.card.onClick {
                    MainActivity.replaceContent(
                        PlaylistDetailsFragmentStarter.newInstance(item.id, item.name)
                    )
                }
            }

        }
    }

}