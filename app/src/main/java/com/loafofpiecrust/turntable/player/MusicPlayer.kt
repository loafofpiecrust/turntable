package com.loafofpiecrust.turntable.player

import android.content.Context
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.queue.Queue
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.model.queue.indexWithinUpNext
import com.loafofpiecrust.turntable.model.song.HistoryEntry
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.util.with
import com.loafofpiecrust.turntable.util.without
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.produce
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import kotlin.coroutines.CoroutineContext

class MusicPlayer(ctx: Context): Player.EventListener, AnkoLogger, CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob()

    enum class EnqueueMode {
        NEXT, // Adds to the end of an "Up Next" section of the queue just after the current song.
        IMMEDIATELY_NEXT, // Will play _immediately_ after the current song
    }


    private val userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:58.0) Gecko/20100101 Firefox/58.0"
    private val bandwidthMeter = DefaultBandwidthMeter()
    private val sourceFactory by lazy {
        DefaultDataSourceFactory(
            ctx,
            bandwidthMeter,
            DefaultHttpDataSourceFactory(
                userAgent,
                bandwidthMeter,
                4000, // timeouts
                4000,
                false
            )
        )
    }
    private val extractorsFactory = DefaultExtractorsFactory()
    private val player = ExoPlayerFactory.newSimpleInstance(
        DefaultRenderersFactory(ctx),
        DefaultTrackSelector(AdaptiveTrackSelection.Factory(bandwidthMeter)),
        DefaultLoadControl()
    ).apply {
        addListener(this@MusicPlayer)
    }


    enum class OrderMode {
        SEQUENTIAL,
        SHUFFLE,
    }
    private var _orderMode: OrderMode = OrderMode.SEQUENTIAL
    var orderMode: OrderMode
        get() = _orderMode
        set(value) {
            if (_orderMode == value) return

            if (value == OrderMode.SEQUENTIAL) {
                // shuffled => sequential
                val bq = nonShuffledQueue
                if (bq != null) {
                    // Find the current song in the backup queue and play from there.
                    _queue putsMapped { it.restoreSequential(bq) }
                    nonShuffledQueue = null
                }
            } else {
                // sequential => shuffled
                val q = _queue.value
                nonShuffledQueue = q.primary
                _queue putsMapped { it.shuffled() }
            }
            _orderMode = value
        }


    private var nonShuffledQueue: Queue? = null
    private val _queue = UserPrefs.queue
    val queue: ReceiveChannel<CombinedQueue> get() = _queue.openSubscription()

    val currentSong: ReceiveChannel<Song?> get() = queue.map { it.current }


    data class BufferState(
        val duration: Long,
        val position: Long,
        val bufferedPosition: Long
    )
    val currentBufferState get() = BufferState(
        player.duration,
        player.currentPosition,
        player.bufferedPosition
    )
    val bufferState: ReceiveChannel<BufferState> get() = produce {
        while (true) {
            try {
                if (player.currentTimeline != null && player.currentTimeline.windowCount > 0) {
                    send(currentBufferState)
                }
            } finally {
            }
            delay(350)
        }
    }


    private val _isPlaying: ConflatedBroadcastChannel<Boolean> = ConflatedBroadcastChannel(false)
    val isPlaying get() = _isPlaying.openSubscription()
    var isStreaming: Boolean = true
        private set

    var shouldAutoplay: Boolean = false
        private set


    val hasNext: Boolean get() = _queue.value.peek() != null
    val hasPrev: Boolean get() = _queue.value.position > 0


    // Re-exported from ExoPlayer
    var volume: Float
        get() = player.volume
        set(value) { player.volume = value }


    fun release() {
        player.release()
        coroutineContext.cancel()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {

    }

    private var errorCount = 0
    override fun onPlayerError(error: ExoPlaybackException) {
        // This means the current song couldn't be loaded.
        // In this case, delete the DB entry for the current song.
        // Then, try again to play it.
        error(error.type, error)

        // if the error is a SOURCE error 404 or 403,
        // clear streams and try again
        // if that fails the 2nd time, skip to the next track in the MediaSource.
        if (error.type == ExoPlaybackException.TYPE_SOURCE) {
            player.stop(true)
            // to retry, we have to rebuild the MediaSource
            playNext()
            prepareSource()

//            errorCount++
//            if (errorCount < 2) async(BG_POOL) {
//                OnlineSearchService.instance.reloadSongStreams(_queue.value.current!!.uuid)
//
//                // TODO: Ensure this works vs resetting the media source entirely.
//                player.seekToDefaultPosition(player.currentWindowIndex)
//            } else {
//                errorCount = 0
//                playNext()
//            }
        }

//        if (currentSong.value.remote != null) {
//        errorCount++
//        error?.printStackTrace()
//
//        if (errorCount < 2) {
//            given(_queue.value.current) { song: Song ->
//                task {
//                    OnlineSearchService.instance.reloadSongStreams(song.uuid)
//                }.success {
//                    if (prepareSong(song)) {
//                        desynced { play() }
//                    } else {
//                        errorCount = 0
//                        player.stop()
//                        playNext()
//                    }
//                }
//            }
//        } else {
//            errorCount = 0
//            playNext()
//        }
//        }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        debug { "player state: playing=$playWhenReady, state=$playbackState" }
        when (playbackState) {
            Player.STATE_ENDED -> if (playWhenReady) {
//                desynced { // Don't sync this, other users will have the same listener.
//                    // TODO: Extend ExoPlayer types to do gapless playback.
////                    player.stop() // Prevent continued skipping.
//                    playNext().await()
//                }
                player.stop()
            }
            Player.STATE_IDLE -> pause()
            Player.STATE_READY -> {
                errorCount = 0
            }
        }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
    }

    // Called when ExoPlayer changes tracks (mapped to windows)
    override fun onPositionDiscontinuity(reason: Int) {
        if (reason == ExoPlayer.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
            playNext()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
    }

    override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED) {
            val q = _queue.value
            if (player.currentWindowIndex != q.position && timeline.windowCount > 0) {

//                player.seekToDefaultPosition(min(timeline.windowCount - 1, q.position))
            }
        }
    }

    override fun onSeekProcessed() {
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    }


    private var isPrepared = false
    private var prepareJob: Deferred<Boolean>? = null
