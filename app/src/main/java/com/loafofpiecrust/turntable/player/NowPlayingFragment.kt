package com.loafofpiecrust.turntable.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.FloatingActionButton
import android.view.View
import android.view.ViewManager
import android.widget.ImageButton
import android.widget.SeekBar
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.queue.RadioQueue
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.selector
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.sync.SyncDetailsDialog
import com.loafofpiecrust.turntable.sync.SyncSession
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.universal.show
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onSeekBarChangeListener

open class NowPlayingFragment : BaseFragment() {
    override fun ViewManager.createView() = constraintLayout {
        backgroundColor = colorAttr(android.R.attr.windowBackground)

        topPadding = dimen(R.dimen.statusbar_height)

        // Main player view
        id = R.id.container
        clipToPadding = false
        clipToOutline = false


        val songCarousel = frameLayout {
            clipToOutline = false
            clipToPadding = false
            clipChildren = false
            id = R.id.albums
            fragment { PlayerAlbumCoverFragment() }
        }

        val seeker = seekBar {
            id = R.id.seekBar

            var isSeeking = false
            onSeekBarChangeListener {
                var value = 0
                onProgressChanged { _, progress, fromUser ->
                    if (fromUser) {
                        value = progress
                    }
                }
                onStartTrackingTouch {
                    isSeeking = true
                }
                onStopTrackingTouch {
                    MusicService.offer(PlayerAction.SeekTo(value.toLong()))
                    isSeeking = false
                }
            }

            MusicService.player.switchMap {
                it?.bufferState
            }.consumeEachAsync {
                max = it.duration.toInt()

                // Allows seeking while the song is playing without weird skipping.
                if (!isSeeking) {
                    progress = it.position.toInt()
                }
                secondaryProgress = it.bufferedPosition.toInt()
            }
        }

        val syncBtn = iconButton(R.drawable.ic_cast) {
            tintResource = R.color.md_white_1000
            SyncSession.mode.consumeEachAsync {
                imageResource = if (it is Sync.Mode.None) {
                    onClick { v ->
                        // Open sync options: contact choice
                        v!!.context.selector("Sync options", listOf(
                            "Sync with Friend" to {
                                val song = runBlocking {
                                    MusicService.player.firstOrNull()?.currentSong?.firstOrNull()
                                }
                                FriendPickerDialog(
                                    Sync.Request(song),
                                    R.string.friend_request_sync
                                ).show(v.context)
                            }
                        )).invoke()
                    }
                    R.drawable.ic_cast
                } else {
                    onClick { v ->
                        // Open sync details dialog!
                        SyncDetailsDialog().show(v!!.context)
                    }
                    R.drawable.ic_cast_connected
                }
            }
        }

        val prevBtn = iconButton(R.drawable.ic_skip_previous) {
            tintResource = R.color.md_white_1000
            onClick {
                MusicService.offer(PlayerAction.RelativePosition(-1))
            }
        }

        val playButton = floatingActionButton {
            id = R.id.playing_icon
            imageResource = R.drawable.ic_play_arrow
//            elevation = dimen(R.dimen.medium_elevation).toFloat()
            isLongClickable = true

            MusicService.player.switchMap {
                it?.isPlaying
            }.consumeEachAsync { playing ->
                if (playing) {
                    imageResource = R.drawable.ic_pause
                    onClick {
                        MusicService.offer(PlayerAction.Pause)
                    }
                } else {
                    imageResource = R.drawable.ic_play_arrow
                    onClick {
                        MusicService.offer(PlayerAction.Play)
                    }
                }
            }
        }

        val nextBtn = iconButton(R.drawable.ic_skip_next) {
            tintResource = R.color.md_white_1000
            onClick {
                MusicService.offer(PlayerAction.RelativePosition(1))
            }
        }

        val shuffleBtn = iconButton(R.drawable.ic_shuffle) {
            combineLatest(
                MusicService.instance,
                MusicService.instance.switchMap { it?.player?.queue }
            ).consumeEachAsync { (music, q) ->
                val q = q.primary
                if (q is RadioQueue) {
                    // Option 1: Toggle playing only songs not in your library (or recent history?)
                    // Option 2: Thumbs up that adds the current song to the radio seed.
                    imageResource = R.drawable.ic_thumb_up
                    setColorFilter(Color.DKGRAY)
                    onClick {
                        q.current?.let { q.addSeed(it) }
                        onClick {}
                        tint = Color.WHITE
                    }
                } else when (music?.player?.orderMode) {
                    is MusicPlayer.OrderMode.Sequential -> {
                        onClick {
                            MusicService.offer(PlayerAction.ChangeOrderMode(
                                MusicPlayer.OrderMode.Shuffle()
                            ))
                        }
                        imageResource = R.drawable.ic_repeat
                    }
                    is MusicPlayer.OrderMode.Shuffle -> {
                        onClick {
                            MusicService.offer(PlayerAction.ChangeOrderMode(
                                MusicPlayer.OrderMode.Sequential
                            ))
                        }
                        imageResource = R.drawable.ic_shuffle
                    }
                }
            }
        }

        bindBackgroundColor(this, seeker, playButton, arrayOf(syncBtn, prevBtn, nextBtn, shuffleBtn))

        generateChildrenIds()
        applyConstraintSet {
            val gap = dimen(R.dimen.text_lines_gap)

            songCarousel {
                connect(
                    TOP to TOP of PARENT_ID,
                    START to START of PARENT_ID,
                    END to END of PARENT_ID
                )
                size = matchConstraint
                dimensionRation = "1:1"
            }
            seeker {
                connect(
                    TOP to BOTTOM of songCarousel margin gap,
                    START to START of PARENT_ID,
                    END to END of PARENT_ID
                )
                width = matchConstraint
            }
            syncBtn {
                connect(
                    START to START of PARENT_ID margin gap,
                    TOP to TOP of playButton,
                    BOTTOM to BOTTOM of playButton
                )
            }
            prevBtn {
                connect(
                    END to START of playButton,
                    TOP to TOP of playButton,
                    BOTTOM to BOTTOM of playButton
                )
            }
            playButton {
                connect(
                    START to START of PARENT_ID,
                    END to END of PARENT_ID,
                    TOP to BOTTOM of seeker margin gap
                )
            }
            nextBtn {
                connect(
                    START to END of playButton,
                    TOP to TOP of playButton,
                    BOTTOM to BOTTOM of playButton
                )
            }
            shuffleBtn {
                connect(
                    END to END of PARENT_ID margin gap,
                    TOP to TOP of playButton,
                    BOTTOM to BOTTOM of playButton
                )
            }
        }
    }

    private fun bindBackgroundColor(
        root: View,
        seeker: SeekBar,
        playButton: FloatingActionButton,
        otherButtons: Array<ImageButton>
    ) {
        MusicService.currentSongColor.consumeEachAsync { color ->
            if (color != null) {
                val (palette, swatch) = color
                val darkSwatch = palette.mutedSwatch
                    ?: palette.darkMutedSwatch
                    ?: palette.darkVibrantSwatch

                val mainColor = swatch.rgb

                val c = darkSwatch?.rgb?.takeIf { it != mainColor }
                    ?: swatch.titleTextColor.withAlpha(255)
                    ?: Color.BLACK

                val colorState = ColorStateList.valueOf(c)
                playButton.backgroundTintList = colorState
                playButton.tint = c.contrastColor
                seeker.thumbTintList = colorState
                seeker.progressTintList = colorState
                darkSwatch?.rgb?.let { c ->
                    seeker.secondaryProgressTintList = ColorStateList.valueOf(c)
                }
                root.backgroundColor = mainColor

                for (btn in otherButtons) {
                    btn.tint = c
                }
            } else {
                // reset playButton color.
                playButton.backgroundTintList = ColorStateList.valueOf(UserPrefs.primaryColor.value)
            }
        }
    }
}