package com.loafofpiecrust.turntable.song

import android.os.Parcelable
import android.support.constraint.ConstraintLayout.LayoutParams.PARENT_ID
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createView
import com.loafofpiecrust.turntable.util.generateChildrenIds
import com.loafofpiecrust.turntable.util.size
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.first
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.dimen
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk27.coroutines.onClick

@Parcelize
class ShufflableSongsUI: UIComponent(), Parcelable {
    private val songsUI = SongsUI.All()

    // FIXME: This is a big mess...
    override fun onDestroy() {
        super.onDestroy()
        songsUI.onDestroy()
    }

    override fun ViewContext.render() = constraintLayout {
        val songs = songsUI.createView(this)

        val shuffleBtn = floatingActionButton {
            imageResource = R.drawable.ic_shuffle
            onClick {
                val songs = songsUI.songs.openSubscription().first()
                MusicService.offer(
                    PlayerAction.PlaySongs(songs, mode = MusicPlayer.OrderMode.SHUFFLE)
                )
            }
        }

        generateChildrenIds()
        applyConstraintSet {
            songs {
                connect(
                    TOP to TOP of PARENT_ID,
                    BOTTOM to BOTTOM of PARENT_ID,
                    START to START of PARENT_ID,
                    END to END of PARENT_ID
                )
                size = matchConstraint
            }
            shuffleBtn {
                val inset = dimen(R.dimen.text_content_margin)
                connect(
                    END to END of PARENT_ID margin inset,
                    BOTTOM to BOTTOM of PARENT_ID margin inset
                )
            }
        }
    }
}