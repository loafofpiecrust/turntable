package com.loafofpiecrust.turntable.model.sync

import android.app.PendingIntent
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.MainActivityStarter
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.toast
import java.util.concurrent.TimeUnit

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
        other is Friend && user == other.user

    fun respondToRequest(accept: Boolean) {
        if (status == Status.RECEIVED_REQUEST) {
            Sync.send(Response(accept), user)

            UserPrefs.friends putsMapped { friends ->
                if (accept) {
                    friends + (this.user to Status.CONFIRMED)
                } else {
                    friends - this.user
                }
            }
        }
    }

    // Friendship
    class Request: Message {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        override suspend fun onReceive(sender: User) = withContext(Dispatchers.Main) {
            val context = App.instance

            // TODO: Don't add duplicate.
            // We don't know this user in any capacity yet.
            UserPrefs.friends putsMapped { it + (sender to Status.RECEIVED_REQUEST) }

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

        companion object {
            private const val NOTIFICATION_ID = 12350

            fun clearNotifications() {
                App.instance.notificationManager.cancel(NOTIFICATION_ID)
            }
        }
    }

    data class Response(val accept: Boolean): Message {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        override suspend fun onReceive(sender: User) {
            val app = App.instance
            val friends = UserPrefs.friends.value
            if (accept) {
                UserPrefs.friends puts friends + (sender to Status.CONFIRMED)
                withContext(Dispatchers.Main) {
                    app.toast("Friendship fostered with ${sender.name}")
                }
            } else {
                UserPrefs.friends puts friends - sender
                withContext(Dispatchers.Main) {
                    app.toast("${sender.name} declined friendship :(")
                }
            }
        }
    }
}