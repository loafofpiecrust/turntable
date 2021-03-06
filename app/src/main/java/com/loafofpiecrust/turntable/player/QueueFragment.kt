package com.loafofpiecrust.turntable.player

import android.graphics.Color
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.model.queue.indexWithinUpNext
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.popupMenu
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.songOptions
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.views.RecyclerListItem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick
import kotlin.coroutines.CoroutineContext

class QueueFragment : BaseFragment() {
    private var queueAdapter: QueueAdapter? = null
    private var songList: RecyclerView? = null

    override fun ViewManager.createView() = cardView {
        cardElevation = dimen(R.dimen.top_elevation).toFloat()

        linearLayout {
            orientation = LinearLayout.VERTICAL
            backgroundColor = context.theme.color(android.R.attr.windowBackground)

            // Currently playing song
            val currentItem = RecyclerListItem(this, 3).also {
                addView(it.itemView)
            }

            currentItem.coverImage?.imageResource = R.drawable.ic_play_circle_outline
            UserPrefs.accentColor.consumeEachAsync {
                currentItem.mainLine.textColor = it
                currentItem.subLine.textColor = it
                currentItem.coverImage?.tint = it
            }

            val queue = MusicService.instance.switchMap { it?.player?.queue }.replayOne()

            currentItem.menu.onClick { v ->
                val curr = queue.valueOrNull?.current
                if (curr != null) {
                    v?.popupMenu {
                        songOptions(v.context, curr)
                    }
                }
            }

            textView(R.string.queue_up_next) {
                textSizeDimen = R.dimen.small_text_size
//                MusicService.currentSongColor.consumeEachAsync {
//                    textColor = it
//                }
            }.lparams {
                marginStart = dip(32)
                bottomMargin = dip(4)
            }


            val linear = LinearLayoutManager(context)
            songList = recyclerView {
                layoutManager = linear
                queueAdapter = QueueAdapter(coroutineContext, queue)
                adapter = queueAdapter

                addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                    setDrawable(context.getDrawable(R.drawable.song_divider)!!)
                })
            }.lparams(matchParent, matchParent)

            // Hook up our UI to the queue!
            queueAdapter?.subscribePos(queue.openSubscription().map { it.position })
            queue.consumeEachAsync { q ->
                val song = q.current
                currentItem.mainLine.text = song?.id?.displayName ?: ""
                currentItem.subLine.text = song?.id?.artist?.displayName ?: ""
                if (queueAdapter?.overscroll == true) { // collapsed
                    delay(100) // Let the queue update itself.
                    linear.scrollToPositionWithOffset(q.position + 1, 0)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        queueAdapter = null
        songList = null
    }


    fun onSheetStateChanged(state: Int) {
        when (state) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                queueAdapter?.overscroll = true
                val layout = songList?.layoutManager as LinearLayoutManager
                layout.scrollToPositionWithOffset(queueAdapter!!.currentPosition + 1, 0)
            }
            else -> {
                queueAdapter?.overscroll = false
            }
        }
    }
}


class QueueAdapter(
    parentContext: CoroutineContext,
    val queue: ConflatedBroadcastChannel<CombinedQueue>
) : RecyclerBroadcastAdapter<Song, RecyclerListItem>(
    parentContext,
    queue.openSubscription().map { it.list }
) {
    override val moveEnabled get() = true
    override val dismissEnabled get() = true

    override fun canMoveItem(index: Int) =
        queue.value.indexWithinUpNext(index)

    override fun onItemMove(fromIdx: Int, toIdx: Int) {
        MusicService.offer(PlayerAction.ShiftQueueItem(fromIdx, toIdx))
    }

    override fun onItemDismiss(idx: Int) {
        MusicService.offer(PlayerAction.RemoveFromQueue(idx))
    }

    var currentPosition: Int = 0
        private set

    var overscroll = true
        set(value) {
            if (field == value) return

            if (value) {
                notifyItemRangeInserted(data.size, OVERSCROLL_ITEMS)
            } else {
                notifyItemRangeRemoved(data.size, OVERSCROLL_ITEMS)
            }

            field = value
        }

    override fun getItemCount(): Int =
        data.size + if (overscroll) OVERSCROLL_ITEMS else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RecyclerListItem(parent, 3)

    override fun RecyclerListItem.onBind(item: Song, index: Int, job: Job) {
        coverImage?.visibility = View.GONE

        if (index >= data.size) {
            // Overscroll empty item
            track.text = ""
            mainLine.text = ""
            subLine.text = ""
            menu.visibility = View.GONE
            menu.setOnClickListener(null)
            card.setOnClickListener(null)
        } else {
            val song = data[index]

            val relPos = index - currentPosition
            if (relPos > 0) {
                track.text = String.format("+%d", relPos)
            } else {
                track.text = relPos.toString()
            }

            val c = if (relPos == 0) {
                UserPrefs.accentColor.value
            } else {
                val base = itemView.context.colorAttr(android.R.attr.textColor)
                if (relPos < 0) {
                    // dim already played tracks
                    base.withAlpha(0xa9)
                } else base
            }

            mainLine.text = song.id.displayName
            subLine.text = song.id.artist.displayName
            menu.visibility = View.VISIBLE
            mainLine.textColor = c
            subLine.textColor = c
            track.textColor = c

            if (queue.value.indexWithinUpNext(index)) {
                // TODO: Better indicator of `Up Next`
                card.backgroundColorResource = R.color.md_red_300
            } else {
                card.backgroundColor = Color.TRANSPARENT
            }

            card.onClick {
                MusicService.offer(PlayerAction.QueuePosition(index))
            }

            menu.onClick { v ->
                v?.popupMenu {
                    // TODO: Abstract this out to a MusicService method that adds queue-specific options!
                    runBlocking { MusicService.instance.filterNotNull().first() }?.let { music ->
                        val q = runBlocking { music.player.queue.first() }
                        if (q.primary is StaticQueue) {
                            menuItem(R.string.queue_remove).onClick {
                                MusicService.offer(PlayerAction.RemoveFromQueue(index))
                            }
                        }
                    }

                    songOptions(v.context, song)
                }
            }
        }
    }

    private val posJob = Job()
    fun subscribePos(channel: ReceiveChannel<Int>) {
        posJob.cancelChildren()
//        if (!channel.isEmpty) {
//            currentPosition = runBlocking { channel.receive() }
//        }
        launch(Dispatchers.Main + posJob) {
            channel.consumeEach { pos ->
                currentPosition = pos
                notifyItemRangeChanged(0, data.size)
            }
        }
    }

    override fun itemsSame(a: Song, b: Song, aIdx: Int, bIdx: Int): Boolean =
        a === b && aIdx == bIdx

    companion object {
        const val OVERSCROLL_ITEMS = 12
    }
}