package com.loafofpiecrust.turntable.sync

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.util.arg
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast

private class UserAdapter(
    val listener: (SyncService.User) -> Unit
): RecyclerAdapter<SyncService.User, RecyclerListItemOptimized>() {
    var selected: RecyclerListItemOptimized? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        = RecyclerListItemOptimized(parent, 2, false)

    override fun onBindViewHolder(holder: RecyclerListItemOptimized, position: Int) = holder.run {
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
    constructor(message: SyncService.Message, acceptText: String = "Send"): this() {
        this.message = message
        this.acceptText = acceptText
    }

    private var message: SyncService.Message by arg()
    private var acceptText: String by arg()

    private var selected: SyncService.User? = null

    override fun ViewManager.createView() = recyclerView {
        minimumHeight = dimen(R.dimen.song_item_height) * 5
        layoutManager = LinearLayoutManager(context)
        adapter = UserAdapter {
            selected = it
        }.apply {
            subscribeData(UserPrefs.friends.openSubscription().map { friends ->
                friends.filter {
                    it.status == SyncService.Friend.Status.CONFIRMED
                }.map { it.user }
            })
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = alert(R.string.friend_pick) {
        customView { createView() }

        // TODO: Prevent implicit dismissal
        positiveButton(acceptText) {
            selected?.let {
                SyncService.send(this@FriendPickerDialog.message, it)
            } ?: toast("Must choose a friend.")
        }

        cancelButton {}
    }.build() as Dialog

}