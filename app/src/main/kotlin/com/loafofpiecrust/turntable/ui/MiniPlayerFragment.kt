package com.loafofpiecrust.turntable.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.util.iconButton
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.util.textStyle
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.experimental.channels.filterNotNull
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onClick

class MiniPlayerFragment: BaseFragment() {

    override fun ViewManager.createView() = linearLayout {
        gravity = Gravity.CENTER_VERTICAL
        backgroundColor = Color.TRANSPARENT

        val cover = imageView {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }.lparams(height = matchParent, width = dimen(R.dimen.mini_player_height))

        // Song info
        lateinit var mainLine: TextView
        lateinit var subLine: TextView
        verticalLayout {
            mainLine = textView {
                maxLines = 2
                textStyle = Typeface.BOLD
//                        textSizeDimen = R.dimen.title_text_size
            }
            subLine = textView {
                lines = 1
            }
        }.lparams {
            leftMargin = dimen(R.dimen.text_content_margin)
            weight = 1f
        }

        iconButton(R.drawable.ic_play_arrow) {
            MusicService.instance.switchMap {
                it.player.isPlaying
            }.map {
                if (it) {
                    R.drawable.ic_pause
                } else R.drawable.ic_play_arrow
            }.bindTo(::imageResource)

            onClick {
                MusicService.enact(SyncService.Message.TogglePause())
            }
        }.lparams {
            gravity = Gravity.CENTER_VERTICAL
        }


        MusicService.instance.switchMap {
            it.player.currentSong.filterNotNull()
        }.consumeEachAsync { song ->
            mainLine.text = song.id.displayName
            subLine.text = song.id.artist.displayName

            song.loadCover(Glide.with(cover)).first()
                ?.listener(loadPalette(song.id.album, arrayOf(mainLine, subLine, this)))?.into(cover) ?: run {
                    cover.imageResource = R.drawable.ic_default_album
                }
        }
    }

}