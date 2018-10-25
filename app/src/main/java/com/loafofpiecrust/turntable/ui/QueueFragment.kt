package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
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
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.model.queue.indexWithinUpNext
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.popupMenu
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.songOptions
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.RecyclerBroadcastAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.sdk27.coroutines.onClick

class QueueFragment : BaseFragment() {
    private var queueAdapter: QueueAdapter? = null
    private var songList: RecyclerView? = null

    override fun ViewManager.createView() = cardView {
        cardElevation = dimen(R.dimen.medium_elevation).toFloat()

        linearLayout {
            orientation = LinearLayout.VERTICAL
            backgroundColor = context.theme.color(android.R.attr.windowBackground)

            // Currently playing song
            val currentItem = RecyclerListItemOptimized(this, 3).also {
                addView(it.itemView)
            }
            currentItem.statusIcon.imageResource = R.drawable.ic_play_circle_outline
            UserPrefs.accentColor.consumeEach(Dispatchers.Main) {
                currentItem.mainLine.textColor = it
                currentItem.subLine.textColor = it
                currentItem.statusIcon.tint = it
            }

            textView(R.string.queue_up_next) {
                textSizeDimen = R.dimen.small_text_size
            }.lparams {
                marginStart = dip(32)
                bottomMargin = dip(4)
            }

            val linear = LinearLayoutManager(context)
            songList = recyclerView {
                layoutManager = linear
                queueAdapter = QueueAdapter()
                adapter = queueAdapter

                addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                    setDrawable(context.getDrawable(R.drawable.song_divider)!!)
                })
            }.lparams(matchParent, matchParent)

            // Hook up our UI to the queue!
            val queue = { MusicService.instance.switchMap { it?.player?.queue } }
            queueAdapter?.subscribePos(queue().map { it.position })
            queue().consumeEachAsync { q ->
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


class QueueAdapter : RecyclerBroadcastAdapter<Song, RecyclerListItemOptimized>(
    MusicService.instance.switchMap {
        it?.player?.queue
    }.map { it.list }
) {
    var queue = MusicService.instance.switchMap {
        it?.player?.queue
    }.replayOne()

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
        RecyclerListItemOptimized(parent, 3)

    override fun RecyclerListItemOptimized.onBind(item: Song, index: Int, job: Job) {
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
                itemView.context.colorAttr(android.R.attr.textColor)
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
                    given(runBlocking { MusicService.instance.filterNotNull().first() }) { music ->
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