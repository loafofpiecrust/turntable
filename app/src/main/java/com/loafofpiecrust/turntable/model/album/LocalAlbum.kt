package com.loafofpiecrust.turntable.model.album

import android.content.Context
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.ui.AlbumEditorActivityStarter


data class LocalAlbum(
    override val id: AlbumId,
    override val tracks: List<Song>
): Album {
    override val year: Int?
        get() = tracks.find { it.year != null }?.year

    override val type by lazy {
        when {
            id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
            tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
            tracks.size <= 7 -> Album.Type.EP
            id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
            else -> Album.Type.LP
        }
    }

    override fun optionsMenu(ctx: Context, menu: Menu) {
        super.optionsMenu(ctx, menu)

        menu.menuItem(R.string.album_edit_metadata).onClick {
            AlbumEditorActivityStarter.start(ctx, id)
        }
    }
}