package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
//import me.angrybyte.circularslider.CircularSlider
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.design.widget.FloatingActionButton
import android.view.View
import android.view.ViewManager
import android.widget.SeekBar
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.*
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onSeekBarChangeListener
import org.jetbrains.anko.support.v4.ctx

open class NowPlayingFragment : BaseFragment() {
    var playButton: FloatingActionButton by weak()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        MusicService.instance.switchMap {
            it?.player?.currentSong
        }.switchMap { song ->
            song?.loadCover(Glide.with(view))?.map { req ->
                song to req
            }
        }.consumeEachAsync { (song, req) ->
            if (req != null) {
                req.listener(loadPalette(song.id.album) { palette, swatch ->
                    view.backgroundColor = swatch?.rgb ?: context!!.getColorCompat(R.color.background)
                    val c =
                        (palette?.mutedSwatch
                            ?: palette?.darkMutedSwatch
                            ?: palette?.darkVibrantSwatch)?.rgb
                            ?: Color.BLACK
                    playButton.backgroundTintList = ColorStateList.valueOf(c)
                }).preload()
            } else {
                // reset playButton color.
                playButton.backgroundTintList = ColorStateList.valueOf(UserPrefs.primaryColor.value)
            }
        }
    }

    override fun ViewManager.createView() = constraintLayout {
        // Main player view
        id = R.id.container
        clipToPadding = false
        clipToOutline = false
//        lparams(width = matchParent, height = matchParent)


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
                    MusicService.enact(PlayerAction.SeekTo(value.toLong()))
                    isSeeking = false
                }
            }

            launch {
                MusicService.instance.switchMap {
                    it?.player?.bufferState
                }.consumeEach {
                    max = it.duration.toInt()

                    // Allows seeking while the song is playing without weird skipping.
                    if (!isSeeking) {
                        progress = it.position.toInt()
                    }
                    secondaryProgress = it.bufferedPosition.toInt()
                }
            }
        }

        val syncBtn = iconButton(R.drawable.ic_cast) {
            tintResource = R.color.md_white_1000
            SyncService.mode.consumeEachAsync {
                imageResource = if (it is SyncService.Mode.None) {
                    onClick { v ->
                        // Open sync options: contact choice
                        v!!.context.selector("Sync options", listOf(
                            "Sync with Friend" to {
                                FriendPickerDialog(
                                    Message.SyncRequest(),
                                    "Request Sync"
                                ).show(context)
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
                MusicService.enact(PlayerAction.RelativePosition(-1))
            }
        }

        playButton = floatingActionButton {
            id = R.id.playing_icon
            imageResource = R.drawable.ic_play_arrow
            elevation = dimen(R.dimen.low_elevation).toFloat()
            isLongClickable = true

            MusicService.instance.switchMap {
                it?.player?.isPlaying
            }.consumeEachAsync { playing ->
                if (playing) {
                    imageResource = R.drawable.ic_pause
                    onClick {
                        MusicService.enact(PlayerAction.Pause())
                    }
                } else {
                    imageResource = R.drawable.ic_play_arrow
                    onClick {
                        MusicService.enact(PlayerAction.Play())
                    }
                }
            }
        }


        val nextBtn = iconButton(R.drawable.ic_skip_next) {
            onClick {
                MusicService.enact(PlayerAction.RelativePosition(1))
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