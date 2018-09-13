package com.loafofpiecrust.turntable.player

import android.net.Uri
import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.DataSource
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.fail
import com.loafofpiecrust.turntable.util.then
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import java.io.IOException
import java.util.concurrent.TimeUnit


class DeferredMediaSource(
    private val song: Song,
    private val sourceFactory: DataSource.Factory,
    private val extractorsFactory: ExtractorsFactory,
    private val callback: Callback
) : MediaSource, AnkoLogger {
    companion object {
        /**
         * This state indicates the [DeferredMediaSource] has just been initialized or reset.
         * The source must be prepared and loaded again before playback.
         */
        const val STATE_INIT = 0
        /**
         * This state indicates the [DeferredMediaSource] has been prepared and is ready to load.
         */
        const val STATE_PREPARED = 1
        /**
         * This state indicates the [DeferredMediaSource] has been loaded without errors and
         * is ready for playback.
         */
        const val STATE_LOADED = 2
    }

    var state: Int = 0
        private set

    private var mediaSource: MediaSource? = null

    /* Custom internal objects */
    private var loader: Job? = null
    private var exoPlayer: ExoPlayer? = null
    private var listener: MediaSource.SourceInfoRefreshListener? = null
    private var error: Throwable? = null

    interface Callback {
        /**
         * Player-specific [com.google.android.exoplayer2.source.MediaSource] resolution
         * from a given StreamInfo.
         */
        fun sourceOf(item: Song, media: Song.Media): MediaSource?
    }

    init {
        this.state = STATE_INIT
    }

    override fun prepareSource(player: ExoPlayer, isTopLevelSource: Boolean, listener: MediaSource.SourceInfoRefreshListener?) {
        this.exoPlayer = player
        this.listener = listener
        this.state = STATE_PREPARED
    }

    /**
     * Externally controlled loading. This method fully prepares the source to be used
     * like any other native [com.google.android.exoplayer2.source.MediaSource].
     *
     * Ideally, this should be called after this source has entered PREPARED state and
     * called once only.
     *
     * If loading fails here, an error will be propagated out and result in an
     * [ExoPlaybackException][com.google.android.exoplayer2.ExoPlaybackException],
     * which is delegated to the player.
     */
    @Synchronized
    fun load() {
        if (state != STATE_PREPARED || loader != null) return

        debug { "Loading: [" + song.id.name + "] with url: <insert-here>" }

        loader = async(BG_POOL) {
            val (url, start, end) = song.loadMedia() ?: return@async null
            val innerSource: MediaSource = ExtractorMediaSource(
                Uri.parse(url),
                sourceFactory,
                extractorsFactory,
                null, null
            )

            if (start > 0 || end > 0) {
                val start = TimeUnit.MILLISECONDS.toMicros(start.toLong())
                val end = if (end > 0) {
                    TimeUnit.MILLISECONDS.toMicros(end.toLong())
                } else C.TIME_END_OF_SOURCE
                ClippingMediaSource(innerSource, start, end)
            } else innerSource
        }.then(UI) {
            onMediaSourceReceived(it)
        }.fail(UI) {
            onStreamInfoError(it)
        }
    }

    @Throws(Exception::class)
    private fun onStreamInfoReceived(item: Song,
                                     info: Song.Media): MediaSource {
        if (callback == null) {
            throw Exception("No available callback for resolving stream info.")
        }

        return callback.sourceOf(item, info)!!
//            ?: throw Exception("Unable to resolve source from stream info. URL: " + stream!!.getUrl() +
//                ", audio count: " + info.audio_streams.size() +
//                ", video count: " + info.video_only_streams.size() + info.video_streams.size())
    }

    @Throws(Exception::class)
    private fun onMediaSourceReceived(mediaSource: MediaSource?) {
        if (exoPlayer == null || listener == null || mediaSource == null) {
            throw Exception("MediaSource loading failed. URL: " + song.id.name)
        }

        debug { " Loaded: [" + song.id.name + "] with url: " }
        state = STATE_LOADED

        this.mediaSource = mediaSource
        this.mediaSource!!.prepareSource(exoPlayer, false, listener)
    }

    private fun onStreamInfoError(throwable: Throwable) {
        debug { "Loading error: $throwable" }
        error = throwable
        state = STATE_LOADED
    }

    /**
     * Delegate all errors to the player after [load][.load] is complete.
     *
     * Specifically, this method is called after an exception has occurred during loading or
     * [prepareSource][com.google.android.exoplayer2.source.MediaSource.prepareSource].
     */
    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        if (error != null) {
            throw IOException(error)
        }

        if (mediaSource != null) {
            mediaSource!!.maybeThrowSourceInfoRefreshError()
        }
    }

    override fun createPeriod(mediaPeriodId: MediaSource.MediaPeriodId, allocator: Allocator): MediaPeriod {
        return mediaSource!!.createPeriod(mediaPeriodId, allocator)
    }

    /**
     * Releases the media period (buffers).
     *
     * This may be called after [releaseSource][.releaseSource].
     */
    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        mediaSource!!.releasePeriod(mediaPeriod)
    }

    /**
     * Cleans up all internal custom objects creating during loading.
     *
     * This method is called when the parent [com.google.android.exoplayer2.source.MediaSource]
     * is released or when the player is stopped.
     *
     * This method should not release or set null the resources passed in through the constructor.
     * This method should not set null the internal [com.google.android.exoplayer2.source.MediaSource].
     */
    override fun releaseSource(listener: MediaSource.SourceInfoRefreshListener?) {
        if (mediaSource != null) {
            mediaSource!!.releaseSource(listener)
        }
        if (loader != null) {
            loader!!.cancel()
        }

        /* Do not set mediaSource as null here as it may be called through releasePeriod */
        loader = null
        exoPlayer = null
        this.listener = null
        error = null

        state = STATE_INIT
    }


    override fun addEventListener(handler: Handler?, eventListener: MediaSourceEventListener?) {
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener?) {
    }

}