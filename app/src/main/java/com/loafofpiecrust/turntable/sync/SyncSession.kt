package com.loafofpiecrust.turntable.sync

import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.ui.BaseService
import com.loafofpiecrust.turntable.util.startWith
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.jetbrains.anko.stopService
import org.jetbrains.anko.toast
import java.lang.ref.WeakReference

/**
 * When a sync session is initiated, this service is started.
 * When a [Message] is received, this service sends a [SyncSession.Ping] response back.
 * When a [Message] is sent, this service waits for any response
 * from the other user(s) in the session.
 * If a response isn't received within [SyncSession.TIMEOUT],
 * the session is ended and a [SyncSession.EndSync] message is sent.
 *
 * It runs in the foreground throughout the session, and
 * the instance may be reused for the next session if it's very soon after.
 */
class SyncSession: BaseService() {
    private var mode: Sync.Mode = Sync.Mode.None

    private enum class MessageDirection { SENT, RECEIVED }
    /// when a message hasn't been received in TIMEOUT seconds, end sync session.
    private val pings = actor<MessageDirection>(
        capacity = Channel.UNLIMITED
    ) {
        var timer: Job? = null
        var lastSent: Long = 0
        var lastReceipt: Long = 0
        for (dir in channel) when (dir) {
            // receiving a ping keeps the session alive
            MessageDirection.RECEIVED -> if (timer != null) {
                timer.cancel()
                lastReceipt = System.currentTimeMillis()
                latency.offer(lastReceipt - lastSent)
                timer = null
            }
            // Wait for a response until TIMEOUT seconds after the least-recent sent message.
            // after that timeout, end the sync session
            MessageDirection.SENT -> if (timer == null || timer.isCompleted) {
                lastSent = System.currentTimeMillis()
                // only wait for a ping if we haven't been pinged for a bit.
                if (lastSent - lastReceipt > 1000) {
                    timer = launch {
                        delay(TIMEOUT)

                        val name = when (val mode = mode) {
                            is Sync.Mode.OneOnOne -> mode.other.name
                            is Sync.Mode.InGroup -> mode.group.name
                            else -> "nobody"
                        }
                        App.launchWith { ctx ->
                            ctx.toast("Connection to $name lost")
                        }

                        end()
                    }
                }
            }
        }
    }

