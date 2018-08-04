package com.loafofpiecrust.turntable.sync

import android.graphics.Color
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
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx
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
    lateinit var onAccept: (SyncService.User) -> Unit
    private var selected: SyncService.User? = null
    override fun makeView(parent: ViewGroup?, manager: ViewManager): View
        = manager.verticalLayout {
            recyclerView {
                layoutManager = LinearLayoutManager(ctx)
                adapter = FriendAdapter {
                    selected = it
                }.apply {
                    subscribeData(UserPrefs.friends.openSubscription().map { it.map { it.user } })
                }
            }.lparams(width = dip(250), height = dimen(R.dimen.song_item_height)*5)

            linearLayout {
                button("Cancel").onClick {
                    dismiss()
                }
                button("Send").onClick {
                    given(selected) {
                        onAccept.invoke(it)
                        dismiss()
                    } ?: toast("Must choose a friend.")
                }
                applyRecursively {
                    backgroundColor = Color.TRANSPARENT
                }
            }.lparams(width = matchParent)
        }
}