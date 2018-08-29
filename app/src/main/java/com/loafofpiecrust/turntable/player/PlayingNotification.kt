package com.loafofpiecrust.turntable.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.consume
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.first
import org.jetbrains.anko.notificationManager

class PlayingNotification(private val service: MusicService) {
    private var lastSongColor: Int? = null
    private var inForeground = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }
    }

    fun show(song: Song?, playing: Boolean) {
        updateSessionMeta(song, playing)
        build(song, playing, lastSongColor)
        if (song != null && playing) {
            // Load color
            song.loadCover(Glide.with(service)) { palette, swatch ->
                if (swatch != null) {
                    lastSongColor = swatch.rgb
                    build(song, playing, swatch.rgb)
                }
            }.consume(UI) {
                first()?.preload()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() = service.notificationManager.createNotificationChannel(
        NotificationChannel("turntable", "Music Playback", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Playback controls and info"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            enableVibration(false)
        }
    )

    private fun build(song: Song?, playing: Boolean, paletteColor: Int? = null) {
        if (song == null) {
            service.stopForeground(true)
            return
        }

//        val chan = NotificationChannel(100, "Playback", NotificationManager.IMPORTANCE_HIGH)
        val n = NotificationCompat.Builder(service, "turntable").apply {
            setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle().run {
                setMediaSession(service.mediaSession.sessionToken)
                setShowCancelButton(true)
                setCancelButtonIntent(service.notifyIntent(
                    MusicService.Action.STOP
                ))
            })

            priority = if (playing) {
                NotificationCompat.PRIORITY_MAX
            } else NotificationCompat.PRIORITY_HIGH

            setSmallIcon(R.drawable.ic_album)
            setContentTitle(song.id.displayName)
            setContentText(song.id.artist.displayName)
            setContentInfo(song.id.album.displayName)
            setAutoCancel(false)
//            setOngoing(playing)
            setColorized(true)
            paletteColor?.let { color = it }
//            setBadgeIconType(R.drawable.ic_cake)
            // Colors the name only

            // Previous
            if (service.player.hasPrev) {
                addAction(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    service.notifyIntent(MusicService.Action.PREVIOUS)
                )
            }

            // Toggle pause
            if (playing) {
                addAction(R.drawable.ic_pause, "Pause", service.notifyIntent(MusicService.Action.PAUSE))
            } else {
                addAction(R.drawable.ic_play_arrow, "Play", service.notifyIntent(MusicService.Action.PLAY))
            }

            // Next
            if (service.player.hasNext) {
                addAction(R.drawable.ic_skip_next, "Next", service.notifyIntent(
                    MusicService.Action.NEXT
                ))
            }

            setContentIntent(PendingIntent.getActivity(
                service, 6977,
                MainActivityStarter.getIntent(service, MainActivity.Action.OpenNowPlaying()),
                0
            ))
        }.build()

        inForeground = if (inForeground && !playing) {
            service.stopForeground(false)
            service.notificationManager.notify(69, n)
            false
        } else if (playing) {
            if (inForeground) {
                service.notificationManager.notify(69, n)
            } else {
                service.startForeground(69, n)
            }
            true
        } else false

    }


    private fun updateSessionMeta(song: Song?, playing: Boolean) {
        if (song != null) {
            service.mediaSession.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.id.album.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.id.artist.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.id.album.artist.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.artworkUrl)
                .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year?.toLong() ?: 0)
                .putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, song.disc.toLong())
                .build())
        }

        // TODO: Get more detailed with our state here
        val state = if (playing) {
            PlaybackStateCompat.STATE_PLAYING
        } else PlaybackStateCompat.STATE_PAUSED

//        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
//            .setState(state, player.currentPosition, 1f)
//            .setBufferedPosition(player.bufferedPosition)
//            .build())

        service.mediaSession.isActive = playing
    }
}