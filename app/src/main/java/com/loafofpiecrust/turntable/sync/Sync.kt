package com.loafofpiecrust.turntable.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.support.v4.app.FragmentActivity
import android.support.v4.app.NotificationCompat
import com.firebase.ui.auth.AuthUI
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.queue.isEmpty
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.*
import io.ktor.client.call.receive
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.tasks.await
import org.jetbrains.anko.*

/**
 * Manages sending messages both for a session and
 * general messages unrelated to a session.
 */
object Sync: AnkoLogger {
    private const val API_KEY = BuildConfig.FIREBASE_API_KEY
    private const val PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
    private const val SERVER_KEY = BuildConfig.FIREBASE_SERVER_KEY


    val selfUser = User()

    private var googleAccount: FirebaseUser? = null
        set(value) {
            field = value
            value?.let {
                selfUser.displayName = it.displayName
                val email = it.email
                if (email?.isEmpty() == false) {
                    selfUser.username = email
                }
            }
        }


    internal fun setCurrentDevice(deviceId: String) {
        selfUser.deviceId = deviceId
        selfUser.upload()
    }

    fun requestSync(other: User) {
        requestSync(null, other)
    }
    fun requestSync(mode: Mode?, other: User) {
        val song = runBlocking {
            MusicService.player.firstOrNull()?.currentSong?.firstOrNull()
        }
        send(Request(song, mode), other)
    }

    fun confirmSync(request: SentMessage<Request>) {
        val mode = request.message.mode ?: Mode.OneOnOne(request.sender)
        GlobalScope.launch {
            send(Response(true, mode), mode)
        }
        SyncSession.start(mode)
    }
    fun confirmSync(other: User) {
        send(Response(true, Mode.OneOnOne(other)), other)
        SyncSession.start(Mode.OneOnOne(other))
    }
    fun declineSync(other: User) {
        // stop ongoing session??
//        mode puts Mode.None()
        send(Response(false, Mode.OneOnOne(other)), other)
    }
    fun declineSync(request: SentMessage<Request>) {
        val mode = request.message.mode ?: Mode.OneOnOne(request.sender)
        send(Response(false, mode), request.sender)
    }

    fun requestLogin(activity: FragmentActivity) {
        val lastAcc = FirebaseAuth.getInstance().currentUser
        if (lastAcc != null) {
            login(lastAcc)
        } else GlobalScope.launch {
            val providers = listOf(
                AuthUI.IdpConfig.GoogleBuilder().build()
            )

            try {
                val acc = AuthUI.getInstance()
                    .silentSignIn(activity, providers)
                    .await()

                login(acc!!.user)
            } catch (err: Exception) {
                withContext(Dispatchers.Main) {
                    // Create and launch sign-in intent
                    activity.startActivityForResult(
                        AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                        69
                    )
                }
            }
        }
    }

    fun login(acc: FirebaseUser) {
        println("login: ${acc.email}")
        googleAccount = acc
        // TODO: Make sure an upload is unneccessary here (eg. user gets uploaded upon first login)
    }

    fun send(msg: Message, to: User) = GlobalScope.launch {
        send(msg, Mode.OneOnOne(to))
    }

    suspend fun send(msg: Message, mode: Sync.Mode): Boolean {
        val target = when (mode) {
            is Sync.Mode.None -> return false
            is Sync.Mode.OneOnOne -> mode.other.deviceId
            is Sync.Mode.InGroup -> mode.group.key
            is Sync.Mode.Topic -> "/topics/${mode.topic}"
        }

        info { "Sending $msg to $mode" }

        val res = Http.post("https://fcm.googleapis.com/fcm/send",
            headers = mapOf(
                "Authorization" to "key=$SERVER_KEY"
            ),
            body = jsonObject(
                "to" to target,
                "priority" to "high",
                "time_to_live" to msg.timeout.inSeconds().longValue,
                "data" to jsonObject(
                    "sender" to serialize(selfUser).toBase64(),
                    "action" to serialize(msg.minimize()).toBase64()
                )
            )
        )

        info { "Send response $res" }
        // TODO: Process messages that fail to send to part of a group.
        // TODO: Cache messages sent when there's no connection, then dispatch them once we regain connection.
        return res.status.value == 200
    }

    suspend fun createGroup(name: String): Sync.Group? {
        val res = Http.post("https://fcm.googleapis.com/fcm/notification",
            headers = mapOf(
                "Authorization" to "key=$API_KEY",
                "project_id" to PROJECT_ID
            ),
            body = jsonObject(
                "operation" to "create",
                "notification_key_name" to name,
                "registration_ids" to jsonArray(selfUser.deviceId)
            )
        ).gson().obj

        return if (res.has("notification_key")) {
            // the group key
            val g = Sync.Group(name, res["notification_key"].string)
            SyncSession.start(Mode.InGroup(g))
            g
        } else {
            error { "Failed to create group '$name'" }
            null
        }
    }

