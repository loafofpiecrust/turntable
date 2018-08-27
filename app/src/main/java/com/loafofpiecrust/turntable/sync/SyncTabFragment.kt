package com.loafofpiecrust.turntable.sync

import android.content.Intent
import android.provider.ContactsContract
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuInflater
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.EditText
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.task
import com.loafofpiecrust.turntable.util.then
import com.mcxiaoke.koi.ext.closeQuietly
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.withContext
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
        val choices = listOf(
            "From Contacts" to {
                pickContact()
            },
            "Email Address" to {
                alert("Add friend") {
                    lateinit var textBox: EditText
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
                        val key = textBox.text.toString()
                        async(UI) {
                            val user = withContext(BG_POOL) { SyncService.User.resolve(key) }
                            // TODO: Use localized strings in xml
                            toast(if (user != null) {
                                if (SyncService.requestFriendship(user)) {
                                    "Befriending ${user.displayName}"
                                } else {
                                    "${user.displayName} is already a friend"
                                }
                            } else {
                                "User '$key' doesn't exist"
                            })
                        }
                    }
                    negativeButton("Cancel") {}
                }.show()
            }
        )

        menu.menuItem("Add friend", R.drawable.ic_add, showIcon=true) {
            onClick {
                ctx.selector("Add friend", choices)()
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
            }.then(UI) { user ->
                if (user != null) {
                    if (SyncService.requestFriendship(user)) {
                        "Befriending ${user.name}"
                    } else {
                        "${user.name} is already a friend"
                    }
                } else {
                    toast("$name isn't on Turntable yet")
                }
            }
        }
    }

    override fun ViewManager.createView() = with(this) {
        // 'New Friend!' button

        // list of friends
        recyclerView {
            layoutManager = LinearLayoutManager(ctx)
            adapter = object : RecyclerAdapter<SyncService.Friend, RecyclerListItem>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    RecyclerListItem(parent, useIcon = true)

                override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
                    val friend = data[position]
                    holder.apply {
                        mainLine.text = friend.user.name
                        subLine.text = when (friend.status) {
                            SyncService.Friend.Status.CONFIRMED -> friend.user.deviceId
                            SyncService.Friend.Status.RECEIVED_REQUEST -> "Wants to be friends"
                            SyncService.Friend.Status.SENT_REQUEST -> "Friendship request sent"
                        }
                        val choices = when (friend.status) {
                            SyncService.Friend.Status.CONFIRMED -> listOf(
                                "Request Sync" to {
                                    friend.user.refresh().then {
                                        SyncService.requestSync(it)
                                    }
                                    toast("Requested sync with ${friend.user.name}")
                                }
                            )
                            SyncService.Friend.Status.RECEIVED_REQUEST -> listOf(
                                "Confirm Friendship" to {
                                    friend.respondToRequest(true)
                                },
                                "Reject Friendship" to {
                                    friend.respondToRequest(false)
                                }
                            )
                            SyncService.Friend.Status.SENT_REQUEST -> listOf(
                                "Rescind Request" to {
                                    UserPrefs.friends putsMapped { it.without(position) }
                                }
                            )
                        }
                        card.onClick {
                            ctx.selector("Do what with friend?", choices)()
                        }
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
