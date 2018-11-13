package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.Menu
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumDetailsUI
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.AbstractPlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.MixtapeDetailsUI
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.song.SongsAdapter
import com.loafofpiecrust.turntable.song.SongsUI
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.withContext
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick
import kotlin.coroutines.CoroutineContext

/**
 * Home page for browsing recommendations, history, friends, etc.
 */
class BrowseFragment: BaseFragment() {
    override fun Menu.createOptions() {
        menuItem("Clear", R.drawable.ic_cake, showIcon = false).onClick {
            UserPrefs.recommendations puts emptyList()
        }
    }

    override fun ViewManager.createView() = verticalLayout {
        padding = dimen(R.dimen.text_content_margin)

        // Recommendations
        cardView().verticalLayout {
            linearLayout {
                gravity = Gravity.CENTER_VERTICAL

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
                layoutManager = LinearLayoutManager(context)
                adapter = MusicAdapter(
                    coroutineContext,
                    UserPrefs.recommendations.openSubscription()
                        .map { it.take(4) }
                )
            }
        }

        // History
        cardView().verticalLayout {
            linearLayout {
                textView("Recently Played").lparams {
                    weight = 1f
                }

                button(R.string.show_more).lparams {
                    gravity = Gravity.END
                }.onClick {
                    context.replaceMainContent(
                        SongsUI.History().createFragment()
                    )
                }
            }
            recyclerView {
                val history = UserPrefs.history.openSubscription()
                    .map { it.asReversed().take(4).map { it.song } }

                layoutManager = LinearLayoutManager(context)
                adapter = SongsAdapter(coroutineContext, history) { songs, pos ->
                    MusicService.offer(PlayerAction.PlaySongs(songs, pos))
                }
            }
        }
    }
}


class MusicAdapter(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<Recommendable>>
): RecyclerAdapter<Recommendable, RecyclerListItem>(parentContext, channel) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RecyclerListItem(parent, 3, false)

    override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
        val item = data[position]

//        holder.mainLine.text = item.id.displayName

        // TODO: Add the recommending user!
        holder.bindMusic(item)
    }
}

fun RecyclerListItem.bindMusic(
   item: Recommendable
) {
    val ctx = itemView.context
    when (item) {
        is Song -> {
            mainLine.text = item.id.displayName
            subLine.text = ctx.getString(
                R.string.song_by_artist,
                item.id.artist.displayName
            )
            coverImage?.image = null
            card.onClick {
                MusicService.offer(PlayerAction.PlaySongs(listOf(item)))
            }
        }
        is AlbumId -> {
            mainLine.text = item.displayName
            subLine.text = ctx.getString(
                R.string.album_by_artist,
                item.artist.displayName
            )
//                (holder.coverImage)?.let { cover ->
//                    launch {
//                        item.loadCover(Glide.with(cover)).first()
//                            ?.into(cover)
//                            ?: run {
//                                cover.imageResource = R.drawable.ic_default_album
//                            }
//                    }
//                }
            card.onClick {
                ctx.replaceMainContent(
                    AlbumDetailsUI(item).createFragment()
                )
            }
        }
        is ArtistId -> {
            mainLine.text = item.displayName
            subLine.text = ctx.getString(R.string.Artist)
            coverImage?.image = null
            card.onClick {
                ctx.replaceMainContent(
                    ArtistDetailsUI(item, ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE).createFragment()
                )
            }
        }
        is PlaylistId -> {
            mainLine.text = item.displayName
            subLine.text = ctx.getString(R.string.playlist_author, item.owner.displayName, "Playlist")
            card.onClick {
//                val playlist = withContext(Dispatchers.IO) {
//                    AbstractPlaylist.findChannel(item.uuid)
//                }
//                ctx.replaceMainContent(MixtapeDetailsUI.Resolved(item, playlist).createFragment())
            }
        }
    }
}