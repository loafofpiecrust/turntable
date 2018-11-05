package com.loafofpiecrust.turntable.playlist

import android.support.v7.graphics.Palette
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.EditText
import com.github.daemontus.unwrap
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.browse.RecentMixTapesFragment
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import kotlin.coroutines.CoroutineContext


class PlaylistsFragment: BaseFragment() {
    companion object {
        fun newInstance(
            user: User? = null,
            columnCount: Int = 0
        ) = PlaylistsFragment().apply {
            user?.let { this.user = it }
            this.columnCount = columnCount
        }
    }
    private var user: User by arg { Sync.selfUser }
    private var columnCount: Int by arg()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.menuItem(R.string.mixtapes_recent).onClick {
            context?.replaceMainContent(RecentMixTapesFragment(), true)
        }

        menu.menuItem(R.string.playlist_new, R.drawable.ic_add, showIcon =true).onClick {
            AddPlaylistDialog().show(requireContext(), fullscreen = true)
        }

        menu.menuItem("From Spotify").onClick {
            alert {
                title = "Playlist From Spotify"

                lateinit var userEdit: EditText
                lateinit var idEdit: EditText
                customView {
                    verticalLayout {
                        userEdit = editText {
                            hint = "User ID"
                        }
                        idEdit = editText {
                            hint = "Playlist ID"
                        }
                    }
                }

                positiveButton("Load") {
                    launch(Dispatchers.IO) {
                        UserPrefs.playlists appends CollaborativePlaylist.fromSpotifyPlaylist(userEdit.text.toString(), idEdit.text.toString()).unwrap()
                    }
                }
                cancelButton {}
            }.show()
        }
    }

    override fun ViewManager.createView() = recyclerView {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        val playlists = if (user === Sync.selfUser) {
            UserPrefs.playlists.openSubscription()
        } else produceSingle {
            AbstractPlaylist.allByUser(user)
        }
        adapter = Adapter(coroutineContext, playlists) { playlist ->
            println("playlist: opening '${playlist.id.name}'")
            activity?.supportFragmentManager?.replaceMainContent(
                GeneralPlaylistDetails(playlist.id).createFragment(),
                true
            )
        }
    }


    class Adapter(
        parentContext: CoroutineContext,
        channel: ReceiveChannel<List<Playlist>>,
        val listener: (Playlist) -> Unit
    ): RecyclerBroadcastAdapter<Playlist, RecyclerListItem>(parentContext, channel) {
        override val dismissEnabled: Boolean
            get() = false

        override val moveEnabled: Boolean
            get() = true

        override fun canMoveItem(index: Int) = true
        override fun onItemMove(fromIdx: Int, toIdx: Int) {
            UserPrefs.playlists putsMapped { it.shifted(fromIdx, toIdx) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItem
            = RecyclerListItem(parent, 3, useIcon = true)

        override fun RecyclerListItem.onBind(item: Playlist, position: Int, job: Job) {
            val ctx = itemView.context
            val typeName = item.javaClass.localizedName(ctx)
            mainLine.text = item.id.displayName
            subLine.text = if (item.id.owner == Sync.selfUser) {
                typeName
            } else {
                ctx.getString(R.string.playlist_author, item.id.owner.name, typeName)
            }
//            header.transitionName = "playlistHeader${item.name}"

            item.color?.let {
                val contrast = Palette.Swatch(it, 0).titleTextColor
                card.backgroundColor = it
                mainLine.textColor = contrast
                subLine.textColor = contrast
                menu.tint = contrast
            }

            statusIcon.imageResource = item.icon
            menu.visibility = View.GONE

            card.setOnClickListener {
                listener.invoke(item)
            }
        }

        override fun itemsSame(a: Playlist, b: Playlist, aIdx: Int, bIdx: Int): Boolean {
            return a.id.uuid == b.id.uuid
        }
    }
}