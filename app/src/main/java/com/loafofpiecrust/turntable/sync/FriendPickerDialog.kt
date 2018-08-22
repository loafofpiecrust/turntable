package com.loafofpiecrust.turntable.sync

import activitystarter.Arg
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast

private class FriendAdapter(
    val listener: (SyncService.User) -> Unit
): RecyclerAdapter<SyncService.User, RecyclerListItem>() {
    var selected: Pair<RecyclerListItem, SyncService.User>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        = RecyclerListItem(parent, 2, false)

    override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
        val item = data[position]
        holder.mainLine.text = item.displayName ?: "Untitled"
        holder.subLine.text = item.username
        holder.coverImage?.imageResource = R.drawable.ic_face
        holder.card.setOnClickListener {
            selected?.first?.card?.backgroundColor = Color.TRANSPARENT
            holder.card.backgroundColor = UserPrefs.accentColor.value
            selected = holder to item
            listener(item)
        }
    }
}

class FriendPickerDialog: BaseDialogFragment() {
    @Arg lateinit var message: SyncService.Message
    @Arg(optional = true) var acceptText: String = "Send"
    private var selected: SyncService.User? = null

    override fun ViewManager.createView(): View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?) = alert {
        customView {
            recyclerView {
                minimumHeight = dimen(R.dimen.song_item_height) * 5
                layoutManager = LinearLayoutManager(ctx)
                adapter = FriendAdapter {
                    selected = it
                }.apply {
                    subscribeData(UserPrefs.friends.openSubscription().map { it.map { it.user } })
                }
            }
        }
        positiveButton("Send") {
            given(selected) {
                SyncService.send(this@FriendPickerDialog.message, it)
                dismiss()
            } ?: toast("Must choose a friend.")
        }
        cancelButton { dismiss() }
    }.build() as Dialog

}