package com.loafofpiecrust.turntable.sync

//import com.fasterxml.jackson.databind.JsonNode
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.PRIORITY_DEFAULT
import android.support.v4.app.NotificationCompat.PRIORITY_HIGH
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import java.lang.ref.WeakReference
import kotlin.coroutines.experimental.CoroutineContext


class SyncService : FirebaseMessagingService(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = SupervisorJob()

    companion object: AnkoLogger by AnkoLogger<SyncService>() {
        private var instance: WeakReference<SyncService>? = null
        private const val API_KEY = BuildConfig.FIREBASE_API_KEY
        private const val PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
        private const val SERVER_KEY = BuildConfig.FIREBASE_SERVER_KEY

        /// Amount of time (milliseconds) to keep a session alive without hearing a response.
        private const val TIMEOUT = 20_000L

        val selfUser = User()

        var deviceId: String = ""
            set(value) {
                if (field != value) {
                    field = value
                    selfUser.deviceId = value
                    selfUser.upload()
                }
            }

        private var googleAccount: FirebaseUser? = null
            set(value) {
                field = value
                value?.let {
                    selfUser.displayName = it.displayName
                    given (it.email) {
                        if (it.isNotEmpty()) selfUser.username = it
                    }
                }
            }

        val mode: ConflatedBroadcastChannel<Mode> by lazy {
            ConflatedBroadcastChannel<Mode>(Mode.None()).also {
                GlobalScope.launch {
                    it.openSubscription()
                        .changes()
                        .consumeEach { (prev, value) ->
                            if (prev is Mode.InGroup) {
                                leaveGroup()
                            } else if (prev is Mode.Topic) {
                                FirebaseMessaging.getInstance().unsubscribeFromTopic(prev.topic)
                            }

                            if (value is Mode.Topic) {
                                FirebaseMessaging.getInstance().subscribeToTopic(value.topic)
                            }
                            instance?.get()?.updateNotification()
                        }
                }
            }
        }

        /// when a message hasn't been received in TIMEOUT seconds, end sync session.
        private enum class MessageDir { SENT, RECEIVED }
        private val pinger = GlobalScope.actor<MessageDir> {
            var timer: Job? = null
            var lastSent: Long = 0
            consumeEach { dir -> when (dir) {
                // receiving a ping keeps the session alive
                MessageDir.RECEIVED -> if (timer?.cancel() == true) {
                    latency puts System.currentTimeMillis() - lastSent
                }
                // after some timeout, end the sync session
                MessageDir.SENT -> {
                    if (timer == null || timer!!.isCompleted) {
                        lastSent = System.currentTimeMillis()
                        timer = launch {
                            delay(TIMEOUT)

                            val mode = mode.value
                            val name = when (mode) {
                                is Mode.OneOnOne -> mode.other.name
                                is Mode.InGroup -> mode.group.name
                                else -> "nobody"
                            }
                            launch(Dispatchers.Main) {
                                App.instance.toast("Connection to $name lost")
                            }
                            Companion.mode puts Mode.None()
                        }
                    }
                }
            } }
        }

        val latency = ConflatedBroadcastChannel(0L)


        fun login(acc: FirebaseUser) {
            println("login: ${acc.email}")
            googleAccount = acc
            // TODO: Make sure an upload is unneccessary here (eg. user gets uploaded upon first login)
        }

        fun send(msg: Message, to: User) = send(msg, Mode.OneOnOne(to))
        fun send(msg: Message) = GlobalScope.async {
            val wasSent = send(msg, mode.value).await()
            if (wasSent && msg !is Message.Ping) {
                pinger.offer(MessageDir.SENT)
            }
        }

        fun send(
            msg: Message,
            mode: Mode
        ) = GlobalScope.async {
            val target = when (mode) {
                is Mode.None -> return@async false
                is Mode.OneOnOne -> mode.other.deviceId
                is Mode.InGroup -> mode.group.key
                is Mode.Topic -> "/topics/${mode.topic}"
            }

            val res = Http.post("https://fcm.googleapis.com/fcm/send",
                headers = mapOf(
                    "Authorization" to "key=$SERVER_KEY",
                    "Content-Type" to "application/json"
                ),
                body = jsonObject(
                    "to" to target,
                    "priority" to "high",
                    "time_to_live" to msg.timeout,
                    "data" to jsonObject(
                        "sender" to serializeToString(selfUser),
                        "action" to serializeToString(msg.minimize())
                    )
                )
            )

            debug { "Send response $res" }
            // TODO: Process messages that fail to send to part of a group.
            // TODO: Cache messages sent when there's no connection, then dispatch them once we regain connection.
            true
        }

        suspend fun createGroup(name: String): Group? {
            val res = Http.post("https://android.googleapis.com/gcm/notification",
                headers = mapOf(
                    "Authorization" to "key=$API_KEY",
                    "project_id" to PROJECT_ID,
                    "Content-Type" to "application/json"
                ),
                body = jsonObject(
                    "operation" to "create",
                    "notification_key_name" to name,
                    "registration_ids" to jsonArray(deviceId)
                )
            ).gson.obj

            return if (res.has("notification_key")) {
                if (mode.value is Mode.InGroup) {
                    leaveGroup()
                }

                // the group key
                val g = Group(name, res["notification_key"].string)
                mode puts Mode.InGroup(g)
                g
            } else {
                error { "Failed to create group '$name'" }
                null
            }
        }

        suspend fun joinGroup(group: Group): Boolean {
            val res = Http.post("https://android.googleapis.com/gcm/notification",
                headers = mapOf(
                    "Authorization" to "key=$API_KEY",
                    "project_id" to PROJECT_ID,
                    "content-type" to "application/json"
                ),
                body = jsonObject(
                    "operation" to "add",
                    "notification_key" to group.key,
                    "notification_key_name" to group.name,
                    "registration_ids" to jsonArray(deviceId)
                ).toString()
            ).gson.obj

            return if (!res.has("notification_key")) {
                error { "Failed to join group '${group.name}'" }
//                MusicService.instance.shouldSync = false
                false
            } else {
                if (mode.value is Mode.InGroup) {
                    leaveGroup()
                }
                mode puts Mode.InGroup(group)
                true
            }
        }

        private suspend fun leaveGroup(): Boolean {
            val mode = mode.value as? Mode.InGroup
                ?: return false
            val group = mode.group

            val res = Http.post("https://android.googleapis.com/gcm/notification",
                headers = mapOf(
                    "Authorization" to "key=$API_KEY",
                    "project_id" to PROJECT_ID,
                    "content-type" to "application/json"
                ),
                body = jsonObject(
                    "operation" to "remove",
                    "notification_key" to group.key,
                    "notification_key_name" to group.name,
                    "registration_ids" to jsonArray(deviceId)
                )
            ).gson.obj

            return if (!res.has("notification_key")) {
                println("sync: failed to leave group '${group.name}'")
                false
            } else {
                Companion.mode puts Mode.None()
                true
            }
        }

        fun disconnect() {
            mode puts Mode.None()
        }


        fun requestSync(other: User) {
            send(Message.SyncRequest(), other)
        }
        fun confirmSync(other: User) {
            mode puts Mode.OneOnOne(other)
            send(Message.SyncResponse(true), other)
        }

        /**
         * @return true if the user wasn't already a friend.
         */
        fun requestFriendship(otherUser: User): Boolean {
            val existing = UserPrefs.friends.value.find { it.user == otherUser }
            return if (existing != null) {
                false
            } else {
                UserPrefs.friends putsMapped { it + Friend(otherUser, Friend.Status.SENT_REQUEST) }
                send(Message.FriendRequest(), Mode.OneOnOne(otherUser))
                true
            }
        }

        fun shareFriendshipLink() = App.launch {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val uri = Uri.parse("turntable://lets-be-friends").buildUpon()
                        .appendQueryParameter("id", deviceId)
                        .appendQueryParameter("id", googleAccount?.displayName)
                        .build()
                putExtra(Intent.EXTRA_TEXT, uri.toString())
            }
            App.instance.startActivity(Intent.createChooser(intent, "Request friendship via"))
        }


        fun initDeviceId() {
            FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener {
                deviceId = it.token
            }
        }
    }



    init {
        instance = WeakReference(this)
    }

    @Parcelize
    data class Group(val name: String?, val key: String): Parcelable

    sealed class Mode : Parcelable {
        @Parcelize class None: Mode()
        @Parcelize data class OneOnOne(val other: User): Mode()
        @Parcelize data class InGroup(val group: Group): Mode()
        @Parcelize data class Topic(val topic: String): Mode()
    }


    @Parcelize
    data class Friend(
        val user: User,
        val status: Status
    ): Parcelable {
        enum class Status {
            CONFIRMED,
            SENT_REQUEST,
            RECEIVED_REQUEST
        }

        override fun hashCode() = user.hashCode()

        fun respondToRequest(accept: Boolean) {
            if (status == Status.RECEIVED_REQUEST) {
                send(Message.FriendResponse(accept), Mode.OneOnOne(user))

                UserPrefs.friends putsMapped {
                    if (accept) {
                        it + this.copy(status = Status.CONFIRMED)
                    } else {
                        it - this
                    }
                }
            } else if (status == Status.CONFIRMED) {
                send(Message.FriendResponse(accept), Mode.OneOnOne(user))
                UserPrefs.friends putsMapped { it - this }
            }
        }
    }

    fun shareSyncLink() = App.launch {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = Uri.Builder().appendPath("turntable://sync-request")
                .appendQueryParameter("from", deviceId)
                .build()
            putExtra(Intent.EXTRA_TEXT, uri)
        }
        startActivity(Intent.createChooser(intent, "Request sync via"))
    }


    override fun onMessageReceived(msg: RemoteMessage) {
        val mode = mode.value

        launch {
            val sender = deserialize<User>(msg.data["sender"]!!)

            if (!msg.data.containsKey("action") || sender.deviceId == selfUser.deviceId /*|| mode is Mode.None*/) {
                // We sent this message ourselves, don't process it.
                return@launch
            }

            val message = deserialize<Message>(msg.data["action"]!!)
            println("sync: received firebase msg, $message from $sender")

            // If we're in a consensual sync session with the sender, tell them we're still online.
            // Otherwise, don't even process the message, but send a rejection response.
            if (mode is Mode.OneOnOne && message !is Message.EndSync) {
                if (mode.other.deviceId == sender.deviceId) {
                    // Only ping if the last message was not a ping
                    // This prevents an infinite ping circle, which is generally unnecessary.
                    if (message !is Message.Ping) {
                        send(Message.Ping())
                    }
                } else if (message is PlayerAction) {
                    send(Message.EndSync())
                    return@launch
                }
            }

            when (message) {
                is Message.SyncRequest -> {
                    val sender = if (sender.displayName == null) {
                        sender.refresh().await()
                    } else sender

                    launch(Dispatchers.Main) {
                        notificationManager.notify(12349, NotificationCompat.Builder(this@SyncService, "turntable").apply {
                            priority = PRIORITY_HIGH
                            setSmallIcon(R.drawable.ic_circle)
                            setContentTitle("Sync request")
                            setContentText("from ${sender.name}")
                            setAutoCancel(true)
                            setOngoing(true)

                            setContentIntent(PendingIntent.getActivity(
                                this@SyncService, 6977,
                                MainActivityStarter.getIntent(this@SyncService, MainActivity.Action.SyncRequest(sender)),
                                0
                            ))
                        }.build())
                    }
                }
                is Message.SyncResponse -> {
                    if (message.accept) {
                        // set sync mode and enable sync in MusicService
                        Companion.mode puts Mode.OneOnOne(sender)
                    }

                    launch(Dispatchers.Main) {
                        val text = if (message.accept) {
                            "Now synced with ${sender.name}"
                        } else {
                            "${sender.name} refused to sync"
                        }
                        toast(text)
                    }
                }
                is Message.EndSync -> {
                    when (mode) {
                        is Mode.OneOnOne -> {
                            val name = mode.other.displayName ?: mode.other.username
                            launch(Dispatchers.Main) { toast("Sync ended by $name") }
                        }
                        is Mode.InGroup -> {
                            val name = mode.group.name ?: mode.group.key
                            launch(Dispatchers.Main) { toast("Left group '$name'?") }
                        }
                    }
                    Companion.mode puts Mode.None()
                }

                is Message.FriendRequest -> {
                    val sender = if (sender.displayName == null) {
                        sender.refresh().await()
                    } else sender

                    // TODO: Don't add duplicate.
                    if (!UserPrefs.friends.value.any { it.user == sender }) {
                        // We don't know this user in any capacity yet.
                        UserPrefs.friends putsMapped { it + Friend(sender, Friend.Status.RECEIVED_REQUEST) }
                    }

                    launch(Dispatchers.Main) {
                        notificationManager.notify(12350, NotificationCompat.Builder(this@SyncService, "turntable").apply {
                            priority = PRIORITY_DEFAULT
                            setSmallIcon(R.drawable.ic_circle)
                            setContentTitle("Friend request")
                            setContentText("from ${sender.name}")
                            setAutoCancel(true)
                            setOngoing(true)

                            setContentIntent(PendingIntent.getActivity(
                                this@SyncService, 6978,
                                MainActivityStarter.getIntent(this@SyncService, MainActivity.Action.FriendRequest(sender)),
                                0
                            ))
                        }.build())
                    }
                }

                is Message.FriendResponse -> {
                    val sender = if (sender.displayName == null) {
                        sender.refresh().await()
                    } else sender

                    val friends = UserPrefs.friends.value
                    val newFriend = Friend(sender, Friend.Status.CONFIRMED)
                    if (message.accept) {
                        UserPrefs.friends puts friends + newFriend
                        launch(Dispatchers.Main) { toast("Friendship fostered with ${sender.name}") }
                    } else {
                        UserPrefs.friends puts friends - newFriend
                        launch(Dispatchers.Main) { toast("${sender.name} declined friendship :(") }
                    }
                }

                is Message.Recommendation -> {
                    UserPrefs.recommendations appends message.content
                }

                is Message.Ping -> {
                    pinger.offer(MessageDir.RECEIVED)
                    // Confirms that the last message sent was received.
//                lastDeliveredTime = System.currentTimeMillis()
//                latency.offer(lastDeliveredTime - lastSentTime)
                }

                is Message.Playlist -> {
                    given(CollaborativePlaylist.find(message.id)) {
                        UserPrefs.recommendations appends it
                    }
                }

                is PlayerAction -> MusicService.enact(message, false)
            }
        }
    }


    private fun updateNotification() {
        val mode = mode.value

        notificationManager.notify(70, NotificationCompat.Builder(this, "turntable").apply {
            priority = PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_refresh)
            setOngoing(true)
            setAutoCancel(false)
            setContentTitle(when (mode) {
                is Mode.OneOnOne -> "Synced with ${mode.other.displayName}"
                is Mode.InGroup -> "Synced in group '${mode.group.name ?: mode.group.key}'"
                is Mode.Topic -> "Synced to topic '${mode.topic}'"
                is Mode.None -> {
//                    stopForeground(true)
                    notificationManager.cancel(70)
                    return
                }
            })
        }.build())
    }

    override fun onNewToken(token: String) {
        deviceId = token
        selfUser.upload()
    }
}
