package com.loafofpiecrust.turntable.sync

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.views.RecyclerListItem
import com.loafofpiecrust.turntable.util.arg
import com.loafofpiecrust.turntable.util.getValue
import com.loafofpiecrust.turntable.util.lazy
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast
import kotlin.coroutines.CoroutineContext

private class UserAdapter(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<User>>,
    val listener: (User) -> Unit
): RecyclerAdapter<User, RecyclerListItem>(parentContext, channel) {
    var selected: RecyclerListItem? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        = RecyclerListItem(parent, 2, false)

    override fun onBindViewHolder(holder: RecyclerListItem, position: Int) = holder.run {
        val item = data[position]
        mainLine.text = item.displayName ?: "Untitled"
        subLine.text = item.username
        coverImage?.imageResource = R.drawable.ic_face
        card.setOnClickListener {
            selected?.card?.backgroundColor = Color.TRANSPARENT
            card.backgroundColor = UserPrefs.accentColor.value
            selected = holder
            listener(item)
        }
    }
}

class FriendPickerDialog(): BaseDialogFragment() {
    constructor(message: Message, acceptText: String = "Send"): this() {
        this.message = message
        this.acceptText = acceptText
    }

    private var message: Message by arg()
    private var acceptText: String by arg()

    private var selected: User? = null

    override fun ViewManager.createView() = recyclerView {
        minimumHeight = dimen(R.dimen.song_item_height) * 5
        layoutManager = LinearLayoutManager(context)
        val friends = Friend.friends.openSubscription().map { friends ->
            friends.lazy.filter { (user, status) ->
                status == Friend.Status.CONFIRMED
            }.map { it.key }.toList()
        }

        adapter = UserAdapter(job, friends) {
            selected = it
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = alert(R.string.friend_pick) {
        customView { createView() }

        // TODO: Prevent implicit dismissal
        positiveButton(acceptText) {
            selected?.let {
                Sync.send(this@FriendPickerDialog.message, it)
            } ?: toast("Must choose a friend.")
        }

        cancelButton {}
    }.build() as Dialog
}