//    private suspend fun prepareSong(song: Song): Boolean = suspendCoroutine { cont ->
//        isPrepared = false
//        isStreaming = false
//        player.prepare(StreamMediaSource(song, sourceFactory, extractorsFactory) { loaded ->
//            isPrepared = loaded
//            cont.resume(loaded)
//        })


    fun stop() {
        pause()
        player.stop()
    }

    private var mediaSource: ConcatenatingMediaSource? = null

    fun playSongs(songs: List<Song>, position: Int = 0, mode: OrderMode = OrderMode.SEQUENTIAL) {
        shouldAutoplay = true

        when (mode) {
            OrderMode.SEQUENTIAL -> {
                val primary = StaticQueue(songs, position)
                _queue putsMapped { q ->
                    val next = if (q.isPlayingNext) q.nextUp.drop(1) else q.nextUp
                    CombinedQueue(primary, next)
                }
            }
            OrderMode.SHUFFLE -> {
                nonShuffledQueue = StaticQueue(songs, position)
                val shuffledSongs = if (position > 0) {
                    listOf(songs[position]) + songs.without(position).shuffled()
                } else songs.shuffled()
                _queue putsMapped { q ->
                    val next = if (q.isPlayingNext) q.nextUp.drop(1) else q.nextUp
                    CombinedQueue(StaticQueue(shuffledSongs, 0), next)
                }
            }
        }
        prepareSource()
    }

    private fun prepareSource() {
        val q = _queue.value
        player.stop(true)
        mediaSource = ConcatenatingMediaSource().apply {
            addMediaSources(q.list.mapIndexed { index, song ->
                val cb: ((Boolean) -> Unit)? = if (index == q.position) {
                    { loaded ->
                        isPrepared = loaded
                        if (loaded) play()
                    }
                } else null
                StreamMediaSource(song, sourceFactory, extractorsFactory, cb)
            })
        }
        player.seekToDefaultPosition(q.position)
        player.prepare(mediaSource, false, true)
        player.playWhenReady = true
        player.shuffleModeEnabled = orderMode == OrderMode.SHUFFLE
    }

    fun clearQueue() {
        nonShuffledQueue = null
        _queue puts CombinedQueue(StaticQueue(listOf(), 0), listOf())
        mediaSource?.clear()
        player.stop()
    }

    /// By default enqueues at the end, passing 1 will queue next.
    fun enqueue(songs: List<Song>, mode: EnqueueMode = EnqueueMode.NEXT) {
        if (mediaSource == null) {
            mediaSource = ConcatenatingMediaSource()
            player.prepare(mediaSource)
            player.playWhenReady = false
        }
        val q = _queue.value
        when (mode) {
            EnqueueMode.IMMEDIATELY_NEXT -> _queue puts run {
                mediaSource?.addMediaSources(
                    q.position + 1,
                    songs.map { StreamMediaSource(it, sourceFactory, extractorsFactory) }
                )
                val pos = if (q.isPlayingNext) 1 else 0
                q.copy(nextUp = q.nextUp.with(songs, pos))
            }
            EnqueueMode.NEXT -> _queue puts run {
                mediaSource?.addMediaSources(
                    q.primary.position + q.nextUp.size + 1,
                    songs.map { StreamMediaSource(it, sourceFactory, extractorsFactory) }
                )
                q.copy(nextUp = q.nextUp + songs)
            }
        }
    }

    fun enqueue(song: Song, mode: EnqueueMode) = run {
        enqueue(listOf(song), mode)
    }

    fun removeFromQueue(position: Int) {
        // TODO: Start with removing from nextUp, then from anywhere else if from StaticQueue (new interface method?)

        if (_queue.value.indexWithinUpNext(position)) {
            _queue putsMapped { q ->
                val diff = if (q.isPlayingNext) 0 else 1
                q.copy(nextUp = q.nextUp.without(position - q.position - diff))
            }
        }
    }

    fun playNext() {
        addCurrentToHistory()
        val q = _queue.value.toNext()
        _queue puts q as CombinedQueue

        if (q.position != player.currentWindowIndex) {
            player.seekToDefaultPosition(q.position)
        }
    }

    fun playPrevious() {
        addCurrentToHistory()
        val q = _queue.value.toPrev()
        _queue puts q as CombinedQueue
//        q
//    }.then(UI) { q ->
        if (q.position != player.currentWindowIndex) {
            player.seekToDefaultPosition(q.position)
        }
    }

    fun shiftQueuePosition(pos: Int) {
        val q = _queue.value
        when (pos) {
            q.position - 1 -> if (pos >= 0) playPrevious()
            q.position + 1 -> if (pos < q.list.size) playNext()
            else -> {
                val q = _queue.value.shiftedPosition(pos)
                _queue puts q as CombinedQueue

                if (player.currentWindowIndex != q.position) {
                    player.seekToDefaultPosition(q.position)
                }
            }
        }
    }

    fun shiftQueuePositionRelative(diff: Int) {
        val q = _queue.value
        when (diff) {
            -1 -> if (q.position > 0) playPrevious()
            1 -> if (q.position + 1 < q.list.size) playNext()
            else -> {
                val q = _queue.value.shiftedPosition(q.position + diff)
                _queue puts q as CombinedQueue

                if (player.currentWindowIndex != q.position) {
                    player.seekToDefaultPosition(q.position)
                }
            }
        }
    }

    fun shiftQueueItem(from: Int, to: Int) {
        _queue putsMapped { q ->
            q.shifted(from, to) as CombinedQueue
        }

//        launch(UI) {
            mediaSource?.moveMediaSource(from, to)
//        }
    }

    fun replaceQueue(q: Queue) {
        _queue putsMapped { prev ->
            if (prev.isPlayingNext) {
                CombinedQueue(q, prev.nextUp.drop(1))
            } else {
                CombinedQueue(q, prev.nextUp)
            }
        }
    }


    /**
     * @return true if the action succeeded
     */
    fun play(): Boolean {
        if (!isPrepared) return false

        val q = _queue.value
        if (q.current != null) {
            lastResumeTime = player.currentPosition
            player.playWhenReady = true
            _isPlaying puts true
            shouldAutoplay = true
        }
        return q.current != null
    }

    /**
     * @return true if the action succeeded
     */
    fun pause(): Boolean {
        if (!isPrepared) return false
        return temporaryPause()
    }

    fun temporaryPause(): Boolean {
        val alreadyPlaying = _isPlaying.value && player.playWhenReady
        if (alreadyPlaying) {
            totalListenedTime += player.currentPosition - lastResumeTime
            player.playWhenReady = false
            _isPlaying puts false
        }
        return alreadyPlaying
    }

    //    @Synchronized
    fun togglePause(): Boolean {
        return if (player.playWhenReady) {
            pause()
        } else {
            play()
        }
    }


    fun seekTo(timeMs: Long) {
        totalListenedTime += player.currentPosition - lastResumeTime
        player.seekTo(timeMs)
        lastResumeTime = player.currentPosition
    }

    private var lastResumeTime = 0L
    private var totalListenedTime = 0L

    private fun addCurrentToHistory() {
        val now = System.currentTimeMillis()
        val total = if (_isPlaying.value) {
            totalListenedTime + (now - lastResumeTime)
        } else totalListenedTime
        val percent = total.toDouble() / player.duration
        // TODO: Decide on percent threshold to count as a "full" listen. Is half the song enough?
        if (percent > 0.5) {
            // TODO: Add timestamp and percent to history entries
            UserPrefs.history appends HistoryEntry(_queue.value.current!!)
        }
        totalListenedTime = 0
    }
}