    suspend fun joinGroup(group: Sync.Group): Boolean {
        val res = Http.post("https://fcm.googleapis.com/fcm/notification",
            headers = mapOf(
                "Authorization" to "key=$API_KEY",
                "project_id" to PROJECT_ID
            ),
            body = jsonObject(
                "operation" to "add",
                "notification_key" to group.key,
                "notification_key_name" to group.name,
                "registration_ids" to jsonArray(selfUser.deviceId)
            ).toString()
        ).gson().obj

        return if (!res.has("notification_key")) {
            error { "Failed to join group '${group.name}'" }
//                MusicService.instance.shouldSync = false
            false
        } else {
            SyncSession.start(Mode.InGroup(group))
            true
        }
    }

    suspend fun leaveGroup(group: Sync.Group): Boolean {
        val res = Http.post("https://fcm.googleapis.com/fcm/notification",
            headers = mapOf(
                "Authorization" to "key=$API_KEY",
                "project_id" to PROJECT_ID
            ),
            body = jsonObject(
                "operation" to "remove",
                "notification_key" to group.key,
                "notification_key_name" to group.name,
                "registration_ids" to jsonArray(selfUser.deviceId)
            )
        ).gson().obj

        return if (!res.has("notification_key")) {
            error("sync: failed to leave group '${group.name}'")
            false
        } else {
            SyncSession.stop()
            true
        }
    }

    fun initDeviceId() {
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener {
            setCurrentDevice(it.token)
        }
    }


    fun shareSyncLink() = App.launch {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = Uri.Builder().appendPath("turntable://sync-request")
                .appendQueryParameter("from", selfUser.username)
                .build()
            putExtra(Intent.EXTRA_TEXT, uri)
        }
        App.instance.startActivity(Intent.createChooser(intent, "Request sync via"))
    }


    fun shareFriendshipLink() = App.launch {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = Uri.parse("turntable://lets-be-friends").buildUpon()
                .appendQueryParameter("user", selfUser.username)
                .build()
            putExtra(Intent.EXTRA_TEXT, uri.toString())
        }
        App.instance.startActivity(Intent.createChooser(intent, "Request friendship via"))
    }

    // Sync setup
    @Parcelize
    class Request(
        private val currentSong: Song?,
        val mode: Sync.Mode? = null
    ): Message, Parcelable {
        override val timeout get() = 20.minutes
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val app = App.instance
            app.notificationManager.notify(12349, NotificationCompat.Builder(app, "turntable").apply {
                priority = NotificationCompat.PRIORITY_HIGH
                setSmallIcon(R.drawable.ic_sync)
                setContentTitle("Sync request from ${sender.name}")
                setAutoCancel(true)
                setOngoing(true)

                if (currentSong != null) {
                    val title = currentSong.id.displayName
                    val artist = currentSong.id.artist.displayName
                    setContentText("Listening to $title by $artist")
                } else {
                    setContentText("Listening to nothing")
                }
                val sent = SentMessage(this@Request, sender)

                setContentIntent(PendingIntent.getActivity(
                    app, 6977,
                    Intent(app, MainActivity::class.java).putExtra("action", MainActivity.Action.SyncRequest(sent)),
                    PendingIntent.FLAG_UPDATE_CURRENT
                ))
            }.build())
        }
    }

    private class Response(
        val accept: Boolean,
        val mode: Sync.Mode
    ): Message {
        override suspend fun onReceive(sender: User) {
            val text = if (accept) {
                "Now synced with ${sender.name}"
            } else {
                "${sender.name} refused to sync"
            }

            GlobalScope.launch(Dispatchers.Main) {
                App.instance.toast(text)
            }

            if (accept) {
                if (mode is Mode.OneOnOne) {
                    // set sync mode and enable sync in MusicService
                    SyncSession.start(Mode.OneOnOne(sender))

                    // Send them our current queue
                    val player = MusicService.player.firstOrNull()
                    val queue = player?.queue?.firstOrNull()
                    if (queue != null) {
                        send(PlayerAction.ReplaceQueue(queue), sender)
                        if (!queue.isEmpty()) {
                            delay(100)
                            send(PlayerAction.SeekTo(player.currentBufferState.position), sender)
                        }
                    }
                } else if (mode is Mode.InGroup) {
                    SyncSession.addMember(sender)
                }
            }
        }
    }

    @Parcelize
    data class Group(val name: String?, val key: String): Parcelable

    sealed class Mode : Parcelable {
        @Parcelize
        object None : Mode()

        @Parcelize
        data class OneOnOne(val other: User): Mode()

        /**
         * A user can join a group after being invited by another user.
         * Any user can start a group at any time.
         * When a user agrees to join, that response is sent to the whole group.
         *
         */
        @Parcelize
        data class InGroup(
            val group: Group,
            val users: List<User> = listOf()
        ): Mode()

        @Parcelize
        data class Topic(val topic: String): Mode()
    }

    @Parcelize
    data class SentMessage<T>(
        val message: T, val sender: User
    ): Parcelable where T: Message, T: Parcelable
}