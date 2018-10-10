package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItemOptimized
import com.loafofpiecrust.turntable.util.*
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.image
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.textColor


open class SongsAdapter(
    private val category: SongsFragment.Category? = null,
    private val listener: (List<Song>, Int) -> Unit
): RecyclerAdapter<Song, RecyclerListItemOptimized>(), FastScrollRecyclerView.SectionedAdapter {
    protected val progressSubs = mutableMapOf<RecyclerListItemOptimized, Job>()

    override fun itemsSame(a: Song, b: Song, aIdx: Int, bIdx: Int) =
        a.id == b.id

    override fun getSectionName(position: Int): String
        = data[position].id.name.first().toUpperCase().toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItemOptimized =
        RecyclerListItemOptimized(parent, when (category) {
            is SongsFragment.Category.OnAlbum -> 3
            else -> 3
        })

    override fun onBindViewHolder(holder: RecyclerListItemOptimized, position: Int) {
        holder.coverImage?.image = null

        // Runs as you re-scroll
        // So, could grab directly from a stream of the albums rather than a fixed list passed in
        var position = position
        if (category is SongsFragment.Category.All) {
            if (position == 0) {
                holder.mainLine.text = holder.card.context.getString(R.string.shuffle_all)
                holder.subLine.text = ""
                holder.track.visibility = View.GONE
//                holder.playingIcon.imageResource = R.drawable.ic_shuffle
//                holder.playingIcon.visibility = View.VISIBLE
                holder.card.onClick {
                    MusicService.enact(PlayerAction.PlaySongs(data, mode = MusicPlayer.OrderMode.SHUFFLE))
                }
                return
            }
            position -= 1
        }

        val song = data[position]
        val ctx = holder.card.context

        val subs = Job(supervisor)
        progressSubs.put(holder, subs)?.cancel()

//        holder.item = song
        holder.mainLine.text = song.id.displayName
        when (category) {
            is SongsFragment.Category.All -> {
                holder.subLine.text = song.id.artist.displayName
                holder.track.visibility = View.INVISIBLE
            }
            is SongsFragment.Category.ByArtist -> {
                holder.subLine.text = song.id.album.toString()
                holder.track.visibility = View.GONE
            }
            is SongsFragment.Category.OnAlbum -> { // On an album, assume this
                holder.subLine.text = if (song.duration > 0) {
                    msToTimeString(song.duration)
                } else ""
                holder.coverImage?.visibility = View.GONE

                if (song.track > 0) {
                    holder.track.text = song.track.toString()
                } else {
                    holder.track.text = (position + 1).toString()
                }

                if (song.id.artist != song.id.album.artist) {
                    holder.subLine.text = song.id.artist.displayName
                }
                if (song.id.features.isNotEmpty()) {
                    holder.subLine.text = ctx.getString(
                        R.string.song_title_ft,
                        holder.subLine.text,
                        song.id.features.joinToString()
                    )
                }
            }
            else -> {
                holder.subLine.text = song.id.artist.displayName
//                holder.track.visibility = View.GONE
                holder.track.text = (position + 1).toString()
                // Show if listened to in recent history
            }
        }

//        if (category is SongsFragment.Category.Playlist) {
//            // TODO: Optimize playlist retrieval to not happen every time we see a new track...
//            val playlist = runBlocking { Library.instance.findPlaylist(category.uuid).first()!! }
//            if (playlist.isCompletable) {
//                holder.statusIcon.visibility = View.VISIBLE
//                UserPrefs.history.consumeEach(UI + subs) { history ->
//                    val entry = history.find { it.song.uuid == song.uuid }
//                    if (entry != null && entry.timestamp > playlist.createdTime.time) {
//                        holder.statusIcon.imageResource = R.drawable.ic_check_box
//                    } else {
//                        holder.statusIcon.imageResource = R.drawable.ic_check_box_outline_blank
//                    }
//                }
//            }
//        }

        launch(Dispatchers.Main + subs) {
            MusicService.instance.switchMap {
                it?.let {
                    combineLatest(it.player.queue, App.instance.internetStatus)
                }
            }.consumeEach { (q, internet) ->
                if (song.id == q.current?.id) {
                    // This is the currently playing song.
//                holder.playingIcon.visibility = View.VISIBLE
//                    holder.track.visibility = View.INVISIBLE

                    val c = UserPrefs.accentColor.value
                    holder.mainLine.textColor = c
                    holder.subLine.textColor = c
                    holder.track.textColor = c
                } else {
//                    holder.track.visibility = View.VISIBLE
//                holder.playingIcon.visibility = View.GONE

                    // This is any other song
//                if (song.local != null) {
//                    holder.statusIcon.visibility = View.VISIBLE
//                } else {
//                    holder.statusIcon.visibility = View.GONE
//                }

                    val isLocal = Library.instance.sourceForSong(song.id) != null
                    val c = ctx.getColorCompat(if (internet == App.InternetStatus.OFFLINE && !isLocal) {
                        R.color.text_unavailable
                    } else R.color.text)
                    holder.mainLine.textColor = c
                    holder.subLine.textColor = c
                    holder.track.textColor = c
                }
            }
        }


        val openOverflow = { v: View ->
            v.popupMenu {
                if (category is SongsFragment.Category.Playlist) {
                    given (runBlocking { ctx.library.findPlaylist(category.id).first() }) { pl ->
                        menuItem(R.string.playlist_remove_item).onClick {
                            when (pl) {
                                is MixTape -> pl.remove(category.sideIdx, position)
                                is CollaborativePlaylist -> pl.remove(position)
                            }
                        }
                    }
                }

                menuItem(R.string.queue_last).onClick {
                    MusicService.enact(PlayerAction.Enqueue(listOf(song), MusicPlayer.EnqueueMode.NEXT))
                }
                menuItem(R.string.queue_next).onClick {
                    MusicService.enact(PlayerAction.Enqueue(listOf(song), MusicPlayer.EnqueueMode.IMMEDIATELY_NEXT))
                }

                songOptions(holder.itemView.context, song)
            }
        }
        holder.menu.setOnClickListener(openOverflow)
        holder.card.setOnLongClickListener {
            openOverflow(it)
            false
        }

        holder.card.onClick {
            val pos = if (data[position] === song) position else data.indexOf(song)
            val song = data[pos]
//            if (App.instance.internetStatus.first() != App.InternetStatus.OFFLINE
//                || song.local != null
//                || Library.instance.findSong(song.uuid).first()?.local != null) {
                // Just in case something got messed up.
                listener.invoke(data, pos)
//            }
        }

//        if (category !is SongsFragment.Category.All) {
//            task(UI + subs) {
//                OnlineSearchService.instance.findDownload(song).consumeEach {
//                    it?.let { dl ->
//                        holder.progress.layoutParams = holder.progress.layoutParams.apply {
//                            width = (holder.card.measuredWidth * dl.progress).toInt()
//                        }
//                        holder.progress.refreshDrawableState()
//                    } ?: run {
//                        val lp = holder.progress.layoutParams
//                        if (lp.width > 0) {
//                            holder.progress.layoutParams = lp.apply {
//                                width = 0
//                            }
//                            holder.progress.refreshDrawableState()
//                        }
//                    }
//                }
//            }
//        }

    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        progressSubs.clear()
    }
}

