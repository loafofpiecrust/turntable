package com.loafofpiecrust.turntable.sync

import com.github.ajalt.timberkt.Timber
import com.github.salomonbrys.kotson.fromJson
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.User
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.runBlocking

/**
 * Manages receiving messages from other users.
 */
class MessageReceiverService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage): Unit = runBlocking {
        Timber.d { "received ${msg.data}" }

        val sender = try {
            App.gson.fromJson<User>(msg.data["sender"]!!)
        } catch (e: Exception) {
            return@runBlocking
        }
//        val mode = deserialize(msg.data["mode"]!!) as Sync.Mode

        if (!msg.data.containsKey("action") || sender.deviceId == Sync.selfUser.deviceId) {
            // We sent this message ourselves, don't process it.
            // This will happen when synced in a group.
            Timber.e { "Message has no action" }
            return@runBlocking
        }

        val message = try {
            App.gson.fromJson<Message>(msg.data["action"]!!)
        } catch (e: Exception) {
            Timber.e(e) { "Unable to parse message" }
            return@runBlocking
        }

        Timber.d { "deserialized message: $message" }

        _messages.send(message to sender)
    }

    override fun onNewToken(token: String) {
        Sync.setCurrentDevice(token)
    }

    companion object {
        private val _messages = BroadcastChannel<Pair<Message, User>>(5)
        val messages get() = _messages.openSubscription()
    }
}
