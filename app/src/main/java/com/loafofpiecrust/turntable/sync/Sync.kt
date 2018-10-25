package com.loafofpiecrust.turntable.sync

import android.app.PendingIntent
import android.content.Intent
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.changes
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.serializeToString
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.*
import java.util.concurrent.TimeUnit

/**
 * Manages sending messages
 */
object Sync: AnkoLogger {
    private const val API_KEY = BuildConfig.FIREBASE_API_KEY
    private const val PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
    private const val SERVER_KEY = BuildConfig.FIREBASE_SERVER_KEY

    val selfUser = User()

    var deviceId: String = ""
        set(value) {
            if (field != value) {
                field = value
                selfUser.deviceId = value
                selfUser.upload()
            }
        }

    internal var googleAccount: FirebaseUser? = null
        set(value) {
            field = value
            value?.let {
                selfUser.displayName = it.displayName
                given(it.email) {
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
                        if (prev is Sync.Mode.InGroup) {
                            leaveGroup()
                        } else if (prev is Sync.Mode.Topic) {
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(prev.topic)
                        }

                        if (value is Sync.Mode.Topic) {
                            FirebaseMessaging.getInstance().subscribeToTopic(value.topic)
                        }

                        SyncSession.updateNotification()
                    }
            }
        }
    }


    fun requestSync(other: User) {
        send(Request(), other)
    }
    fun confirmSync(other: User) {
        mode puts Mode.OneOnOne(other)
        send(Response(true), other)
    }
    fun declineSync(other: User) {
        mode puts Mode.None()
        send(Response(false), other)
    }

    fun login(acc: FirebaseUser) {
        println("login: ${acc.email}")
        googleAccount = acc
        // TODO: Make sure an upload is unneccessary here (eg. user gets uploaded upon first login)
    }

    fun send(msg: Message, to: User) = GlobalScope.launch {
        send(msg, Mode.OneOnOne(to.refresh().await()))
    }

    fun sendToSession(msg: Message) = GlobalScope.launch {
        val wasSent = send(msg, mode.value)

        // We expect a response from every message,
        // ensuring we know whether our session is still alive.
        if (wasSent) {
            SyncSession.waitForResponse()
        }
    }

    private suspend fun send(msg: Message, mode: Sync.Mode): Boolean {
        val target = when (mode) {
            is Sync.Mode.None -> return false
            is Sync.Mode.OneOnOne -> mode.other.deviceId
            is Sync.Mode.InGroup -> mode.group.key
            is Sync.Mode.Topic -> "/topics/${mode.topic}"
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

        info { "Send response $res" }
        // TODO: Process messages that fail to send to part of a group.
        // TODO: Cache messages sent when there's no connection, then dispatch them once we regain connection.
        return res.isSuccessful
    }

    suspend fun createGroup(name: String): Sync.Group? {
        val res = Http.post("https://android.googleapis.com/gcm/notification",
            headers = mapOf(
                "Authorization" to "key=${API_KEY}",
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
            if (mode.value is Sync.Mode.InGroup) {
                leaveGroup()
            }

            // the group key
            val g = Sync.Group(name, res["notification_key"].string)
            mode puts Sync.Mode.InGroup(g)
            g
        } else {
            error { "Failed to create group '$name'" }
            null
        }
    }

    suspend fun joinGroup(group: Sync.Group): Boolean {
        val res = Http.post("https://android.googleapis.com/gcm/notification",
            headers = mapOf(
                "Authorization" to "key=${API_KEY}",
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
            if (mode.value is Sync.Mode.InGroup) {
                leaveGroup()
            }
            mode puts Sync.Mode.InGroup(group)
            true
        }
    }

    private suspend fun leaveGroup(): Boolean {
        val mode = mode.value as? Sync.Mode.InGroup
            ?: return false
        val group = mode.group

        val res = Http.post("https://android.googleapis.com/gcm/notification",
            headers = mapOf(
                "Authorization" to "key=${API_KEY}",
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
            this.mode puts Sync.Mode.None()
            true
        }
    }

    fun disconnect() {
        mode puts Sync.Mode.None()
    }

    /**
     * @return true if the user wasn't already a friend.
     */
    fun requestFriendship(otherUser: User): Boolean {
        val existing = UserPrefs.friends.value[otherUser]
        return if (existing != null) {
            false
        } else {
            UserPrefs.friends putsMapped { it + (otherUser to Friend.Status.SENT_REQUEST) }
            send(Friend.Request(), otherUser)
            true
        }
    }

    fun initDeviceId() {
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener {
            deviceId = it.token
        }
    }

    // Sync setup
    class Request: Message {
        override val timeout get() = TimeUnit.MINUTES.toSeconds(20)
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val app = App.instance
            app.notificationManager.notify(12349, NotificationCompat.Builder(app, "turntable").apply {
                priority = NotificationCompat.PRIORITY_HIGH
                setSmallIcon(R.drawable.ic_circle)
                setContentTitle("Sync request")
                setContentText("from ${sender.name}")
                setAutoCancel(true)
                setOngoing(true)

                setContentIntent(PendingIntent.getActivity(
                    app, 6977,
                    Intent(app, MainActivity::class.java).putExtra("action", MainActivity.Action.SyncRequest(sender)),
                    PendingIntent.FLAG_UPDATE_CURRENT
                ))
            }.build())
        }
    }

    private class Response(val accept: Boolean): Message {
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val context = App.instance

            val text = if (accept) {
                "Now synced with ${sender.name}"
            } else {
                "${sender.name} refused to sync"
            }
            context.toast(text)

            if (accept) {
                // set sync mode and enable sync in MusicService
                mode puts Mode.OneOnOne(sender)
            }
        }
    }

    @Parcelize
    data class Group(val name: String?, val key: String): Parcelable

    sealed class Mode : Parcelable {
        @Parcelize
        class None: Mode()
        @Parcelize
        data class OneOnOne(val other: User): Mode()
        @Parcelize
        data class InGroup(val group: Group): Mode()
        @Parcelize
        data class Topic(val topic: String): Mode()
    }

}