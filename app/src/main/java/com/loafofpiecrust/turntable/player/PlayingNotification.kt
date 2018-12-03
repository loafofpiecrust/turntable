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
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.launch
import org.jetbrains.anko.notificationManager

class PlayingNotification(private val service: MusicService) {
    private var lastSongColor: Int? = null

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
            GlobalScope.launch {
                lastSongColor = MusicService.currentSongColor.openSubscription().first()
                build(song, playing, lastSongColor)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() = service.notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_HIGH).apply {
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

        val n = NotificationCompat.Builder(service, CHANNEL_ID).apply {
            setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle().run {
                setMediaSession(service.mediaSession.sessionToken)
                setShowCancelButton(true)
                setCancelButtonIntent(service.notifyIntent(
                    PlayerAction.Stop
                ))
            })

            priority = if (playing) {
                NotificationCompat.PRIORITY_MAX
            } else NotificationCompat.PRIORITY_HIGH

            setOnlyAlertOnce(true)
            setSmallIcon(R.drawable.ic_album)
            setContentTitle(song.id.displayName)
            setContentText(song.id.artist.displayName)
            setAutoCancel(false)
            setColorized(true)
            setShowWhen(false)
            paletteColor?.let { color = it }
            // Colors the name only

            // Previous
            if (service.player.hasPrev) {
                addAction(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    service.notifyIntent(PlayerAction.RelativePosition(-1))
                )
            }

            // Toggle pause
            if (playing) {
                addAction(R.drawable.ic_pause, "Pause", service.notifyIntent(PlayerAction.Pause))
            } else {
                addAction(R.drawable.ic_play_arrow, "Play", service.notifyIntent(PlayerAction.Play))
            }

            // Next
            if (service.player.hasNext) {
                addAction(R.drawable.ic_skip_next, "Next", service.notifyIntent(
                    PlayerAction.RelativePosition(1)
                ))
            }

            setContentIntent(PendingIntent.getActivity(
                service, 6977,
                MainActivityStarter.getIntent(service, MainActivity.Action.OpenNowPlaying()),
                0
            ))
        }.build()

        if (playing) {
            service.startForeground(69, n)
        } else {
            service.stopForeground(false)
            service.notificationManager.notify(69, n)
        }
    }


    private fun updateSessionMeta(song: Song?, playing: Boolean) {
        if (song != null) {
            service.mediaSession.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.id.album.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.id.artist.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.id.album.artist.displayName)
                .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, song.disc.toLong())
                .build()
            )
        }

        // TODO: Get more detailed with our state here
        val state = if (playing) {
            PlaybackStateCompat.STATE_PLAYING
        } else PlaybackStateCompat.STATE_PAUSED

        val seekState = service.player.currentBufferState
        service.mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(state, seekState.position, 1f)
            .setBufferedPosition(seekState.bufferedPosition)
            .build())

        service.mediaSession.isActive = playing
    }

    companion object {
        private const val CHANNEL_ID = "turntable"
    }
}