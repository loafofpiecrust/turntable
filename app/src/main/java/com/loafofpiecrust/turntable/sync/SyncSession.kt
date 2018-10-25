package com.loafofpiecrust.turntable.sync

import android.content.Intent
import android.net.Uri
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.PRIORITY_DEFAULT
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.util.deserialize
import com.mcxiaoke.koi.ext.startService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.first
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext


/**
 * Manages receiving messages.
 */
class SyncSession : FirebaseMessagingService(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    val latency = ConflatedBroadcastChannel(0L)

    /// when a message hasn't been received in TIMEOUT seconds, end sync session.
    private enum class MessageDirection { SENT, RECEIVED }
    private val pings = actor<MessageDirection> {
        var timer: Job? = null
        var lastSent: Long = 0
        for (dir in channel) when (dir) {
            // receiving a ping keeps the session alive
            MessageDirection.RECEIVED -> if (timer?.cancel() == true) {
                latency.offer(System.currentTimeMillis() - lastSent)
            }
            // Wait for a response until TIMEOUT seconds after the least-recent sent message.
            // after that timeout, end the sync session
            MessageDirection.SENT -> if (timer == null || timer.isCompleted) {
                lastSent = System.currentTimeMillis()
                timer = launch {
                    delay(TIMEOUT)

                    val prevMode = Sync.mode.value
                    val name = when (prevMode) {
                        is Sync.Mode.OneOnOne -> prevMode.other.name
                        is Sync.Mode.InGroup -> prevMode.group.name
                        else -> "nobody"
                    }
                    App.launchWith { ctx ->
                        ctx.toast("Connection to $name lost")
                    }
                    endSession()
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        instance.offer(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        launch {
            val mode = Sync.mode.value
            val sender = deserialize(msg.data["sender"]!!) as User

            if (!msg.data.containsKey("action") || sender.deviceId == Sync.selfUser.deviceId) {
                // We sent this message ourselves, don't process it.
                // This will happen when synced in a group.
                return@launch
            }

            val message = try {
                deserialize(msg.data["action"]!!) as Message
            } catch (e: Throwable) {
                error("Unable to parse message", e)
                return@launch
            }

            info { "received $message from $sender" }

            val inSessionWithSender = when (mode) {
                is Sync.Mode.None -> false
                is Sync.Mode.OneOnOne ->
                    mode.other.deviceId == sender.deviceId ||
                        mode.other.username == sender.username
                else -> TODO("Define who can send a message to me")
            }

            // If we're in a consensual sync session with the sender, tell them we're still online.
            if (inSessionWithSender && message !is EndSync && message !is Ping) {
                // Only ping if the last message was not a ping
                // This prevents an infinite ping circle, which is generally unnecessary.
                Sync.sendToSession(Ping())
            }

            // If a random user sends a session-specific message,
            // Don't process it, but send them a rejection response.
            if (!inSessionWithSender && message.requiresSession) {
                Sync.send(EndSync(), sender)
                return@launch
            }

            val resolvedSender = if (sender.displayName == null) {
                sender.refresh().await()
            } else sender

            message.onReceive(resolvedSender)
        }
    }

    override fun onNewToken(token: String) {
        Sync.deviceId = token
        Sync.selfUser.upload()
    }

    fun shareSyncLink() = App.launch {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = Uri.Builder().appendPath("turntable://sync-request")
                .appendQueryParameter("from", Sync.deviceId)
                .build()
            putExtra(Intent.EXTRA_TEXT, uri)
        }
        startActivity(Intent.createChooser(intent, "Request sync via"))
    }

    private fun endSession() {
        Sync.mode puts Sync.Mode.None()
        // TODO: Confirm unnecessary
//        stopForeground(true)
    }


    private fun updateNotification() {
        val mode = Sync.mode.value

        // TODO: Add to same notification group as MusicService
        startForeground(70, NotificationCompat.Builder(this, "turntable").apply {
            priority = PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_sync)
            setOngoing(true)
            setAutoCancel(false)
            setContentTitle(when (mode) {
                is Sync.Mode.OneOnOne -> "Synced with ${mode.other.displayName}"
                is Sync.Mode.InGroup -> "Synced in group '${mode.group.name ?: mode.group.key}'"
                is Sync.Mode.Topic -> "Synced to topic '${mode.topic}'"
                is Sync.Mode.None -> {
                    stopForeground(true)
                    return
                }
            })
        }.build())
    }

    fun shareFriendshipLink() = App.launch {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = Uri.parse("turntable://lets-be-friends").buildUpon()
                .appendQueryParameter("id", Sync.deviceId)
                .appendQueryParameter("id", Sync.googleAccount?.displayName)
                .build()
            putExtra(Intent.EXTRA_TEXT, uri.toString())
        }
        App.instance.startActivity(Intent.createChooser(intent, "Request friendship via"))
    }


    private class Ping: Message {
        override suspend fun onReceive(sender: User) {
            inbox.offer { pings.offer(MessageDirection.RECEIVED) }
        }
    }

    private class EndSync: Message {
        override suspend fun onReceive(sender: User) {
            val mode = Sync.mode.value
            when (mode) {
                is Sync.Mode.OneOnOne -> App.launchWith {
                    it.toast("Sync ended by ${mode.other.name}")
                }
                is Sync.Mode.InGroup -> {
                    val name = mode.group.name ?: mode.group.key
                    App.launchWith { it.toast("Left group '$name'?") }
                }
            }
            inbox.offer { endSession() }
        }
    }

    companion object: AnkoLogger by AnkoLogger<SyncSession>() {
        /// Amount of time (milliseconds) to keep a session alive without hearing a response.
        const val TIMEOUT = 20_000L

        private val instance = ConflatedBroadcastChannel<SyncSession>()

        private val inbox = GlobalScope.actor<SyncSession.() -> Unit>(
            capacity = Channel.UNLIMITED,
            start = CoroutineStart.LAZY
        ) {
            for (e in channel) {
                val sync = instance.valueOrNull ?: run {
                    App.instance.startService<SyncSession>()
                    instance.openSubscription().first()
                }
                e.invoke(sync)
            }
        }

        // @Deprecated
        fun updateNotification() {
            inbox.offer { updateNotification() }
        }

        fun waitForResponse() {
            inbox.offer { pings.offer(MessageDirection.SENT) }
        }
    }
}

