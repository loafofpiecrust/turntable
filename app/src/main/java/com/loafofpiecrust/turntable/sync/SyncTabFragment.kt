package com.loafofpiecrust.turntable.sync

import android.content.Intent
import android.provider.ContactsContract
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuInflater
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.EditText
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.util.success
import com.loafofpiecrust.turntable.util.task
import com.mcxiaoke.koi.ext.closeQuietly
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.selector
import org.jetbrains.anko.support.v4.toast


class SyncTabFragment: BaseFragment() {

    companion object {
        const val RESULT_PICK_CONTACT = 2
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        val choices = listOf("From Contacts", "Email Address")
        menu.menuItem("Add friend", R.drawable.ic_add, showIcon=true) {
            onClick {
                selector("Add friend", choices) { dialog, idx ->
                    if (idx == 0) {
                        pickContact()
                    } else {
                        alert("Add friend") {
                            var textBox: EditText? = null
                            customView {
                                verticalLayout {
                                    padding = dip(16)
                                    textBox = editText {
                                        lines = 1
                                        hint = "User email address"
                                    }
                                }
                            }
                            positiveButton("Befriend") {
                                val key = textBox!!.text.toString()
                                task {
                                    SyncService.User.resolve(key)
                                }.success(UI) { user ->
                                    if (user != null) {
                                        toast("Befriending ${user.displayName}")
                                        SyncService.requestFriendship(user)
                                    } else {
                                        toast("User '$key' doesn't exist.")
                                    }
                                }
                            }
                            negativeButton("Cancel") {}
                        }.show()
                    }
                }
            }
        }
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)
        startActivityForResult(intent, RESULT_PICK_CONTACT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESULT_PICK_CONTACT && data?.data != null) {
            val cursor = ctx.contentResolver.query(
                data.data,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null, null, null
            ).apply { moveToFirst() }

            val id = cursor.stringValue(ContactsContract.Contacts._ID)
            val name = cursor.stringValue(ContactsContract.Contacts.DISPLAY_NAME)
            val email = cursor.stringValue(ContactsContract.CommonDataKinds.Email.ADDRESS)
            println("friends: adding $id:$name at $email")

            cursor.closeQuietly()
            task {
                SyncService.User.resolve(email)
            }.success(UI) {
                if (it != null) {
                    toast("Befriending $it")
//                SyncService.instance.requestFriendship(it)
                } else {
                    toast("$name isn't on Turntable yet")
                }
            }
        }
    }

    override fun makeView(ui: ViewManager) = with(ui) {
        // 'New Friend!' button

        // list of friends
        recyclerView {
            layoutManager = LinearLayoutManager(ctx)
            adapter = object: RecyclerAdapter<SyncService.Friend, RecyclerListItem>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    RecyclerListItem(parent, useIcon = true)

                override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
                    val friend = data[position]
                    holder.apply {
                        mainLine.text = friend.user.name
                        subLine.text = friend.user.deviceId
                        val choices = sortedMapOf(
                            "Request Sync" to {
                                friend.user.refresh().success {
                                    SyncService.requestSync(it)
                                }
                                toast("Requested sync with ${friend.user.name}")
                            }//,
//                            "Playlists" to {
//
//                            }
                        )
                        card.onClick {
                            val keys = choices.keys.toList()

                            selector("Do what with friend?", keys) { dialog, idx ->
                                choices[keys[idx]]!!.invoke()
                            }
                        }
//                        menu.onClick {
//                            val popup = PopupMenu(
//                                menu.context, menu, Gravity.CENTER,
//                                0, R.style.AppTheme_PopupOverlay
//                            )
//                            popup.menu.apply {
//                                when (friend.status) {
//                                    SyncService.Friend.Status.RECEIVED_REQUEST -> {
//                                        menuItem("Accept").onClick {
//                                            friend.respondToRequest(true)
//                                        }
//                                        menuItem("Reject").onClick {
//                                            friend.respondToRequest(false)
//                                        }
//                                    }
//                                }
//                            }
//                            popup.show()
//                        }
                    }
                }
            }.apply {
                subscribeData(UserPrefs.friends.openSubscription().map {
                    it.filter { it.status != SyncService.Friend.Status.SENT_REQUEST }
                })
            }
        }
    }
}