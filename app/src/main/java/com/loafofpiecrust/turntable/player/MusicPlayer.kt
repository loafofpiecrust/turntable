package com.loafofpiecrust.turntable.player

import android.content.Context
import com.github.ajalt.timberkt.Timber
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.model.queue.*
import com.loafofpiecrust.turntable.model.song.HistoryEntry
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.serialize.page
import com.loafofpiecrust.turntable.util.startWith
import com.loafofpiecrust.turntable.util.with
import com.loafofpiecrust.turntable.util.without
import io.paperdb.Paper
import kotlinx.collections.immutable.immutableListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.produce
import okhttp3.OkHttpClient
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

class MusicPlayer(ctx: Context): Player.EventListener, CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob()

    enum class EnqueueMode {
        /**
         * Adds to the end of an "Up Next" section of the queue just after the current song.
         */
        NEXT,
        /**
         * Will play _immediately_ after the current song
         */
        IMMEDIATELY_NEXT,
    }

    private val bandwidthMeter = DefaultBandwidthMeter()
    private val mediaSourceFactory by lazy {
        ExtractorMediaSource.Factory(
            DefaultDataSourceFactory(
                ctx,
                bandwidthMeter,
                OkHttpDataSourceFactory(
                    OkHttpClient(),
                    USER_AGENT
                )
            )
        ).setExtractorsFactory(DefaultExtractorsFactory())
    }

    val player = ExoPlayerFactory.newSimpleInstance(
        ctx,
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
    private val _queue by Paper.page("queue") {
        CombinedQueue(StaticQueue(listOf(), 0), listOf())
    }
    val queue: ReceiveChannel<CombinedQueue> get() = _queue.openSubscription()

    val currentSong: ReceiveChannel<Song?>
        get() = queue.map { it.current }.startWith(null)


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
    val bufferState: ReceiveChannel<BufferState> get() = produce(Dispatchers.Main) {
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


    private val _isPlaying: ConflatedBroadcastChannel<Boolean> =
        ConflatedBroadcastChannel(false)
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


    init {
        // Handle case of restoring state after process death.
        if (_queue.valueOrNull?.isEmpty() == false) {
            prepareSource()
        }
    }

    fun release() {
        player.release()
        coroutineContext.cancelChildren()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
    }

    override fun onTracksChanged(
        trackGroups: TrackGroupArray?,
        trackSelections: TrackSelectionArray?
    ) {
    }

    private var errorCount = 0
    override fun onPlayerError(error: ExoPlaybackException) {
        // This means the current song couldn't be loaded.
        // In this case, delete the DB entry for the current song.
        // Then, try again to play it.
        Timber.e(error) { error.type.toString() }

        // if the error is a SOURCE error 404 or 403,
        // clear streams and try again
        // if that fails the 2nd time, skip to the next track in the MediaSource.
        if (error.type == ExoPlaybackException.TYPE_SOURCE) {
            App.instance.toast("Song not available to stream")
            if (hasNext) {
                // to retry, we have to rebuild the MediaSource
                playNext()
                prepareSource()
            } else {
                temporaryPause()
            }
        } else {
            App.instance.toast("Dubious player error (type ${error.type})")
        }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        Timber.d { "player state: playing=$playWhenReady, state=$playbackState" }
        _isPlaying.offer(playWhenReady)
        when (playbackState) {
            Player.STATE_ENDED -> if (playWhenReady) {
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
        Timber.d { "position discontinuity, reason = $reason" }
        if (reason == ExoPlayer.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
            shiftQueuePosition(player.currentWindowIndex, false)
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
    }

    override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
        Timber.d { "timeline changed (reason: $reason) to $timeline" }
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
//                val q = _queue.valueOrNull
//                val next = if (q != null) {
//                    if (q.isPlayingNext) q.nextUp.drop(1) else q.nextUp
//                } else listOf()

                _queue.offer(CombinedQueue(primary, immutableListOf()))
            }
            OrderMode.SHUFFLE -> {
                nonShuffledQueue = StaticQueue(songs, position)
                val shuffledSongs = if (position > 0) {
                    listOf(songs[position]) + songs.without(position).shuffled()
                } else songs.shuffled()

                val q = _queue.valueOrNull
                val next = if (q != null) {
                    if (q.isPlayingNext) q.nextUp.drop(1) else q.nextUp
                } else listOf()

                _queue.offer(CombinedQueue(
                    StaticQueue(shuffledSongs, 0),
                    next
                ))
            }
        }
        prepareSource()
    }

    private fun prepareSource() {
        val q = _queue.value
        player.stop(true)
        mediaSource = ConcatenatingMediaSource().apply {
            addMediaSources(q.list.mapIndexed { index, song ->
                val cb: ((Boolean) -> Unit)? = { loaded ->
                    isPrepared = loaded
                    if (loaded) launch(Dispatchers.Main) {
                        play()
                    }
                }
                StreamMediaSource(song, mediaSourceFactory, cb)
            })
        }
        player.seekToDefaultPosition(q.position)
        player.prepare(mediaSource, false, true)
        player.playWhenReady = true
        player.shuffleModeEnabled = orderMode == OrderMode.SHUFFLE
    }

    fun clearQueue() {
        nonShuffledQueue = null
        _queue puts CombinedQueue(StaticQueue(listOf(), 0), immutableListOf())
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

        when (mode) {
            EnqueueMode.IMMEDIATELY_NEXT -> {
                val q = _queue.value
                mediaSource!!.addMediaSources(
                    player.currentWindowIndex + 1,
                    songs.map { StreamMediaSource(it, mediaSourceFactory) }
                )

                val pos = if (q.isPlayingNext) 1 else 0
                _queue puts q.copy(nextUp = q.nextUp.with(songs, pos))
            }
            EnqueueMode.NEXT -> _queue putsMapped { q ->
                mediaSource!!.addMediaSources(
                    q.primary.position + q.nextUp.size + 1,
                    songs.map { StreamMediaSource(it, mediaSourceFactory) }
                )

                q.copy(nextUp = q.nextUp + songs)
            }
        }
    }

    fun removeFromQueue(position: Int) {
        // TODO: Start with removing from nextUp, then from anywhere else if from StaticQueue (new interface method?)

        val q = _queue.value
        if (q.indexWithinUpNext(position)) {
            val diff = if (q.isPlayingNext) 0 else 1
            val indexToRemove = position - q.position - diff
            _queue puts q.copy(nextUp = q.nextUp.without(indexToRemove))
            mediaSource?.removeMediaSource(position)
        } else {
            Timber.e { "Cannot remove song from base queue" }
        }
    }

    fun playNext(applyToPlayer: Boolean = true) {
        addCurrentToHistory()
        val prev = _queue.value
        val q = prev.toNext()
        _queue puts q as CombinedQueue

        if (prev.isPlayingNext) {
            mediaSource?.removeMediaSource(prev.position)
        }

        if (applyToPlayer && q.position != player.currentWindowIndex) {
            player.seekToDefaultPosition(q.position)
        }
    }

    fun playPrevious(applyToPlayer: Boolean = true) {
        addCurrentToHistory()
        val q = _queue.value.toPrev()
        _queue puts q as CombinedQueue

        if (applyToPlayer && q.position != player.currentWindowIndex) {
            player.seekToDefaultPosition(q.position)
        }
//        prepareSource()
    }

    fun shiftQueuePosition(pos: Int, applyToPlayer: Boolean = true) {
        val prev = _queue.value
        when (pos) {
//            prev.position -> {}
            prev.position - 1 -> if (pos >= 0) {
                playPrevious(applyToPlayer)
            }
            prev.position + 1 -> if (pos < prev.list.size) {
                playNext(applyToPlayer)
            }
            else -> {
                val q = prev.shiftedPosition(pos)
                _queue puts q as CombinedQueue

                // clear out previous tracks that were in queue
                if (pos > prev.position) {
                    for (idx in prev.position..pos) {
                        if (prev.indexWithinUpNext(idx)) {
                            mediaSource?.removeMediaSource(idx)
                        }
                    }
                }

                if (applyToPlayer && player.currentWindowIndex != q.position) {
                    player.seekToDefaultPosition(q.position)
                }
            }
        }
    }

    fun shiftQueuePositionRelative(diff: Int) {
        val prev = _queue.value
        val pos = prev.position + diff
        shiftQueuePosition(pos)
    }

    fun shiftQueueItem(from: Int, to: Int) {
        _queue putsMapped { q ->
            q.shifted(from, to) as CombinedQueue
        }

        prepareSource()
    }

    fun replaceQueue(q: CombinedQueue) {
        _queue puts q
        if (q.isEmpty()) {
            stop()
        } else {
            prepareSource()
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
//            _isPlaying puts true
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

    private fun temporaryPause(): Boolean {
        // TODO: Move this stuff to onPlayerStateChanged
        val alreadyPlaying = _isPlaying.value && player.playWhenReady
        if (alreadyPlaying) {
            totalListenedTime += player.currentPosition - lastResumeTime
            player.playWhenReady = false
        }
        return alreadyPlaying
    }


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
        if (percent > LISTENED_PROPORTION) {
            // TODO: Add timestamp and percent to history entries
            UserPrefs.history putsMapped {
                it.add(HistoryEntry(_queue.value.current!!))
            }
        }
        totalListenedTime = 0
    }

    companion object {
        // TODO: Decide on percent threshold to count as a "full" listen. Is half the song enough?
        private const val LISTENED_PROPORTION = 0.5
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:58.0) Gecko/20100101 Firefox/58.0"
//        val THREAD_CONTEXT = newSingleThreadContext("music-player")
//        val THREAD_CONTEXT = Dispatchers.Main
    }
}
