package com.loafofpiecrust.turntable.player

//import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import activitystarter.ActivityStarter
import activitystarter.Arg
import activitystarter.MakeActivityStarter
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.firstOrNull
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext
import org.jetbrains.anko.*
import java.lang.ref.WeakReference

/// Plays the music to the speakers and manages the _queue, that's it.
@MakeActivityStarter
class MusicService : Service(), OnAudioFocusChangeListener {

    companion object: AnkoLogger {
        private val _instance = ConflatedBroadcastChannel<WeakReference<MusicService>>()
        val instance get() = _instance.openSubscription().map { it.get() }

        const val INTENT_ACTION = "com.loafofpiecrust.turntable.MUSIC_ACTION"

        fun enact(msg: SyncService.Message, shouldSync: Boolean = true) {
            val instance = _instance.valueOrNull?.get()
            if (instance != null) {
                instance.doAction(msg, shouldSync)
            } else {
                MusicServiceStarter.start(App.instance, msg, shouldSync)
            }
        }
    }


    /**
     * Incoming command from notification _or_ the sync service.
     * Should remain generic between these two uses to make syncing work as smoothly as possible.
     */
    @Arg(optional=true)
    var command: SyncService.Message? = null

    @Arg(optional=true)
    var shouldSync: Boolean? = null


//    private val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(bandwidthMeter))

    lateinit var player: MusicPlayer


    private var stopped: Boolean = false
    private val subs = Job()
    // TODO: Release wakelock when playback is stopped for a bit, then re-acquire when playback resumes.
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.loafofpiecrust.turntable:musicWakeLock")
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "com.loafofpiecrust.turntable")
    }
    private val session by lazy {
        MediaSessionCompat(
            App.instance,
            "com.loafofpiecrust.turntable",
            null,
            notifyIntent(SyncService.Message.Play(), App.instance)
        ).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player.play()
                }

                override fun onPause() {
                    player.pause()
                }

                override fun onSkipToNext() {
                    player.playNext()
                }
            })
        }
    }
//    private var sessionConnector: MediaSessionConnector? = null

    private var inForeground = false
    private var isFocused = false


    /**
     * Sync shit
     */
    var canControl: Boolean = true
        private set
