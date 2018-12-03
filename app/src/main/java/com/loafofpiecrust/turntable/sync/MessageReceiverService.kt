package com.loafofpiecrust.turntable.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.fromBase64
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error


/**
 * Manages receiving messages from other users.
 */
class MessageReceiverService : FirebaseMessagingService(), AnkoLogger {
    override fun onMessageReceived(msg: RemoteMessage) = runBlocking {
        val sender = deserialize(msg.data["sender"]!!) as User
//        val mode = deserialize(msg.data["mode"]!!) as Sync.Mode

        if (!msg.data.containsKey("action") || sender.deviceId == Sync.selfUser.deviceId) {
            // We sent this message ourselves, don't process it.
            // This will happen when synced in a group.
            error("Message has no action")
            return@runBlocking
        }

        val message = try {
            deserialize(msg.data["action"]!!.fromBase64()) as Message
        } catch (e: Exception) {
            error("Unable to parse message", e)
            return@runBlocking
        }

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

