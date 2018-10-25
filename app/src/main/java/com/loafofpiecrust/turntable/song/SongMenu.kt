package com.loafofpiecrust.turntable.song

import android.content.Context
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumDetailsUI
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.playlist.PlaylistPicker
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.ui.createFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.showDialog
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
        PlaylistPicker(song).showDialog(context)
    }

    menuItem(R.string.share).onClick {
        FriendPickerDialog(
            Message.Recommend(song),
            context.getString(R.string.share)
        ).show(context)
    }
}