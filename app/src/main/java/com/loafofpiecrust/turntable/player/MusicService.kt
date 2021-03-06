package com.loafofpiecrust.turntable.player

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
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v7.graphics.Palette
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.broadcastReceiver
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.sync.SyncSession
import com.loafofpiecrust.turntable.ui.BaseService
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.anko.audioManager
import org.jetbrains.anko.powerManager
import org.jetbrains.anko.wifiManager
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Plays the music to the speakers and manages the queue.
 */
@MakeActivityStarter
class MusicService : BaseService(), OnAudioFocusChangeListener {
    companion object {
        private val _instance = ConflatedBroadcastChannel<WeakReference<MusicService>>()
        val instance get() = _instance.openSubscription()
            .map { it.get() }.distinctInstanceSeq()

        val player get() = instance.map { it?.player }.startWith(null)

        data class PickedSwatch(
            val palette: Palette,
            val swatch: Palette.Swatch
        )

        val currentSongColor: BroadcastChannel<PickedSwatch?> = player
            .switchMap { it?.queue }
            .switchMap(Dispatchers.IO) { q ->
                val song = q.current
                song?.loadCover(Glide.with(App.instance))
                    ?.map { song to it }
            }
            .map(Dispatchers.Main) { (song, req) ->
                if (req == null) {
                    null
                } else suspendCoroutine<PickedSwatch?> { cont ->
                    req.addListener(loadPalette(song.id) { palette, swatch ->
                        val p = if (palette != null && swatch != null) {
                            PickedSwatch(palette, swatch)
                        } else null
                        cont.resume(p)
                    }).preload()
                }
            }
            .broadcast(CONFLATED)


        private data class SyncedAction(
            val message: PlayerAction,
            val shouldSync: Boolean
        )

        private var lastAction: SyncedAction? = null

        fun offer(msg: PlayerAction, shouldSync: Boolean = true) {
            lastAction = SyncedAction(msg, shouldSync)
            MusicServiceStarter.start(App.instance)
        }
    }

    /**
     * Incoming command from notification _or_ the sync service.
     * Should remain generic between these two uses to make syncing work as smoothly as possible.
     */
    @Arg(optional = true)
    var command: PlayerAction? = null

    @Arg(optional = true)
    var shouldSync: Boolean = false


    lateinit var player: MusicPlayer

    // TODO: Release wakelock when playback is stopped for a bit, then re-acquire when playback resumes.
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.loafofpiecrust.turntable:musicWake"
        )
    }

    private val wifiLock: WifiManager.WifiLock by lazy {
        wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL,
            "com.loafofpiecrust.turntable:musicWifi"
        )
    }

    val mediaSession by lazy {
        MediaSessionCompat(
            applicationContext,
            "com.loafofpiecrust.turntable"
        ).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    offer(PlayerAction.Play)
                }

                override fun onPause() {
                    offer(PlayerAction.Pause)
                }

                override fun onSkipToNext() {
                    offer(PlayerAction.RelativePosition(1))
                }
            })
        }
    }
