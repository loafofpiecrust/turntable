package com.loafofpiecrust.turntable.song

import android.os.Parcelable
import android.view.ViewManager
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.util.fastScrollRecycler
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.map

@Parcelize
class AllSongsUI: SongsUI(), Parcelable {
    override val songs: BroadcastChannel<List<Song>> =
        Library.songsMap.openSubscription().map {
            it.values.sortedBy { it.id }
        }.broadcast(Channel.CONFLATED)

    override fun ViewManager.renderRecycler() =
        fastScrollRecycler {
            turntableStyle()
        }
}