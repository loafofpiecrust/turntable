package com.loafofpiecrust.turntable.song

import android.content.Context
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.playlist.PlaylistPicker
import com.loafofpiecrust.turntable.ui.showDialog
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick

fun Menu.songOptions(context: Context, song: Song) {
    menuItem(R.string.add_to_playlist).onClick {
        PlaylistPicker(song).showDialog(context)
    }
}