//    private var sessionConnector: MediaSessionConnector? = null

    private var isFocused = false

    fun notifyIntent(
        msg: PlayerAction,
        shouldSync: Boolean = true,
        context: Context? = this
    ): PendingIntent = PendingIntent.getService(
        context ?: App.instance, 69 + msg.hashCode(),
        MusicServiceStarter.getIntent(context, msg, shouldSync),
        PendingIntent.FLAG_UPDATE_CURRENT // Signals the existing MusicService instance
    )


    private val plugReceiver = broadcastReceiver { context, intent ->
        val state = intent.getIntExtra("state", 0)
        when (state) {
            0 -> if (UserPrefs.pauseOnUnplug.valueOrNull == true) {
                offer(PlayerAction.Pause)
            }
            1 -> if (UserPrefs.resumeOnPlug.valueOrNull == true && !player.queue.isEmpty && player.shouldAutoplay) {
                offer(PlayerAction.Play)
            }
        }
    }

    private val noisyReceiver = broadcastReceiver { context, intent ->
        // Pause when the device is about to become "noisy"
        // This usually means: headphones unplugged
        if (UserPrefs.pauseOnUnplug.value) {
            offer(PlayerAction.Pause)
        }
    }

    override fun onCreate() {
        super.onCreate()

//        launch(MusicPlayer.THREAD_CONTEXT) {
            //        runBlocking(MusicPlayer.THREAD_CONTEXT) {
        player = MusicPlayer(this@MusicService)
        val sessionConnector = MediaSessionConnector(mediaSession)
        sessionConnector.setPlayer(player.player, null)
        sessionConnector.setQueueNavigator(object: TimelineQueueNavigator(mediaSession) {
            override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
                val queue = runBlocking {
                    this@MusicService.player.queue.first()
                }
                val idx = minOf(windowIndex, queue.list.size - 1)
                val song = queue.list[idx]
                return MediaDescriptionCompat.Builder()
                    .setTitle(song.id.displayName)
                    .setSubtitle(song.id.artist.displayName)
                    .build()
            }
        })
//        }

            launch(Dispatchers.Default) {
                player.currentSong.distinctInstanceSeq().consumeEach { song ->
                    withContext(Dispatchers.Main) {
                        showNotification(song, player.isPlaying.first())
                    }
                }
            }

            launch(Dispatchers.Default) {
                player.isPlaying.skip(1).distinctSeq().consumeEach { isPlaying ->
                    withContext(Dispatchers.Main) {
                        showNotification(player.currentSong.first(), isPlaying)
                    }
                }
            }

            launch(Dispatchers.Default) {
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
//                        val buffer = player.currentBufferState
//                        if (buffer != null) {
//                            wakeLock.acquire(buffer.duration - buffer.position)
//                        } else {
                            wakeLock.acquire()
//                        }
                        } else if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    }
            }

            registerReceiver(noisyReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
            registerReceiver(plugReceiver, IntentFilter(ACTION_HEADSET_PLUG))

            // once we're good and initialized, tell everyone we exist!
            _instance puts WeakReference(this@MusicService)
//        }
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
        mediaSession.release()
        unregisterReceiver(noisyReceiver)
        unregisterReceiver(plugReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            ActivityStarter.fill(this, intent)
            if (lastAction != null) {
                command = lastAction!!.message
                shouldSync = lastAction!!.shouldSync
                lastAction = null
            }
            command?.let { msg ->
                doAction(msg, shouldSync)
            }

            command = null
            shouldSync = false
        }
        return START_STICKY
    }

    private fun doAction(msg: PlayerAction, shouldSync: Boolean) {
        val success = msg.run { this@MusicService.enact() }
        if (success && shouldSync) {
            SyncSession.sendToActive(msg)
        }
    }

    override fun onAudioFocusChange(change: Int) {
//        launch(MusicPlayer.THREAD_CONTEXT) {
            isFocused = change == AUDIOFOCUS_GAIN ||
                change == AUDIOFOCUS_GAIN_TRANSIENT ||
                change == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

            when (change) {
                AUDIOFOCUS_GAIN -> doAction(PlayerAction.Play, false)
                AUDIOFOCUS_LOSS -> doAction(PlayerAction.Pause, false)
                AUDIOFOCUS_LOSS_TRANSIENT -> doAction(PlayerAction.Pause, false)
                AUDIOFOCUS_GAIN_TRANSIENT -> doAction(PlayerAction.Play, false)
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
//        }
    }

    fun requestFocus(): Boolean {
        return isFocused || (
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AUDIOFOCUS_GAIN
            ) == AUDIOFOCUS_REQUEST_GRANTED
        ).also { isFocused = it }
    }

    fun abandonFocus(): Boolean {
        audioManager.abandonAudioFocus(this)
        isFocused = false
        return true
    }

    private val notification by lazy { PlayingNotification(this) }
    private fun showNotification(song: Song?, playing: Boolean) {
        notification.show(song, playing)
    }
}