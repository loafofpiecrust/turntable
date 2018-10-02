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
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.closeQuietly
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast


class SyncTabFragment: BaseFragment() {

    companion object {
        const val RESULT_PICK_CONTACT = 2
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        val choices = listOf(
            context!!.getString(R.string.friend_from_contacts) to {
                pickContact()
            },
            context!!.getString(R.string.friend_from_email) to {
                alert(R.string.friend_add) {
                    lateinit var textBox: EditText
                    customView {
                        verticalLayout {
                            padding = dimen(R.dimen.text_content_margin)
                            textBox = editText {
                                lines = 1
                                hint = "User email address"
                            }
                        }
                    }
                    positiveButton(R.string.user_befriend) {
                        val key = textBox.text.toString()
                        async {
                            val user = withContext(Dispatchers.IO) { SyncService.User.resolve(key) }
                            // TODO: Use localized strings in xml
                            toast(if (user != null) {
                                if (SyncService.requestFriendship(user)) {
                                    ctx.getString(R.string.friend_request_sent, user.displayName)
                                } else {
                                    ctx.getString(R.string.friend_request_already_added, user.displayName)
                                }
                            } else {
                                ctx.getString(R.string.user_nonexistent, key)
                            })
                        }
                    }
                    negativeButton(R.string.cancel) {}
                }.show()
            }
        )

        menu.menuItem(R.string.friend_add, R.drawable.ic_add, showIcon =true) {
            onClick {
                ctx.selector(ctx.getString(R.string.friend_add), choices)()
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
            val cursor = context!!.contentResolver.query(
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
            launch(Dispatchers.Default) {
                val user = SyncService.User.resolve(email)
                withContext(Dispatchers.Main) {
                    toast(if (user != null) {
                        if (SyncService.requestFriendship(user)) {
                            getString(R.string.friend_request_sent, user.name)
                        } else {
                            getString(R.string.friend_request_already_added, user.name)
                        }
                    } else {
                        "$name isn't on Turntable yet"
                    })
                }
            }
        }
    }

    override fun ViewManager.createView() = with(this) {
        // 'New Friend!' button

        // list of friends
        recyclerView {
            layoutManager = LinearLayoutManager(context)
            adapter = object : RecyclerAdapter<SyncService.Friend, RecyclerListItem>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    RecyclerListItem(parent, useIcon = true)

                override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
                    val friend = data[position]
                    holder.apply {
                        mainLine.text = friend.user.name
                        subLine.text = when (friend.status) {
                            SyncService.Friend.Status.CONFIRMED -> friend.user.deviceId
                            SyncService.Friend.Status.RECEIVED_REQUEST -> ctx.getString(R.string.friend_request_received)
                            SyncService.Friend.Status.SENT_REQUEST -> ctx.getString(R.string.friend_request_sent_line)
                        }
                        val choices = when (friend.status) {
                            SyncService.Friend.Status.CONFIRMED -> listOf(
                                ctx.getString(R.string.friend_request_sync) to {
                                    val user = friend.user.refresh()
                                    launch {
                                        SyncService.requestSync(user.await())
                                    }
                                    toast("Requested sync with ${friend.user.name}")
                                }
                            )
                            SyncService.Friend.Status.RECEIVED_REQUEST -> listOf(
                                ctx.getString(R.string.friend_confirm) to {
                                    friend.respondToRequest(true)
                                },
                                ctx.getString(R.string.friend_reject) to {
                                    friend.respondToRequest(false)
                                }
                            )
                            SyncService.Friend.Status.SENT_REQUEST -> listOf(
                                ctx.getString(R.string.friend_request_cancel) to {
                                    UserPrefs.friends putsMapped { it.without(position) }
                                }
                            )
                        }
                        card.onClick {
                            ctx.selector(ctx.getString(R.string.friend_do_what), choices)()
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
