package com.loafofpiecrust.turntable.sync

//import com.fasterxml.jackson.databind.JsonNode
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.PRIORITY_DEFAULT
import android.support.v4.app.NotificationCompat.PRIORITY_HIGH
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIgnore
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
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
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.model.song.SaveableMusic
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit


class SyncService : FirebaseMessagingService() {
    companion object: AnkoLogger by AnkoLogger<SyncService>() {
        private var instance: WeakReference<SyncService>? = null
        private const val API_KEY = BuildConfig.FIREBASE_API_KEY
        private const val PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
        private const val SERVER_KEY = BuildConfig.FIREBASE_SERVER_KEY

        /// Amount of time (milliseconds) to keep a session alive without hearing a response.
        private const val TIMEOUT = 20_000

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
                it.openSubscription()
                    .changes()
                    .consumeEach(BG_POOL) { (prev, value) ->
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

        /// when a message hasn't been received in TIMEOUT seconds, end sync session.
        private enum class MessageDir { SENT, RECEIVED }
        private val pinger = actor<MessageDir>(BG_POOL) {
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
                        timer = launch(BG_POOL) {
                            delay(TIMEOUT)

                            val mode = mode.value
                            val name = when (mode) {
                                is Mode.OneOnOne -> mode.other.name
                                is Mode.InGroup -> mode.group.name
                                else -> "nobody"
                            }
                            launch(UI) {
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
        fun send(msg: Message) = send(msg, mode.value).then { wasSent ->
            if (wasSent && msg !is Message.Ping) {
                pinger.offer(MessageDir.SENT)
            }
        }

        fun send(
            msg: Message,
            mode: Mode
        ) = task {
            val target = when (mode) {
                is Mode.None -> return@task false
                is Mode.OneOnOne -> mode.other.deviceId
                is Mode.InGroup -> mode.group.key
                is Mode.Topic -> "/topics/${mode.topic}"
            }

            val ttl = when (msg) {
                is Message.Recommendation -> TimeUnit.DAYS.toSeconds(28)
                is Message.FriendRequest -> TimeUnit.DAYS.toSeconds(28)
                is Message.FriendResponse -> TimeUnit.DAYS.toSeconds(28)
                is Message.SyncRequest -> TimeUnit.HOURS.toSeconds(1)
                else -> TimeUnit.MINUTES.toSeconds(3)
            }

            val res = Http.post("https://fcm.googleapis.com/fcm/send",
                headers = mapOf(
                    "Authorization" to "key=$SERVER_KEY",
                    "Content-Type" to "application/json"
                ),
                body = jsonObject(
                    "to" to target,
                    "priority" to "high",
                    "time_to_live" to ttl,
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
                UserPrefs.friends appends Friend(otherUser, Friend.Status.SENT_REQUEST)
                send(Message.FriendRequest(), Mode.OneOnOne(otherUser))
                true
            }
        }

        fun shareFriendshipLink() = task(UI) {
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

    sealed class Message {
        internal open fun minimize(): Message = this
        /// seconds
        internal open val timeout: Long get() = TimeUnit.MINUTES.toSeconds(3)

        class Play: Message()
        class Pause: Message()
        class TogglePause: Message()
        class Stop: Message()
        data class QueuePosition(val pos: Int): Message()
        data class RelativePosition(val diff: Int): Message()
        data class Enqueue(
            val songs: List<Song>,
            val mode: MusicPlayer.EnqueueMode
        ): Message()
        data class RemoveFromQueue(val pos: Int): Message()
        data class PlaySongs(
            val songs: List<Song>,
            val pos: Int = 0,
            val mode: MusicPlayer.OrderMode = MusicPlayer.OrderMode.SEQUENTIAL
        ): Message() {
//            override fun minimize() = copy(songs = songs.map { it.minimizeForSync() })
        }
        data class SeekTo(val pos: Long): Message()
        class SyncRequest: Message() {
            override val timeout get() = TimeUnit.MINUTES.toSeconds(20)
        }
        class EndSync: Message()
        data class SyncResponse(val accept: Boolean): Message()
        class FriendRequest: Message() {
            override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        }
        data class FriendResponse(val accept: Boolean): Message() {
            override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        }
        data class Recommendation(val content: SaveableMusic): Message() {
            override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        }
        data class Playlist(val id: UUID): Message()
        class Ping: Message()
        class ClearQueue: Message()
    }


    @Parcelize
    @DynamoDBTable(tableName="TurntableUsers")
    data class User(
        @get:DynamoDBHashKey(attributeName="email")
        var username: String,
        var deviceId: String,
        var displayName: String?
    ): Parcelable {
        constructor(): this("", "", null)

        @get:DynamoDBIgnore
        val name get() = displayName?.let {
            if (it.isNotBlank()) it else username
        } ?: username

        companion object {
            fun resolve(username: String): User? = run {
                if (username.isBlank()) return null

                val db = OnlineSearchService.instance.dbMapper
                db.load(User::class.java, username)
            }
        }

        fun upload() {
            if (username.isBlank()) return

            println("sync: saving user info under $username")
            task {
                val db = OnlineSearchService.instance.dbMapper
                try {
                    db.save(this@User)
                } catch (e: Exception) {
                    task(UI) { e.printStackTrace() }
                }
            }
        }

        fun refresh() = task {
            val db = OnlineSearchService.instance.dbMapper
            val remote = db.load(User::class.java, username)
            deviceId = remote.deviceId
            displayName = remote.displayName
            this@User
        }
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

        fun respondToRequest(accept: Boolean) {
            if (status == Status.RECEIVED_REQUEST) {
                send(Message.FriendResponse(accept), Mode.OneOnOne(user))

                UserPrefs.friends putsMapped {
                    val idx = it.indexOf(this)
                    if (accept) {
                        val new = this.copy(status = Status.CONFIRMED)
                        if (idx >= 0) {
                            it.withReplaced(idx, new)
                        } else it + new
                    } else {
                        if (idx >= 0) {
                            it.without(idx)
                        } else it
                    }
                }
            } else if (status == Status.CONFIRMED) {
                send(Message.FriendResponse(accept), Mode.OneOnOne(user))
                UserPrefs.friends putsMapped { it.withoutFirst { it.user == user } }
            }
        }
    }

    fun shareSyncLink() = task(UI) {
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

        launch(BG_POOL) {
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
                } else {
                    send(Message.EndSync())
                    return@launch
                }
            }

            when (message) {
                is Message.SyncRequest -> {
                    val sender = if (sender.displayName == null) {
                        sender.refresh().await()
                    } else sender

                    launch(UI) {
                        notificationManager.notify(12349, NotificationCompat.Builder(ctx, "turntable").apply {
                            priority = PRIORITY_HIGH
                            setSmallIcon(R.drawable.ic_circle)
                            setContentTitle("Sync request")
                            setContentText("from ${sender.name}")
                            setAutoCancel(true)
                            setOngoing(true)

                            setContentIntent(PendingIntent.getActivity(
                                ctx, 6977,
                                MainActivityStarter.getIntent(ctx, MainActivity.Action.SyncRequest(sender)),
                                0
                            ))
                        }.build())
                    }
                }
                is Message.SyncResponse -> {
                    if (message.accept) {
                        // set sync mode and enable sync in MusicService
                        Companion.mode puts Mode.OneOnOne(sender)
                        task(UI) { toast("Now synced with ${sender.name}") }
                    } else {
                        task(UI) { toast("${sender.name} refused to sync") }
                    }
                }
                is Message.EndSync -> {
                    when (mode) {
                        is Mode.OneOnOne -> {
                            val name = mode.other.displayName ?: mode.other.username
                            task(UI) { toast("Sync ended by $name") }
                        }
                        is Mode.InGroup -> {
                            val name = mode.group.name ?: mode.group.key
                            task(UI) { toast("Left group '$name'?") }
                        }
                    }
                    Companion.mode puts Mode.None()
                }

                is Message.FriendRequest -> {
                    val sender = if (sender.displayName == null) {
                        sender.refresh().await()
                    } else sender

                    // TODO: Don't add duplicate.
                    if (UserPrefs.friends.value.find { it.user == sender } == null) {
                        // We don't know this user in any capacity yet.
                        UserPrefs.friends appends Friend(sender, Friend.Status.RECEIVED_REQUEST)
                    }

                    launch(UI) {
                        notificationManager.notify(12350, NotificationCompat.Builder(ctx, "turntable").apply {
                            priority = PRIORITY_DEFAULT
                            setSmallIcon(R.drawable.ic_circle)
                            setContentTitle("Friend request")
                            setContentText("from ${sender.name}")
                            setAutoCancel(true)
                            setOngoing(true)

                            setContentIntent(PendingIntent.getActivity(
                                ctx, 6978,
                                MainActivityStarter.getIntent(ctx, MainActivity.Action.FriendRequest(sender)),
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
                    val existingIdx = friends.indexOfFirst { it.user == sender }
                    if (message.accept) {
                        UserPrefs.friends puts friends.withReplaced(existingIdx, Friend(sender, Friend.Status.CONFIRMED))
                        task(UI) { toast("Friendship fostered with ${sender.name}") }
                    } else {
                        UserPrefs.friends puts friends.without(existingIdx)
                        task(UI) { toast("${sender.name} declined friendship :(") }
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

                else -> MusicService.enact(message, false)
            }
        }
    }


    private fun updateNotification() {
        val mode = mode.value

        notificationManager.notify(70, NotificationCompat.Builder(ctx, "turntable").apply {
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
