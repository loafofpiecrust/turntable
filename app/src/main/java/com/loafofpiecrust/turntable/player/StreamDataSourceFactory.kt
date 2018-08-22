package com.loafofpiecrust.turntable.player

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.DataSource
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.BG_POOL
import kotlinx.coroutines.experimental.async
import java.util.*
import java.util.concurrent.TimeUnit

class StreamMediaSource(
    private val song: Song,
    private val sourceFactory: DataSource.Factory,
    private val extractorsFactory: ExtractorsFactory,
    private val listener: ((Boolean) -> Unit)? = null
): BaseMediaSource(), MediaSource.SourceInfoRefreshListener {
    private var innerSource: MediaSource? = null
    private var player: ExoPlayer? = null

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator): MediaPeriod? {
        return if (innerSource == null) {
            return StreamMediaPeriod(song, sourceFactory, extractorsFactory, id, allocator) { src ->
                innerSource = src
                src?.prepareSource(player, false, this)
            }
        } else {
            innerSource!!.createPeriod(id, allocator)
        }
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod?) {
        try {
            val period = if (mediaPeriod is StreamMediaPeriod) {
                mediaPeriod.mediaPeriod
            } else mediaPeriod
            innerSource?.releasePeriod(period)
        } catch (e: Throwable) {
        }
    }

    override fun prepareSourceInternal(player: ExoPlayer, isTopLevelSource: Boolean) {
        this.player = player
        refreshSourceInfo(SinglePeriodTimeline(song.duration.toLong(), true, false), null)
    }

    override fun releaseSourceInternal() {
        innerSource?.releaseSource(this)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        innerSource?.maybeThrowSourceInfoRefreshError()
    }

    override fun onSourceInfoRefreshed(source: MediaSource, timeline: Timeline, manifest: Any?) {
        refreshSourceInfo(timeline, manifest)
        listener?.invoke(true)
    }
}

class StreamMediaPeriod(
    private val song: Song,
    private val sourceFactory: DataSource.Factory,
    private val extractorsFactory: ExtractorsFactory,
    private val id: MediaSource.MediaPeriodId,
    private val allocator: Allocator,
    private val sourceCallback: (MediaSource?) -> Unit
): MediaPeriod, MediaPeriod.Callback {
    override fun onPrepared(mediaPeriod: MediaPeriod) {
        callback.onPrepared(this)
    }

    override fun onContinueLoadingRequested(source: MediaPeriod) {
        callback.onContinueLoadingRequested(this)
    }

    var mediaPeriod: MediaPeriod? = null
        private set
    private var source: MediaSource? = null
    private lateinit var callback: MediaPeriod.Callback

    override fun maybeThrowPrepareError() {
        mediaPeriod?.maybeThrowPrepareError()
    }

    override fun getBufferedPositionUs(): Long {
        return mediaPeriod?.bufferedPositionUs ?: 0
    }

    override fun reevaluateBuffer(positionUs: Long) {
        mediaPeriod?.reevaluateBuffer(positionUs)
    }

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        // Loading happens here, telling `callback` when it's done.
        this.callback = callback

        fun setupMedia(media: Song.Media) {
            source = ExtractorMediaSource(
                Uri.parse(media.url),
                sourceFactory,
                extractorsFactory,
                null, null
            )

            if (media.start > 0 || media.end > 0) {
                val start = TimeUnit.MILLISECONDS.toMicros(media.start.toLong())
                val end = if (media.end > 0) {
                    TimeUnit.MILLISECONDS.toMicros(media.end.toLong())
                } else C.TIME_END_OF_SOURCE
                source = ClippingMediaSource(source, start, end)
            }

            callback.onContinueLoadingRequested(this)
        }

        val local = song.localMedia
        if (local != null) {
            setupMedia(local)
        } else async(BG_POOL) {
            val remote = song.remoteMedia()
            if (remote == null) {
                maybeThrowPrepareError()
                throw MissingResourceException("No stream to load!", Song.Media::class.simpleName, song.id.toString())
            }
            setupMedia(remote)
        }
    }

    override fun continueLoading(positionUs: Long): Boolean {
        // continue loading...!!!
        return if (source != null) {
            if (mediaPeriod != null) {
                mediaPeriod?.continueLoading(positionUs)
            } else {
                mediaPeriod = source!!.createPeriod(id, allocator)
                sourceCallback.invoke(source)
                mediaPeriod?.prepare(this, positionUs)
            }
            true
        } else {
//            callback.onContinueLoadingRequested(this)
            false
        }
    }


    override fun getTrackGroups(): TrackGroupArray = mediaPeriod!!.trackGroups
    override fun selectTracks(selections: Array<out TrackSelection>?, mayRetainStreamFlags: BooleanArray?, streams: Array<out SampleStream>?, streamResetFlags: BooleanArray?, positionUs: Long): Long {
        return mediaPeriod!!.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs)
    }


    override fun readDiscontinuity(): Long {
        return mediaPeriod?.readDiscontinuity() ?: 0
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        mediaPeriod?.discardBuffer(positionUs, toKeyframe)
    }

    override fun getNextLoadPositionUs(): Long {
        return mediaPeriod?.nextLoadPositionUs ?: 0
    }

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters?): Long {
        return mediaPeriod?.getAdjustedSeekPositionUs(positionUs, seekParameters) ?: 0
    }

    override fun seekToUs(positionUs: Long): Long {
        return mediaPeriod?.seekToUs(positionUs) ?: 0
    }
}