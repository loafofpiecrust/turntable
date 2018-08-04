package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
//import me.angrybyte.circularslider.CircularSlider
import android.graphics.Color
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.Toolbar
import android.view.ViewManager
import android.widget.SeekBar
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.fragment
import com.loafofpiecrust.turntable.generateChildrenIds
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.radio.RadioQueue
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.channels.filterNotNull
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.sdk25.coroutines.onClick

open class NowPlayingFragment : BaseFragment() {

    // Ui elements
    lateinit var toolbar: Toolbar
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: FloatingActionButton

    override fun makeView(ui: ViewManager) = ui.constraintLayout {

        MusicService.instance.filterNotNull().consumeEach(UI) { music ->
            music.player.isPlaying.consumeEach(UI) {
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

            music.player.currentSong.switchMap { song ->
                song!!.loadCover(Glide.with(this)).map {
                    song to it
                }
            }.consumeEach(UI) { (song, req) ->
                req?.listener(loadPalette(song.id.album) { palette, swatch ->
                    backgroundColor = swatch?.rgb ?: context.resources.getColor(R.color.background)
                    val c =
                        (palette?.mutedSwatch
                            ?: palette?.darkMutedSwatch
                            ?: palette?.darkVibrantSwatch)?.rgb
                            ?: Color.BLACK
                    playButton.backgroundColor = c
                })?.preload()
            }
        }

        // Main player view
        id = R.id.container
        clipToPadding = false
        clipToOutline = false
        lparams(width = matchParent, height = matchParent)


        val songCarousel = frameLayout {
            id = R.id.mainContent
            clipToOutline = false
            clipToPadding = false
            fragment(fragmentManager, PlayerAlbumCoverFragment())
        }

        val seeker = seekBar {
            id = R.id.seekBar

            var isSeeking = false
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
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

            MusicService.instance.filterNotNull().switchMap {
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

        val syncBtn = imageButton(R.drawable.ic_cast) {
            backgroundResource = R.drawable.round_selector_dark
            padding = dip(16)
            setColorFilter(Color.WHITE)
            SyncService.mode.consumeEach(UI) {
                imageResource = if (it is SyncService.Mode.None) {
                    onClick {
                        // Open sync options: contact choice
                    }
                    R.drawable.ic_cast
                } else {
                    onClick {
                        // Open sync details dialog!
                    }
                    R.drawable.ic_cast_connected
                }
            }
        }

        val prevBtn = imageButton(R.drawable.ic_skip_previous) {
            backgroundResource = R.drawable.round_selector_dark
            padding = dip(16)
            onClick {
                MusicService.enact(SyncService.Message.RelativePosition(-1))
            }
        }

        playButton = floatingActionButton {
            id = R.id.playing_icon
            imageResource = R.drawable.ic_play_arrow
            elevation = dip(2).toFloat()
            isLongClickable = true
        }

        val nextBtn = imageButton(R.drawable.ic_skip_next) {
            backgroundResource = R.drawable.round_selector_dark
            padding = dip(16)
            onClick {
                MusicService.enact(SyncService.Message.RelativePosition(1))
            }
        }

        val shuffleBtn = imageButton(R.drawable.ic_shuffle) {
            backgroundResource = R.drawable.round_selector_dark
            setColorFilter(Color.DKGRAY)
            padding = dip(16)

            MusicService.instance.filterNotNull().consumeEach(UI) { music ->
                music.player.queue.consumeEach(UI) {
                    val q = it.primary
                    if (q is RadioQueue) {
                        // Option 1: Toggle playing only songs not in your library (or recent history?)
                        // Option 2: Thumbs up that adds the current song to the radio seed.
                        imageResource = R.drawable.ic_thumb_up
                        setColorFilter(Color.DKGRAY)
                        onClick {
                            given(q.current) { q.addSeed(it) }
                            onClick {}
                            setColorFilter(Color.WHITE)
                        }
                    } else {
                        imageResource = R.drawable.ic_shuffle
                        onClick {
                            val mode = music.player.orderMode
                            if (mode == MusicPlayer.OrderMode.SEQUENTIAL) {
                                task { music.player.orderMode = MusicPlayer.OrderMode.SHUFFLE }
                                setColorFilter(Color.WHITE)
                            } else {
                                task { music.player.orderMode = MusicPlayer.OrderMode.SEQUENTIAL }
                                setColorFilter(Color.DKGRAY)
                            }
                        }
                    }
                }
            }
        }

        generateChildrenIds()
        applyConstraintSet {
            songCarousel {
                connect(
                    TOP to TOP of this@constraintLayout,
                    START to START of this@constraintLayout,
                    END to END of this@constraintLayout
                )
                width = matchConstraint
                height = matchConstraint
                dimensionRation = "1:1"
            }
            seeker {
                connect(
                    TOP to BOTTOM of songCarousel margin dip(8),
                    START to START of this@constraintLayout,
                    END to END of this@constraintLayout
                )
                width = matchConstraint
            }
            syncBtn {
                connect(
                    START to START of this@constraintLayout margin dip(8),
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
                    START to START of this@constraintLayout,
                    END to END of this@constraintLayout,
                    TOP to BOTTOM of seeker margin dip(8)
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
                    END to END of this@constraintLayout margin dip(8),
                    TOP to TOP of playButton,
                    BOTTOM to BOTTOM of playButton
                )
            }
        }
    }
}