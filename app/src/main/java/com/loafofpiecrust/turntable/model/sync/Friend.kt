package com.loafofpiecrust.turntable.model.sync

import android.app.PendingIntent
import android.content.Intent
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import com.github.ajalt.timberkt.Timber
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.serialize.page
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.days
import io.paperdb.Paper
import kotlinx.android.parcel.Parcelize
import kotlinx.collections.immutable.immutableMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.toast

@Parcelize
data class Friend(
    val user: User,
    val status: Status
) : Parcelable {
    enum class Status {
        CONFIRMED,
        SENT_REQUEST,
        RECEIVED_REQUEST
    }

    override fun hashCode(): Int = user.hashCode()
    override fun equals(other: Any?): Boolean =
        other is Friend && other.user == user

    fun respondToRequest(accept: Boolean) {
        Friend.respondToRequest(user, accept)
    }

    // Friendship
    object Request : Message {
        override val timeout get() = 27.5.days
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val context = App.instance

            // TODO: Don't add duplicate.
            // We don't know this user in any capacity yet.
            friends putsMapped { it.put(sender, Status.RECEIVED_REQUEST) }

            val n = NotificationCompat.Builder(context, "turntable").apply {
                priority = NotificationCompat.PRIORITY_DEFAULT
                setSmallIcon(R.drawable.ic_circle)
                setContentTitle("Friend request")
                setContentText("from ${sender.name}")
                setAutoCancel(true)
                setOngoing(true)

                setContentIntent(PendingIntent.getActivity(
                    context, 6978,
                    Intent(context, MainActivity::class.java)
                        .putExtra("action", MainActivity.Action.FriendRequest(sender)),
                    0
                ))
            }.build()

            context.notificationManager.notify(NOTIFICATION_ID, n)
        }

        private const val NOTIFICATION_ID = 12350

        fun clearNotifications() {
            App.instance.notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    data class Response(val accept: Boolean): Message {
        override val timeout get() = 27.5.days
        override suspend fun onReceive(sender: User) {
            val app = App.instance
            if (accept) {
                friends putsMapped { it.put(sender, Status.CONFIRMED) }
                withContext(Dispatchers.Main) {
                    app.toast(app.getString(R.string.friend_request_confirmed, sender.name))
                }
            } else {
                friends putsMapped { it.remove(sender) }
                withContext(Dispatchers.Main) {
                    app.toast(app.getString(R.string.friend_request_declined, sender.name))
                }
            }
        }
    }

    object Remove: Message {
        override suspend fun onReceive(sender: User) {
            friends putsMapped { it.remove(sender) }
        }
    }


    companion object {
        /**
         * Map of users to their friend status
         */
        val friends by Paper.page("friends") {
            immutableMapOf<User, Status>()
        }

        /**
         * List of current friends and users with pending friend requests
         */
        val friendList get() = friends.openSubscription().map {
            it.map { Friend(it.key, it.value) }
        }

        /**
         * @return whether the user wasn't already a friend
         */
        fun request(user: User): Boolean {
            val alreadyFriends = friends.value.containsKey(user)
            if (!alreadyFriends) runBlocking {
                Timber.i { "requesting friendship with ${user.displayName} at ${user.username}" }
                friends putsMapped { it.put(user, Status.SENT_REQUEST) }
                Sync.send(Request, user)
            }
            return !alreadyFriends
        }

        fun respondToRequest(user: User, accept: Boolean) {
            Sync.send(Response(accept), user)

            runBlocking {
                friends putsMapped { friends ->
                    if (accept) {
                        friends.put(user, Status.CONFIRMED)
                    } else {
                        friends.remove(user)
                    }
                }
            }
        }

        fun remove(user: User) {
            Sync.send(Remove, user)
            runBlocking {
                friends putsMapped { it.remove(user) }
            }
        }
    }
}