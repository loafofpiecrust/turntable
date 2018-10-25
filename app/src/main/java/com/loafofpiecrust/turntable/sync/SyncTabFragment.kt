package com.loafofpiecrust.turntable.sync

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.EditText
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.selector
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.then
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast


class SyncTabFragment: BaseFragment() {

    companion object {
        const val REQUEST_PICK_CONTACT = 2
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        val choices = listOf(
            getString(R.string.friend_from_contacts) to {
                requestPickContact()
            },
            getString(R.string.friend_from_email) to {
                alert {
                    titleResource = R.string.friend_add

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
                        async(Dispatchers.IO) {
                            User.resolve(key)
                        }.then(Dispatchers.Main) { user ->
                            // TODO: Use localized strings in xml
                            toast(if (user != null) {
                                if (Sync.requestFriendship(user)) {
                                    getString(R.string.friend_request_sent, user.displayName)
                                } else {
                                    getString(R.string.friend_request_already_added, user.displayName)
                                }
                            } else {
                                getString(R.string.user_nonexistent, key)
                            })
                        }
                    }
                    cancelButton {}
                }.show()
            }
        )

        menu.menuItem(R.string.friend_add, R.drawable.ic_add, showIcon = true) {
            onClick {
                selector(getString(R.string.friend_add), choices).invoke()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Friend.Request.clearNotifications()
    }

    private fun requestPickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)
        startActivityForResult(intent, REQUEST_PICK_CONTACT)
    }

    private fun pickContactFrom(uri: Uri) {
        context!!.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null, null, null
        )?.use { cursor ->
            cursor.moveToFirst()
            val id = cursor.stringValue(ContactsContract.Contacts._ID)
            val name = cursor.stringValue(ContactsContract.Contacts.DISPLAY_NAME)
            val email = cursor.stringValue(ContactsContract.CommonDataKinds.Email.ADDRESS)
            println("friends: adding $id:$name at $email")

            launch(Dispatchers.Default) {
                val user = User.resolve(email)
                withContext(Dispatchers.Main) {
                    toast(if (user != null) {
                        if (Sync.requestFriendship(user)) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == REQUEST_PICK_CONTACT && resultCode == RESULT_OK) {
            val uri = intent?.data
            // TODO: Use a contract here.
            pickContactFrom(uri!!)
        }
    }

    override fun ViewManager.createView() = with(this) {
        // 'New Friend!' button

        // list of friends
        recyclerView {
            layoutManager = LinearLayoutManager(context)
            val friends = UserPrefs.friends.openSubscription().map {
                it.map { (user, status) -> Friend(user, status) }.toList()
                //.filter { it.status != Sync.Friend.Status.SENT_REQUEST }
            }
            adapter = object : RecyclerAdapter<Friend, RecyclerListItemOptimized>(friends) {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    RecyclerListItemOptimized(parent, useIcon = true)

                override fun onBindViewHolder(holder: RecyclerListItemOptimized, position: Int) {
                    val friend = data[position]
                    holder.apply {
                        menu.visibility = View.GONE
                        mainLine.text = friend.user.name
                        subLine.text = when (friend.status) {
                            Friend.Status.CONFIRMED -> friend.user.deviceId
                            Friend.Status.RECEIVED_REQUEST -> getString(R.string.friend_request_received)
                            Friend.Status.SENT_REQUEST -> getString(R.string.friend_request_sent_line)
                        }
                        val choices = when (friend.status) {
                            Friend.Status.CONFIRMED -> listOf(
                                getString(R.string.friend_request_sync) to {
                                    val user = friend.user.refresh()
                                    launch {
                                        Sync.requestSync(user.await())
                                    }
                                    toast("Requested sync with ${friend.user.name}")
                                },
                                "Remove as Friend" to {
                                    UserPrefs.friends putsMapped { it - friend.user }
                                }
                            )
                            Friend.Status.RECEIVED_REQUEST -> listOf(
                                getString(R.string.friend_confirm) to {
                                    friend.respondToRequest(true)
                                },
                                getString(R.string.friend_reject) to {
                                    friend.respondToRequest(false)
                                }
                            )
                            Friend.Status.SENT_REQUEST -> listOf(
                                getString(R.string.friend_request_cancel) to {
                                    UserPrefs.friends putsMapped { it - friend.user }
                                },
                                "Resend Request" to {
                                    Sync.requestFriendship(friend.user)
                                }
                            )
                        }
                        card.onClick {
                            selector(getString(R.string.friend_do_what), choices).invoke()
                        }
                    }
                }
            }
        }
    }
}
