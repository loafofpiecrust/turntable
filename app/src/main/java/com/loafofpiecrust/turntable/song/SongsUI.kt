package com.loafofpiecrust.turntable.song

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.emptyContentView
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.recyclerview.v7.recyclerView

abstract class SongsUI(
    val makeEmptyView: ViewManager.() -> View = {
        emptyContentView(
            R.string.songs_empty,
            0
        )
    }
): UIComponent() {
    abstract val songs: BroadcastChannel<List<Song>>

    override fun Menu.prepareOptions(context: Context) {
        if (BuildConfig.DEBUG) {
            menuItem(R.string.show_history).onClick {
                context.replaceMainContent(
                    SongsUI.History().createFragment()
                )
            }
        }
    }

    protected open fun makeAdapter(): RecyclerView.Adapter<*> {
        return SongsAdapter(coroutineContext, songs.openSubscription()) { songs, idx ->
            MusicService.offer(PlayerAction.PlaySongs(songs, idx))
        }
    }

    protected open fun ViewManager.renderRecycler(): RecyclerView =
        recyclerView {
            turntableStyle()
        }

    override fun ViewContext.render(): View = refreshableRecyclerView {
        channel = songs.openSubscription()

        contents {
            renderRecycler().apply {
                adapter = makeAdapter()
                clipToPadding = false

                val linear = LinearLayoutManager(context)
                layoutManager = linear

                addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                    setDrawable(context.getDrawable(R.drawable.song_divider))
                })
            }
        }

        emptyState { makeEmptyView() }
    }

    @Parcelize
    class History: SongsUI(), Parcelable {
        override val songs = UserPrefs.history.openSubscription()
            .map { it.map { it.song } }
            .broadcast(CONFLATED)
    }

    class Custom(
        override val songs: BroadcastChannel<List<Song>>
    ): SongsUI()
}