    /// The last interval in milliseconds between sending a message and receiving a response.
    val latency = ConflatedBroadcastChannel(0L)


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val prevMode = mode
            if (prevMode != Sync.Mode.None) {
                // Ensure that if we're switching modes we cut off the last connection.
                launch { Sync.send(EndSync, prevMode) }
                pings.offer(MessageDirection.RECEIVED)
            }
            // Either first starting a session or switching the session to a different mode.
            mode = intent.getParcelableExtra("mode")
            updateNotification()
            instance.offer(WeakReference(this))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun end() {
        val prevMode = mode
        GlobalScope.launch {
            Sync.send(EndSync, prevMode)
            if (prevMode is Sync.Mode.InGroup) {
                Sync.leaveGroup(prevMode.group)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        mode = Sync.Mode.None
        instance.offer(WeakReference(this))
    }

    private fun updateNotification() {
        // TODO: Add to same notification group as MusicService
        startForeground(70, NotificationCompat.Builder(this, "turntable").apply {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_sync)
            setOngoing(true)
            setAutoCancel(false)
            setShowWhen(false)
            setContentTitle(when (val mode = mode) {
                is Sync.Mode.OneOnOne -> "Synced with ${mode.other.name}"
                is Sync.Mode.InGroup -> "Synced in group '${mode.group.name ?: mode.group.key}'"
                is Sync.Mode.Topic -> "Synced to topic '${mode.topic}'"
                is Sync.Mode.None -> {
                    stopSelf()
                    return
                }
            })
        }.build())
    }

    private suspend fun onReceive(message: Message) {
        pings.send(SyncSession.MessageDirection.RECEIVED)

        // If we're in a consensual sync session with the sender, tell them we're still online.
        if (message != EndSync && message != Ping) {
            // Only ping if the last message was not a ping
            // This prevents an infinite ping circle, which is generally unnecessary.
            Sync.send(Ping, mode)
        }
    }


    private object Ping: Message {
        override val requiresSession: Boolean
            get() = true

        override suspend fun onReceive(sender: User) {}
    }

    private object EndSync: Message {
        override val requiresSession: Boolean
            get() = true

        override suspend fun onReceive(sender: User) = inbox.send {
            when (val mode = mode) {
                is Sync.Mode.OneOnOne -> App.launchWith {
                    it.toast("Sync ended by ${mode.other.name}")
                }
                is Sync.Mode.InGroup -> {
                    val name = mode.group.name ?: mode.group.key
                    this.mode = mode.copy(users = mode.users - sender)
                    App.launchWith { it.toast("Left group '$name'?") }
                }
            }
            stopSelf()
        }
    }

    companion object {
        /// Amount of time (milliseconds) to keep a session alive without hearing a response.
        const val TIMEOUT = 20_000L

        private val instance = ConflatedBroadcastChannel<WeakReference<SyncSession>>()

        private val isActive: Boolean get() =
            instance.valueOrNull?.get()?.isActive == true

        private val inbox = GlobalScope.actor<suspend SyncSession.() -> Unit>(
            capacity = Channel.UNLIMITED,
            start = CoroutineStart.LAZY
        ) {
            for (e in channel) {
                instance.valueOrNull?.get()?.let {
                    if (it.isActive) {
                        e.invoke(it)
                    }
                }
            }
        }

        val mode: ReceiveChannel<Sync.Mode> get() = instance.openSubscription().map {
            val session = it.get()
            session?.mode ?: Sync.Mode.None
        }.startWith(Sync.Mode.None)

        val latency get() = instance.openSubscription().switchMap {
            it.get()?.latency?.openSubscription()
        }.startWith(0)

        fun sendToActive(message: Message) = inbox.offer {
            val wasSent = Sync.send(message, mode)

            // We expect a response from every message,
            // ensuring we know whether our session is still alive.
            if (wasSent) {
                pings.offer(MessageDirection.SENT)
            }
        }

        fun start(mode: Sync.Mode) {
            val intent = Intent(App.instance, SyncSession::class.java)
            intent.putExtra("mode", mode)
            App.instance.startService(intent)
        }

        fun stop() {
            inbox.offer { end() }
        }

        fun processMessages(channel: ReceiveChannel<Pair<Message, User>>) {
            GlobalScope.launch {
                channel.consumeEach { (msg, sender) ->
                    processMessage(msg, sender)
                }
            }
        }

        private suspend fun processMessage(message: Message, sender: User) {
            val sender = if (sender.displayName == null) {
                withContext(Dispatchers.IO) {
                    tryOr(null) { User.resolve(sender.username) }
                } ?: sender
            } else sender

            if (isActive) inbox.send {
                val inSessionWithSender = when (val mode = mode) {
                    is Sync.Mode.None -> false
                    is Sync.Mode.OneOnOne ->
                        mode.other.deviceId == sender.deviceId ||
                            mode.other.username == sender.username
                    else -> TODO("Define who can send a message to me")
                }

                if (inSessionWithSender) {
                    onReceive(message)
                } else if (message.requiresSession) {
                    // If a random user sends a session-specific message,
                    // Don't process it, but send them a rejection response.
                    Sync.send(SyncSession.EndSync, sender)
                    return@send
                }

                message.onReceive(sender)
            } else if (!message.requiresSession) {
                message.onReceive(sender)
            }
        }

        fun addMember(user: User) {
            inbox.offer {
                if (mode is Sync.Mode.InGroup) {
                    val groupMode = mode as Sync.Mode.InGroup
                    mode = groupMode.copy(users = groupMode.users + user)
                } else {
                    // ???
                    // Should we turn a OneOnOne session into a group??
                }
            }
        }
    }
}