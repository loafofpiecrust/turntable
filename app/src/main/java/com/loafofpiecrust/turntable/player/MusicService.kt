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
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import java.lang.ref.WeakReference

/// Plays the music to the speakers and manages the _queue, that's it.
@MakeActivityStarter
class MusicService : Service(), OnAudioFocusChangeListener, AnkoLogger {
    companion object {
        private val actions = actor<Pair<SyncService.Message, Boolean>>(capacity = Channel.UNLIMITED) {
            for ((action, synced) in channel) {
                val service = _instance.valueOrNull?.get() ?: run {
                    MusicServiceStarter.start(App.instance)
                    instance.first()
                }
                service.doAction(action, synced)
            }
        }

        private val _instance = ConflatedBroadcastChannel<WeakReference<MusicService>>()
        val instance get() = _instance.openSubscription().map { it.get() }.filterNotNull()

        fun enact(msg: SyncService.Message, shouldSync: Boolean = true) {
            actions.offer(msg to shouldSync)
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


    lateinit var player: MusicPlayer

    private val subs = Job()
    // TODO: Release wakelock when playback is stopped for a bit, then re-acquire when playback resumes.
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.loafofpiecrust.turntable:musicWakeLock")
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "com.loafofpiecrust.turntable")
    }
    val mediaSession by lazy {
        MediaSessionCompat(
            this,
            "com.loafofpiecrust.turntable",
            null,
            notifyIntent(SyncService.Message.Play())
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

    private var isFocused = false

    fun notifyIntent(msg: SyncService.Message, context: Context? = this): PendingIntent = PendingIntent.getService(
        context ?: App.instance, 69 + msg.hashCode(),
        MusicServiceStarter.getIntent(context, msg),
        PendingIntent.FLAG_UPDATE_CURRENT // Signals the existing MusicService instance
    )


    private val plugReceiver = broadcastReceiver { context, intent ->
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

    private val noisyReceiver = broadcastReceiver { context, intent ->
        // Pause when the device is about to become "noisy"
        // This usually means: headphones unplugged
        if (UserPrefs.pauseOnUnplug.value) {
            player.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = MusicPlayer(ctx)

        player.currentSong.combineLatest(player.isPlaying)
            .consumeEach(UI + subs) { (song, playing) ->
                showNotification(song, playing)
            }

        player.isPlaying.skip(1)
            .distinctSeq()
            .consumeEach(BG_POOL + subs) { isPlaying ->
                if (player.isStreaming) {
                    // Playing remote song
                    if (isPlaying) {
                        wifiLock.acquire()
                    } else if (wifiLock.isHeld) {
                        wifiLock.release()
                    }
                }
                if (isPlaying) {
                    val buffer = player.bufferState.firstOrNull()
                    if (buffer != null) {
                        wakeLock.acquire(buffer.duration - buffer.position)
                    } else {
                        wakeLock.acquire()
                    }
                } else if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }

        registerReceiver(noisyReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
        registerReceiver(plugReceiver, IntentFilter(ACTION_HEADSET_PLUG))

        // once we're good and initialized, tell everyone we exist!
        _instance puts WeakReference(this)
    }

    override fun onDestroy() {
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
        mediaSession.release()
        unregisterReceiver(noisyReceiver)
        unregisterReceiver(plugReceiver)
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
            is SyncService.Message.RelativePosition -> player.shiftQueuePositionRelative(msg.diff)
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

    private fun showNotification(song: Song?, playing: Boolean) {
        PlayingNotification(this).show(song, playing)
    }

    override fun onBind(intent: Intent): IBinder? = null
}