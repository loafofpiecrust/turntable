package com.loafofpiecrust.turntable.playlist

import android.support.v7.graphics.Palette
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.EditText
import com.github.ajalt.timberkt.Timber
import com.github.daemontus.Result
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.artist.emptyContentView
import com.loafofpiecrust.turntable.browse.RecentMixTapesFragment
import com.loafofpiecrust.turntable.model.playlist.AbstractPlaylist
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.serialize.arg
import com.loafofpiecrust.turntable.serialize.getValue
import com.loafofpiecrust.turntable.serialize.setValue
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.ui.universal.show
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.views.RecyclerListItem
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast
import kotlin.coroutines.CoroutineContext


class PlaylistsFragment: BaseFragment() {
    private var user: User by arg { Sync.selfUser }
    private var columnCount: Int by arg()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.menuItem(R.string.mixtapes_recent).onClick {
            context?.replaceMainContent(RecentMixTapesFragment(), true)
        }

        menu.menuItem(R.string.playlist_new, R.drawable.ic_add, showIcon = true).onClick {
            NewPlaylistDialog().show(requireContext(), fullscreen = true)
        }

        menu.menuItem("Import from Spotify").onClick {
            alert {
                title = "Import from Spotify"

                lateinit var urlEdit: EditText
                customView {
                    frameLayout {
                        horizontalPadding = dimen(R.dimen.text_content_margin)
                        urlEdit = editText {
                            hint = "Spotify Playlist URL"
                        }
                    }
                }

                positiveButton("Load") {
                    loadSpotifyPlaylist(urlEdit.text)
                }
                cancelButton {}
            }.show()
        }
    }

    private fun loadSpotifyPlaylist(urlText: CharSequence) {
        val pattern = Regex("^https://open.spotify.com/user/(\\d+)/playlist/([^?]+)")
        val url = pattern.find(urlText)
        if (url != null) {
            val userId = url.groupValues[1]
            val playlistId = url.groupValues[2]
            Timber.d { "Loading spotify playlist $playlistId by $userId" }
            launch(Dispatchers.IO) {
                val pl = CollaborativePlaylist.fromSpotifyPlaylist(userId, playlistId)
                when (pl) {
                    is Result.Ok -> {
                        Timber.d { "Loaded playlist ${pl.ok.tracks}" }
                        UserPrefs.playlists appends pl.ok
                    }
                    is Result.Error -> launch(Dispatchers.Main) {
                        Timber.e(pl.error) { "Failed to load Spotify playlist" }
                        toast("Failed to load Spotify playlist")
                    }
                }
            }
        } else {
            toast("Invalid Spotify playlist url")
        }
    }

    override fun ViewManager.createView() = refreshableRecyclerView {
        val playlists = if (user === Sync.selfUser) {
            UserPrefs.playlists
        } else broadcastSingle {
            AbstractPlaylist.allByUser(user)
        }

        channel = playlists.openSubscription()

        contents {
            recyclerView {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

                adapter = Adapter(coroutineContext, playlists.openSubscription()) { playlist ->
                    Timber.d { "playlist: opening '${playlist.id.name}'" }
                    activity?.replaceMainContent(
                        GeneralPlaylistDetails(playlist.id).createFragment(),
                        true
                    )
                }
            }
        }

        emptyState {
            emptyContentView(
                R.string.playlists_empty,
                R.string.playlists_empty_details
            )
        }
    }


    class Adapter(
        parentContext: CoroutineContext,
        channel: ReceiveChannel<List<Playlist>>,
        private val readOnly: Boolean = false,
        private val listener: (Playlist) -> Unit
    ): RecyclerBroadcastAdapter<Playlist, RecyclerListItem>(parentContext, channel) {
        override val dismissEnabled: Boolean
            get() = false

        override val moveEnabled: Boolean
            get() = !readOnly

        override fun canMoveItem(index: Int) = true
        override fun onItemMove(fromIdx: Int, toIdx: Int) {
            UserPrefs.playlists putsMapped { it.shifted(fromIdx, toIdx) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            RecyclerListItem(parent, 3, useIcon = true)

        override fun RecyclerListItem.onBind(item: Playlist, position: Int, job: Job) {
            val ctx = itemView.context
            val typeName = item.javaClass.localizedName(ctx)
            mainLine.text = item.id.displayName
            subLine.text = when {
                item.id.owner == null || item.id.owner == Sync.selfUser -> typeName
                item.id.owner != null -> {
                    ctx.getString(R.string.playlist_author, item.id.owner?.name, typeName)
                }
                else -> ""
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

    companion object {
        fun newInstance(
            user: User? = null,
            columnCount: Int = 0
        ) = PlaylistsFragment().apply {
            user?.let { this.user = it }
            this.columnCount = columnCount
        }
    }
}