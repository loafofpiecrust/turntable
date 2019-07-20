package com.loafofpiecrust.turntable.song

import android.content.Context
import android.os.Parcelable
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first

@Parcelize
class ShufflableSongsUI: UIComponent(), Parcelable {
    private val songsUI = AllSongsUI()

    // FIXME: This is a big mess...
    // The issue is that we're essentially recreating the whole fragment system
    // and how fragments are built to nest all for the sake of the instantiation syntax.
    // it might be a worthy sacrifice?
    override fun onDestroy() {
        super.onDestroy()
        songsUI.onDestroy()
    }

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.shuffle_all, R.drawable.ic_shuffle, showIcon = true).onClick {
            val songs = songsUI.songs.openSubscription().first()
            MusicService.offer(
                PlayerAction.PlaySongs(songs, mode = MusicPlayer.OrderMode.Shuffle())
            )
        }
    }

    override fun ViewContext.render() = songsUI.createView(this)
}