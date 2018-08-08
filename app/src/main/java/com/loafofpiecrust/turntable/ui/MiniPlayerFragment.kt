package com.loafofpiecrust.turntable.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.textStyle
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.experimental.channels.filterNotNull
import kotlinx.coroutines.experimental.channels.first
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class MiniPlayerFragment: BaseFragment() {

    override fun makeView(ui: ViewManager) = ui.linearLayout {
//        lparams {
//            height = dimen(R.dimen.mini_player_height)
//            width = matchParent
//            onApi(21) { elevation = 1f }
//        }
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

        imageButton(R.drawable.ic_play_arrow) {
            backgroundResource = R.drawable.round_selector_dark
            padding = dip(16)
            MusicService.instance.filterNotNull().switchMap {
                it.player.isPlaying
            }.consumeEach(UI) {
                imageResource = if (it) {
                    R.drawable.ic_pause
                } else R.drawable.ic_play_arrow
            }

            onClick {
                MusicService.enact(SyncService.Message.TogglePause())
            }
        }.lparams(height = matchParent)

        MusicService.instance.filterNotNull().switchMap {
            it.player.currentSong.filterNotNull()
        }.consumeEach(UI) { song ->
            mainLine.text = song.id.displayName
            subLine.text = song.id.artist.displayName

            song.loadCover(Glide.with(cover)).first()
                ?.listener(loadPalette(song.id.album) { palette, swatch ->
                    given(swatch?.titleTextColor) {
                        mainLine.textColor = it
                        subLine.textColor = it
                    }
                    backgroundColor = swatch?.rgb ?: Color.TRANSPARENT
                })?.into(cover) ?: run {
                cover.imageResource = R.drawable.ic_default_album
            }
        }
    }

}