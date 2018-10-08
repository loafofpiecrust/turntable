package com.loafofpiecrust.turntable.player

//import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import activitystarter.ActivityStarter
import activitystarter.Arg
import activitystarter.MakeActivityStarter
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.broadcastReceiver
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.ui.BaseService
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.*
import java.lang.ref.WeakReference

/// Plays the music to the speakers and manages the _queue, that's it.
@MakeActivityStarter
class MusicService : BaseService(), OnAudioFocusChangeListener, AnkoLogger {
    companion object {
        private val _instance = ConflatedBroadcastChannel<WeakReference<MusicService>>()
        val instance get() = _instance.openSubscription().map { it.get() }

        private data class SyncedAction(
            val message: PlayerAction,
            val shouldSync: Boolean
        )

        private val actions = GlobalScope.actor<SyncedAction>(capacity = Channel.UNLIMITED) {
            for (action in channel) {
                val service = _instance.valueOrNull?.get() ?: run {
                    App.instance.startService<MusicService>()
                    instance.filterNotNull().first()
                }
                service.doAction(action.message, action.shouldSync)
            }
        }

        fun enact(msg: PlayerAction, shouldSync: Boolean = true) {
            actions.offer(SyncedAction(msg, shouldSync))
        }
        suspend fun enactNow(msg: PlayerAction, shouldSync: Boolean = true) {
            actions.send(SyncedAction(msg, shouldSync))
        }
    }


    enum class Action {
        PLAY, PAUSE, NEXT, PREVIOUS, STOP
    }
    /**
     * Incoming command from notification _or_ the sync service.
     * Should remain generic between these two uses to make syncing work as smoothly as possible.
     */
    @Arg(optional=true)
    var command: Action? = null

    @Arg(optional=true)
    var shouldSync: Boolean = false


    lateinit var player: MusicPlayer

    // TODO: Release wakelock when playback is stopped for a bit, then re-acquire when playback resumes.
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.loafofpiecrust.turntable:musicWakeLock")
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "com.loafofpiecrust.turntable")
    }
    val mediaSession by lazy {
        tryOr(null) {
            MediaSessionCompat(
                applicationContext,
                "com.loafofpiecrust.turntable",
                null,
                notifyIntent(Action.PLAY)
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
    }
//    private var sessionConnector: MediaSessionConnector? = null

    private var isFocused = false

    fun notifyIntent(msg: Action, context: Context? = this): PendingIntent = PendingIntent.getService(
        context ?: App.instance, 69 + msg.ordinal,
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
        player = MusicPlayer(this)

        launch(Dispatchers.Main) {
            combineLatest(
                player.queue.map { it.current }.distinctInstanceSeq(),
                player.isPlaying.distinctSeq()
            ).consumeEach { (song, playing) ->
                showNotification(song, playing)
            }
        }

        launch {
            player.isPlaying.skip(1)
                .distinctSeq()
                .consumeEach { isPlaying ->
                    if (player.isStreaming) {
                        // Playing remote song
                        if (isPlaying) {
                            wifiLock.acquire()
                        } else if (wifiLock.isHeld) {
                            wifiLock.release()
                        }
                    }
                    if (isPlaying) {
                        val buffer = player.bufferState.consume { receive() }
//                        if (buffer != null) {
                            wakeLock.acquire(buffer.duration - buffer.position)
//                        } else {
//                            wakeLock.acquire()
//                        }
                    } else if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
        }

        registerReceiver(noisyReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
        registerReceiver(plugReceiver, IntentFilter(ACTION_HEADSET_PLUG))

        // once we're good and initialized, tell everyone we exist!
        _instance puts WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        abandonFocus()
        player.release()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        mediaSession?.release()
        unregisterReceiver(noisyReceiver)
        unregisterReceiver(plugReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            ActivityStarter.fill(this, intent)
            command?.let { msg ->
                doAction(when (msg) {
                    Action.PLAY -> PlayerAction.Play()
                    Action.PAUSE -> PlayerAction.Pause()
                    Action.PREVIOUS -> PlayerAction.RelativePosition(-1)
                    Action.NEXT -> PlayerAction.RelativePosition(1)
                    Action.STOP -> PlayerAction.Stop()
                }, shouldSync)
            }
        }
        return START_STICKY
    }

    private fun doAction(msg: PlayerAction, shouldSync: Boolean) {
        var success = true
        val msg = if (msg is PlayerAction.TogglePause) {
            if (runBlocking { player.isPlaying.first() }) {
                PlayerAction.Pause()
            } else PlayerAction.Play()
        } else msg
        when (msg) {
            is PlayerAction.Play -> {
                if (requestFocus()) {
                    success = player.play()
                }
            }
            is PlayerAction.Pause -> {
                success = player.pause()
                if (success) {
                    abandonFocus()
                }
            }
            is PlayerAction.QueuePosition -> {
                player.shiftQueuePosition(msg.pos)
            }
            is PlayerAction.RelativePosition -> {
                player.shiftQueuePositionRelative(msg.diff)
            }
            is PlayerAction.Enqueue -> {
                player.enqueue(msg.songs, msg.mode)
            }
            is PlayerAction.PlaySongs -> {
                player.playSongs(msg.songs, msg.pos)
            }
            is PlayerAction.SeekTo -> {
                player.seekTo(msg.pos)
            }
            is PlayerAction.ShiftQueueItem -> {
                player.shiftQueueItem(msg.fromIdx, msg.toIdx)
            }
            is PlayerAction.RemoveFromQueue -> {
                player.removeFromQueue(msg.pos)
            }
        }
        if (success && shouldSync) {
            SyncService.send(msg)
        }
    }

    override fun onAudioFocusChange(change: Int) {
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
        return isFocused || (
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AUDIOFOCUS_GAIN
            ) == AUDIOFOCUS_REQUEST_GRANTED
        ).also { isFocused = it }
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocus(this)
        isFocused = false
    }

    private val notification by lazy { PlayingNotification(this) }
    private fun showNotification(song: Song?, playing: Boolean) {
        notification.show(song, playing)
    }
}