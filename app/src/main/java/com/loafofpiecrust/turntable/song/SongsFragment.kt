package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.ViewManager
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import org.jetbrains.anko.recyclerview.v7.recyclerView

abstract class SongsUI: UIComponent() {
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

    override fun ViewContext.render() = refreshableRecyclerView {
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
    }

    @Parcelize
    class All: SongsUI(), Parcelable {
        override val songs: BroadcastChannel<List<Song>> =
            Library.songsMap.openSubscription().map {
                it.values.sortedBy { it.id }
            }.broadcast(CONFLATED)

        override fun ViewManager.renderRecycler() = fastScrollRecycler {
            turntableStyle()
        }
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