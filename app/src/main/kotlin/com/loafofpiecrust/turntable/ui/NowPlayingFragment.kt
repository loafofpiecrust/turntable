package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
//import me.angrybyte.circularslider.CircularSlider
import android.graphics.Color
import android.support.constraint.ConstraintSet.PARENT_ID
import android.view.ViewManager
import android.widget.ImageButton
import android.widget.SeekBar
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.SyncDetailsDialog
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

open class NowPlayingFragment : BaseFragment() {

    override fun ViewManager.createView() = constraintLayout {
        lateinit var playButton: ImageButton

        MusicService.instance.switchMap {
            it.player.isPlaying
        }.consumeEach(UI) {
            if (it) {
                playButton.imageResource = R.drawable.ic_pause
                playButton.onClick {
                    MusicService.enact(SyncService.Message.Pause())
                }
            } else {
                playButton.imageResource = R.drawable.ic_play_arrow
                playButton.onClick {
                    MusicService.enact(SyncService.Message.Play())
                }
            }
        }

        async(UI) {
            MusicService.instance.switchMap {
                it.player.currentSong
            }.switchMap { song ->
                song?.loadCover(Glide.with(this@constraintLayout))?.map { req ->
                    song to req
                } ?: produceSingle(null to null)
            }.consumeEach { (song, req) ->
                if (song != null && req != null) {
                    req.listener(loadPalette(song.id.album) { palette, swatch ->
                        backgroundColor = swatch?.rgb ?: context.getColorCompat(R.color.background)
                        val c =
                            (palette?.mutedSwatch
                                ?: palette?.darkMutedSwatch
                                ?: palette?.darkVibrantSwatch)?.rgb
                                ?: Color.BLACK
                        playButton.backgroundColor = c
                    }).preload()
                } else {
                    // reset playButton color.
                }
            }
        }

        // Main player view
        id = R.id.container
        clipToPadding = false
        clipToOutline = false
        lparams(width = matchParent, height = matchParent)


        val songCarousel = frameLayout {
            clipToOutline = false
            clipToPadding = false
            id = R.id.albums
            fragment { PlayerAlbumCoverFragment() }
        }

        val seeker = seekBar {
            id = R.id.seekBar

            var isSeeking = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var value = 0
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        value = progress
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    MusicService.enact(SyncService.Message.SeekTo(value.toLong()))
                    isSeeking = false
                }
            })

            MusicService.instance.switchMap {
                it.player.bufferState
            }.consumeEach(UI) {
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
            SyncService.mode.consumeEach(UI) {
                imageResource = if (it is SyncService.Mode.None) {
                    onClick { v ->
                        // Open sync options: contact choice
                        v!!.context.selector("Sync options", listOf(
                            "Sync with Friend" to {
                                FriendPickerDialog(
                                    SyncService.Message.SyncRequest(),
                                    "Request Sync"
                                ).show(ctx)
                            }
                        ))()
                    }
                    R.drawable.ic_cast
                } else {
                    onClick { v ->
                        // Open sync details dialog!
                        SyncDetailsDialog().show(ctx)
                    }
                    R.drawable.ic_cast_connected
                }
            }
        }

        val prevBtn = iconButton(R.drawable.ic_skip_previous) {
            onClick {
                MusicService.enact(SyncService.Message.RelativePosition(-1))
            }
        }

        playButton = floatingActionButton {
            id = R.id.playing_icon
            imageResource = R.drawable.ic_play_arrow
            elevation = dimen(R.dimen.low_elevation).toFloat()
            isLongClickable = true
        }

        val nextBtn = iconButton(R.drawable.ic_skip_next) {
            onClick {
                MusicService.enact(SyncService.Message.RelativePosition(1))
            }
        }

        val shuffleBtn = iconButton(R.drawable.ic_shuffle) {
            tintResource = R.color.md_grey_700

//            MusicService.instance.combineLatest(
//                MusicService.instance.switchMap { it.player.queue }
//            ).consumeEach(UI) { (music, q) ->
//                val q = q.primary
//                if (q is RadioQueue) {
//                    // Option 1: Toggle playing only songs not in your library (or recent history?)
//                    // Option 2: Thumbs up that adds the current song to the radio seed.
//                    imageResource = R.drawable.ic_thumb_up
//                    setColorFilter(Color.DKGRAY)
//                    onClick {
//                        given(q.current) { q.addSeed(it) }
//                        onClick {}
//                        setColorFilter(Color.WHITE)
//                    }
//                } else {
//                    imageResource = R.drawable.ic_shuffle
//                    onClick {
//                        val mode = music.player.orderMode
//                        if (mode == MusicPlayer.OrderMode.SEQUENTIAL) {
//                            task { music.player.orderMode = MusicPlayer.OrderMode.SHUFFLE }
//                            setColorFilter(Color.WHITE)
//                        } else {
//                            task { music.player.orderMode = MusicPlayer.OrderMode.SEQUENTIAL }
//                            setColorFilter(Color.DKGRAY)
//                        }
//                    }
//                }
//            }
        }

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
}