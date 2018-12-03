package com.loafofpiecrust.turntable.model.sync

import android.app.PendingIntent
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import com.chibatching.kotpref.preference
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import com.loafofpiecrust.turntable.util.days
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.withContext
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.toast

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
    override fun equals(other: Any?) =
        other is Friend && other.user == user

    fun respondToRequest(accept: Boolean) {
        Friend.respondToRequest(user, accept)
    }

    // Friendship
    object Request: Message {
        override val timeout get() = 28.days
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val context = App.instance

            // TODO: Don't add duplicate.
            // We don't know this user in any capacity yet.
            friends putsMapped { it + (sender to Status.RECEIVED_REQUEST) }

            context.notificationManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, "turntable").apply {
                priority = NotificationCompat.PRIORITY_DEFAULT
                setSmallIcon(R.drawable.ic_circle)
                setContentTitle("Friend request")
                setContentText("from ${sender.name}")
                setAutoCancel(true)
                setOngoing(true)

                setContentIntent(PendingIntent.getActivity(
                    context, 6978,
                    MainActivityStarter.getIntent(context, MainActivity.Action.FriendRequest(sender)),
                    0
                ))
            }.build())
        }

        private const val NOTIFICATION_ID = 12350

        fun clearNotifications() {
            App.instance.notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    data class Response(val accept: Boolean): Message {
        override val timeout get() = 28.days
        override suspend fun onReceive(sender: User) {
            val app = App.instance
            if (accept) {
                friends putsMapped { it + (sender to Status.CONFIRMED) }
                withContext(Dispatchers.Main) {
                    app.toast("Friendship fostered with ${sender.name}")
                }
            } else {
                friends putsMapped { it - sender }
                withContext(Dispatchers.Main) {
                    app.toast("${sender.name} declined friendship :(")
                }
            }
        }
    }

    object Remove: Message {
        override suspend fun onReceive(sender: User) {
            friends putsMapped { it - sender }
        }
    }


    companion object {
        val friends by preference(emptyMap<User, Status>())
        val friendList get() = friends.openSubscription().map {
            it.map { Friend(it.key, it.value) }
        }

        /**
         * @return true if the user wasn't already a friend.
         */
        fun request(user: User): Boolean {
            return if (!friends.value.containsKey(user)) {
                friends putsMapped { it + (user to Status.SENT_REQUEST) }
                Sync.send(Request, user)
                true
            } else false
        }

        fun respondToRequest(user: User, accept: Boolean) {
//            if (friends.value[user] == Status.RECEIVED_REQUEST) {
                Sync.send(Response(accept), user)

                friends putsMapped { friends ->
                    if (accept) {
                        friends + (user to Status.CONFIRMED)
                    } else {
                        friends - user
                    }
                }
//            }
        }

        fun remove(user: User) {
            Sync.send(Remove, user)
            friends putsMapped { it - user }
        }
    }
}