//    var syncMode: SyncService.Mode = SyncService.Mode.None()

    fun notifyIntent(msg: SyncService.Message, context: Context? = this): PendingIntent = PendingIntent.getService(
        context ?: App.instance, 69 + msg.hashCode(),
        MusicServiceStarter.getIntent(context, msg),
        PendingIntent.FLAG_UPDATE_CURRENT // Signals the existing MusicService instance
    )


    override fun onCreate() {
        super.onCreate()
        player = MusicPlayer(ctx)

        player.currentSong.consumeEach(BG_POOL + subs) { song ->
            if (song != null) {
                showNotification(song, player.isPlaying.first())
            }
        }

        player.isPlaying.skip(1)
            .distinctSeq()
            .consumeEach(BG_POOL + subs) { isPlaying ->
                given (player.currentSong.firstOrNull()) { song ->
                    showNotification(song, isPlaying)
                }

                if (player.isStreaming) {
                    // Playing remote song
                    if (isPlaying) {
                        wifiLock.acquire()
                    } else if (wifiLock.isHeld) {
                        wifiLock.release()
                    }
                }
                if (isPlaying) {
//                    val buffer = player.bufferState.firstOrNull()
//                    if (buffer != null) {
//                        wakeLock.acquire(buffer.duration - buffer.position)
//                    } else {
                        wakeLock.acquire()
//                    }
                } else if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }

//        player.queue.consumeEach(BG_POOL + subs) {
//            SyncService.send(SyncService.Message.QueuePosition(it.position))
//        }

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (UserPrefs.pauseOnUnplug.value) {
                    player.pause()
                }
            }
        }, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra("state", 0)
                when (state) {
                    0 -> if (UserPrefs.pauseOnUnplug.valueOrNull == true) {
                        player.pause()
                    }
                    1 -> if (UserPrefs.resumeOnPlug.valueOrNull == true && !player.queue.isEmpty && player.shouldAutoplay) {
                        player.play()
                    }
                }
            }
        }, IntentFilter(ACTION_HEADSET_PLUG))

        // once we're good and initialized, tell everyone we exist!
        _instance puts WeakReference(this)
    }

    override fun onDestroy() {
        _instance puts WeakReference<MusicService>(null)
//        given (queue.blockingFirst().current) {
//            buildNotification(it, false)
//        }
        stopForeground(true)
        audioManager.abandonAudioFocus(this)
        player.release()
        subs.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        session.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            ActivityStarter.fill(this, intent)
            val msg = command
            val shouldSync = shouldSync
            if (msg != null && shouldSync != null) {
                doAction(msg, shouldSync)
            }
        }
        return START_STICKY
    }

    private fun doAction(msg: SyncService.Message, shouldSync: Boolean) {
        var success = true
        val msg = if (msg is SyncService.Message.TogglePause) {
            if (runBlocking { player.isPlaying.first() }) {
                SyncService.Message.Pause()
            } else SyncService.Message.Play()
        } else msg
        when (msg) {
            is SyncService.Message.Play -> {
                if (isFocused || requestFocus()) {
                    success = player.play()
                }
            }
            is SyncService.Message.Pause -> {
                success = player.pause()
                if (success) {
                    audioManager.abandonAudioFocus(this)
                    isFocused = false
                }
            }
            is SyncService.Message.QueuePosition -> player.shiftQueuePosition(msg.pos)
            is SyncService.Message.Enqueue -> player.enqueue(msg.songs, msg.mode)
            is SyncService.Message.PlaySongs -> player.playSongs(msg.songs, msg.pos)
            is SyncService.Message.SeekTo -> player.seekTo(msg.pos)
        }
        if (success && shouldSync) {
            SyncService.send(msg)
        }
    }

    override fun onAudioFocusChange(change: Int) {
        println("Audio focus changed to $change")
        isFocused = (change == AUDIOFOCUS_GAIN || change == AUDIOFOCUS_GAIN_TRANSIENT || change == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        when (change) {
            AUDIOFOCUS_GAIN -> player.play()
            AUDIOFOCUS_LOSS -> player.temporaryPause()
            AUDIOFOCUS_LOSS_TRANSIENT -> player.temporaryPause()
            AUDIOFOCUS_GAIN_TRANSIENT -> player.play()
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // just decrease volume
                if (UserPrefs.reduceVolumeOnFocusLoss.value) {
                    player.volume = 0.3f
                }
            }
            AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                // increase volume back to normal
                player.volume = 1f
            }
        }
    }

    private fun requestFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AUDIOFOCUS_GAIN
        ) == AUDIOFOCUS_REQUEST_GRANTED
        isFocused = result
        return result
    }

    private fun updateSessionMeta(song: Song?, playing: Boolean) {
        if (song != null) {
            session.setMetadata(MediaMetadataCompat.Builder()
                .putString(METADATA_KEY_ALBUM, song.id.album.toString())
                .putString(METADATA_KEY_ARTIST, song.id.artist)
                .putString(METADATA_KEY_ALBUM_ARTIST, song.id.album.artist.toString())
                .putString(METADATA_KEY_ALBUM_ART_URI, song.artworkUrl)
                .putLong(METADATA_KEY_YEAR, song.year?.toLong() ?: 0)
                .putLong(METADATA_KEY_DISC_NUMBER, song.disc.toLong())
                .build())
        }

        // TODO: Get more detailed with our state here
        val state = if (playing) {
            PlaybackStateCompat.STATE_PLAYING
        } else PlaybackStateCompat.STATE_PAUSED

//        session.setPlaybackState(PlaybackStateCompat.Builder()
//            .setState(state, player.currentPosition, 1f)
//            .setBufferedPosition(player.bufferedPosition)
//            .build())

        session.isActive = playing
    }

    var lastSongColor: Int? = null

    private suspend fun showNotification(song: Song?, playing: Boolean) {
        withContext(UI) { updateSessionMeta(song, playing) }
        buildNotification(song, playing, lastSongColor)
        if (playing) {
            // Load color
            song?.loadCover(Glide.with(ctx)) { palette, swatch ->
                if (swatch != null) {
                    lastSongColor = swatch.rgb
                    buildNotification(song, playing, swatch.rgb)
                }
            }?.consume(UI + subs) {
                first()?.preload()
            }
        }
    }

    private fun buildNotification(song: Song?, playing: Boolean, paletteColor: Int? = null) {
        if (song == null) {
            stopForeground(true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel("turntable", "Music Playback", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Playback controls and info"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
            }
        }
//        val chan = NotificationChannel(100, "Playback", NotificationManager.IMPORTANCE_HIGH)
        val n = NotificationCompat.Builder(ctx, "turntable").apply {
            setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle().run {
                setMediaSession(session.sessionToken)
                setShowCancelButton(true)
                setCancelButtonIntent(notifyIntent(
                        SyncService.Message.Stop()
                ))
            })

            priority = if (playing) {
                NotificationCompat.PRIORITY_MAX
            } else NotificationCompat.PRIORITY_HIGH

            setSmallIcon(R.drawable.ic_album)
            setContentTitle(song.id.displayName)
            setContentText(song.id.artist)
            setContentInfo(song.id.album.displayName)
            setAutoCancel(false)
//            setOngoing(playing)
            setColorized(true)
            given(paletteColor) { color = it }
//            setBadgeIconType(R.drawable.ic_cake)
            // Colors the name only

            // Previous
            if (player.hasPrev) {
                addAction(R.drawable.ic_skip_previous, "Previous", notifyIntent(
                        SyncService.Message.RelativePosition(-1)
                ))
            }

            // Toggle pause
            if (playing) {
                addAction(R.drawable.ic_pause, "Pause", notifyIntent(SyncService.Message.Pause()))
            } else {
                addAction(R.drawable.ic_play_arrow, "Play", notifyIntent(SyncService.Message.Play()))
            }

            // Next
            if (player.hasNext) {
                addAction(R.drawable.ic_skip_next, "Next", notifyIntent(
                        SyncService.Message.RelativePosition(1)
                ))
            }

            setContentIntent(PendingIntent.getActivity(
                ctx, 6977,
                MainActivityStarter.getIntent(ctx, MainActivity.Action.OpenNowPlaying()),
                0
            ))
        }.build()


        task(UI) {
            inForeground = if (inForeground && !playing) {
                stopForeground(false)
                notificationManager.notify(69, n)
                false
            } else if (playing) {
                if (inForeground) {
                    notificationManager.notify(69, n)
                } else {
                    startForeground(69, n)
                }
                true
            } else {
                false
            }
        }
    }

    class Binder(val music: MusicService) : android.os.Binder()
    override fun onBind(intent: Intent): IBinder? = Binder(this)

    /// Song has ended. Move on to the next song, or stop if at the end of the queue.
//    override fun onCompletion(player: MediaPlayer?) {
//        println("song ended")
//        val q = _queue.value
//        desynced { // Don't sync this, other users will have the same listener.
//            if (q.position >= q.list.size) {
//                stop()
//            } else {
//                runBlocking { playNext().await() }
//            }
//        }
//    }

//    private fun broadcast(a: Action) {
////        sendBroadcast(Intent(INTENT_ACTION).putExtra("action", a))
//        events.onNext(a)
//        // TODO: We can also do our networked messages here!!!
//    }

    /// @return Whether the song could successfully be loaded for playback
//    @Synchronized


//    @Synchronized





}