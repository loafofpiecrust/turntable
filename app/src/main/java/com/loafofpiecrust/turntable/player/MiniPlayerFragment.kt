package com.loafofpiecrust.turntable.player

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.iconButton
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.util.textStyle
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onClick

class MiniPlayerFragment: BaseFragment() {
    override fun ViewManager.createView() = linearLayout {
        gravity = Gravity.CENTER_VERTICAL

        val cover = imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }.lparams(height = matchParent, width = dimen(R.dimen.mini_player_height))

        // Song info
        lateinit var mainLine: TextView
        lateinit var subLine: TextView
        verticalLayout {
            mainLine = textView {
                maxLines = 2
                textStyle = Typeface.BOLD
                textSizeDimen = R.dimen.subtitle_text_size
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
                it?.player?.isPlaying
            }.consumeEachAsync {
                imageResource = if (it) {
                    R.drawable.ic_pause
                } else R.drawable.ic_play_arrow
            }

            onClick {
                MusicService.offer(PlayerAction.TogglePause)
            }
        }.lparams {
            gravity = Gravity.CENTER_VERTICAL
        }

        MusicService.currentSongColor.consumeEachAsync { color ->
            if (color != null) {
                val (palette, swatch) = color
                mainLine.textColor = swatch.titleTextColor
                subLine.textColor = swatch.titleTextColor
                this@linearLayout.backgroundColor = swatch.rgb
            }
        }

        MusicService.instance.switchMap {
            it?.player?.queue?.map { it.current }?.filterNotNull()
        }.switchMap { song ->
            mainLine.text = song.id.displayName
            subLine.text = song.id.artist.displayName

            song.loadCover(Glide.with(this@MiniPlayerFragment)).map {
                song to it
            }
        }.consumeEachAsync { (song, req) ->
            req?.into(cover)
        }
    }
}