package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
import android.graphics.Color
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.LinearLayout
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.queue.indexWithinUpNext
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.player.StaticQueue
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.*
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
            backgroundColor = context.getColorCompat(R.color.background)

            // Currently playing song
            val currentItem = RecyclerListItemOptimized(this, 3).also {
                addView(it.itemView)
            }
            currentItem.statusIcon.imageResource = R.drawable.ic_play_circle_outline
            UserPrefs.accentColor.consumeEach(UI) {
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
                adapter = queueAdapter.also { adapter ->
                    val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                        ItemTouchHelper.START or ItemTouchHelper.END
                    ) {
                        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
//                                    ctx.music.shiftQueueItem(viewHolder.adapterPosition, target.adapterPosition)
                            adapter?.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                            return true
                        }

                        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                            adapter?.onItemDismiss(viewHolder.adapterPosition)
//                                    ctx.music.removeFromQueue(viewHolder.adapterPosition)
                        }

                        override fun isLongPressDragEnabled() = true
                        override fun isItemViewSwipeEnabled() = true
                    })
                    helper.attachToRecyclerView(this@recyclerView)
                }

                addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                    setDrawable(context.getDrawable(R.drawable.song_divider))
                })
            }.lparams(matchParent, matchParent)

            // Hook up our UI to the queue!
            val queue = { MusicService.instance.switchMap { it.player.queue } }
            queueAdapter?.subscribeData(queue().map { it.list })
            queueAdapter?.subscribePos(queue().map { it.position })
            queue().consumeEach(UI) { q ->
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


class QueueAdapter : RecyclerBroadcastAdapter<Song, RecyclerListItemOptimized>() {
    private var queue: CombinedQueue? = null
    override val channel: ReceiveChannel<List<Song>>
        get() = MusicService.instance.switchMap {
            it.player.queue
        }.map {
            queue = it
            it.list
        }

    override val moveEnabled get() = true
    override val dismissEnabled get() = true

    override fun canMoveItem(index: Int) =
        queue?.indexWithinUpNext(index) == true

    override fun onItemMove(fromIdx: Int, toIdx: Int) {
        MusicService.enact(SyncService.Message.ShiftQueueItem(fromIdx, toIdx))
    }

    override fun onItemDismiss(idx: Int) {
        MusicService.enact(SyncService.Message.RemoveFromQueue(idx))
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

    override fun onBindViewHolder(holder: RecyclerListItemOptimized, index: Int) = holder.run {
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

            val withinUpNext = queue?.indexWithinUpNext(index)
            if (withinUpNext == true) {
                // TODO: Better indicator of `Up Next`
                track.textColor = Color.RED
            }

            val relPos = index - currentPosition
            if (relPos > 0) {
                track.text = String.format("+%d", relPos)
            } else {
                track.text = relPos.toString()
            }
            val c = if (relPos == 0) {
                UserPrefs.accentColor.value
            } else {
                itemView.context.getColorCompat(R.color.text)
            }

            mainLine.text = song.id.displayName
            subLine.text = song.id.artist.displayName
            menu.visibility = View.VISIBLE
            mainLine.textColor = c
            subLine.textColor = c
            track.textColor = c

            card.onClick {
                MusicService.enact(SyncService.Message.QueuePosition(index))
            }

            menu.onClick { v ->
                v?.popupMenu {
                    // TODO: Abstract this out to a MusicService method that adds queue-specific options!
                    given(runBlocking { MusicService.instance.first() }) { music ->
                        val q = runBlocking { music.player.queue.first() }
                        if (q.primary is StaticQueue) {
                            menuItem(R.string.queue_remove).onClick {
                                MusicService.enact(SyncService.Message.RemoveFromQueue(index))
                            }
                        }
                    }

                    song.optionsMenu(v.context, this)
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
        launch(UI, parent = posJob) {
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