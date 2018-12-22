package com.loafofpiecrust.turntable.song

import android.content.Context
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumDetailsUI
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.AddToPlaylistDialog
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.ui.universal.show
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick

fun Menu.songOptions(context: Context, song: Song) {
    menuItem("Go to Album").onClick {
        context.replaceMainContent(
            AlbumDetailsUI(song.id.album).createFragment()
        )
    }

    menuItem("Go to Artist").onClick {
        context.replaceMainContent(
            ArtistDetailsUI(song.id.artist).createFragment()
        )
    }

    menuItem(R.string.add_to_playlist).onClick {
        AddToPlaylistDialog(song).show(context)
    }

    menuItem(R.string.share).onClick {
        FriendPickerDialog(
            Message.Recommend(song),
            R.string.share
        ).show(context)
    }
}

fun Menu.queueOptions(context: Context, song: Song) {
    menuItem(R.string.queue_last).onClick {
        MusicService.offer(PlayerAction.Enqueue(
            listOf(song),
            MusicPlayer.EnqueueMode.NEXT
        ))
    }
    menuItem(R.string.queue_next).onClick {
        MusicService.offer(PlayerAction.Enqueue(
            listOf(song),
            MusicPlayer.EnqueueMode.IMMEDIATELY_NEXT
        ))
    }
}