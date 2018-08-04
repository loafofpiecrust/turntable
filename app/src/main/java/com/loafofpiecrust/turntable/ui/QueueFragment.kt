package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.service.MusicService2
import activitystarter.MakeActivityStarter
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Gravity
import android.view.Gravity.CENTER_VERTICAL
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.ImageView
import android.widget.TextView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.player.StaticQueue
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.experimental.channels.filterNotNull
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

@MakeActivityStarter
class QueueFragment : BaseFragment() {
    private lateinit var nowPlayingMainLine: TextView
    private lateinit var nowPlayingSubLine: TextView
    private lateinit var queueAdapter: QueueAdapter
    private var songList: RecyclerView? = null

    override fun makeView(ui: ViewManager): View {
        return ui.cardView {
            cardElevation = dimen(R.dimen.medium_elevation).toFloat()

            verticalLayout {
                backgroundColor = ctx.resources.getColor(R.color.background)

                // Currently playing song
                linearLayout {
                    gravity = CENTER_VERTICAL

                    lateinit var playIcon: ImageView
                    linearLayout {
                        gravity = Gravity.CENTER
                        playIcon = imageView(R.drawable.ic_play_circle_outline) {

                        }.lparams(dimen(R.dimen.icon_size), dimen(R.dimen.icon_size))
                    }.lparams(width=dimen(R.dimen.overflow_icon_space))

                    verticalLayout {
                        nowPlayingMainLine = textView {
                            maxLines = 2
                        }
                        nowPlayingSubLine = textView {
                            lines = 1
                            textSizeDimen = R.dimen.small_text_size
                        }
                    }
                    UserPrefs.accentColor.consumeEach(UI) {
                        nowPlayingMainLine.textColor = it
                        nowPlayingSubLine.textColor = it
                        playIcon.setColorFilter(it)
                    }
                }.lparams(width=matchParent, height=dimen(R.dimen.song_item_height))

                textView("Up Next") {
                    textSizeDimen = R.dimen.small_text_size
                }.lparams {
                    marginStart = dip(32)
                    bottomMargin = dip(4)
                }

                songList = recyclerView {
                    val linear = LinearLayoutManager(context)
                    layoutManager = linear
                    queueAdapter = QueueAdapter()
                    adapter = queueAdapter.also { adapter ->
                        val helper = ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(
                            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                            ItemTouchHelper.START or ItemTouchHelper.END
                        ) {
                            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
//                                    ctx.music.shiftQueueItem(viewHolder.adapterPosition, target.adapterPosition)
                                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                                return true
                            }

                            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                                adapter.onItemDismiss(viewHolder.adapterPosition)
//                                    ctx.music.removeFromQueue(viewHolder.adapterPosition)
                            }

                            override fun isLongPressDragEnabled() = true
                            override fun isItemViewSwipeEnabled() = true
                        })
                        helper.attachToRecyclerView(this)
                    }

                    addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                        setDrawable(resources.getDrawable(R.drawable.song_divider))
                    })
                }.lparams(
                    width = matchParent,
                    height = matchParent
                )

                MusicService.instance.filterNotNull()
                    .switchMap { it.player.queue }
                    .consumeEach(UI) { q ->
                        val song = q.current
                        nowPlayingMainLine.text = song?.id?.displayName ?: ""
                        nowPlayingSubLine.text = song?.id?.artist ?: ""
                        queueAdapter.updateData(q.list, q.position)
                        if (queueAdapter.overscroll) { // collapsed
                            delay(100) // Let the queue update itself.
                            val layout = songList?.layoutManager as LinearLayoutManager
                            layout.scrollToPositionWithOffset(q.position + 1, 0)
                        }
                    }

//                verticalLayout {
//                    lparams {
//                        width = matchParent
//                        height = matchParent
//                    }
//                    textView {
//                        text = "Currently Playing song"
//                        backgroundColor = Color.BLACK
//                    }.lparams(height=dip(60), width=matchParent)
//                }
            }
        }
    }


    fun onSheetStateChanged(state: Int) {
        when (state) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                queueAdapter.overscroll = true
                val layout = songList?.layoutManager as LinearLayoutManager
                layout.scrollToPositionWithOffset(queueAdapter.currentPosition + 1, 0)
            }
            else -> {
                queueAdapter.overscroll = false
            }
        }
    }
}


class QueueAdapter : RecyclerAdapter<Song, RecyclerListItemOptimized>(
    itemsSame = { a, b, aIdx, bIdx -> a === b && aIdx == bIdx }
) {
    companion object {
        const val OVERSCROLL_ITEMS = 12
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

    override fun getItemCount(): Int
        = data.size + if (overscroll) OVERSCROLL_ITEMS else 0

    override fun onBindViewHolder(holder: RecyclerListItemOptimized, index: Int) {
        holder.coverImage?.visibility = View.GONE
        if (index >= data.size) {
            // Overscroll empty item
            holder.track.text = ""
            holder.mainLine.text = ""
            holder.subLine.text = ""
            holder.menu.visibility = View.GONE
            holder.card.setOnClickListener {}
        } else {
            val song = data[index]
//            holder.item = song
            val relPos = index - currentPosition
            if (relPos > 0) {
                holder.track.text = String.format("+%d", relPos)
            } else {
                holder.track.text = relPos.toString()
            }
            val c = if (relPos == 0) {
                UserPrefs.accentColor.value
            } else {
                holder.itemView.context.resources.getColor(R.color.text)
            }

            holder.mainLine.text = song.id.displayName
            holder.subLine.text = song.id.artist
            holder.menu.visibility = View.VISIBLE
            holder.mainLine.textColor = c
            holder.subLine.textColor = c
            holder.track.textColor = c

            holder.card.setOnClickListener {
                MusicService.enact(SyncService.Message.QueuePosition(index))
            }

            holder.menu.setOnClickListener { v ->
                val popup = PopupMenu(
                    v.context, v, Gravity.CENTER,
                    0, R.style.AppTheme_PopupOverlay
                )
                popup.menu.apply {
                    // TODO: Abstract this out to a MusicService method that adds queue-specific options!
                    given(runBlocking { MusicService.instance.first() }) { music ->
                        val q = runBlocking { music.player.queue.first() }
                        if (q.primary is StaticQueue) {
                            menuItem("Remove from Queue").onClick {
                                MusicService.enact(SyncService.Message.RemoveFromQueue(index))
                            }
                        }
                    }
                }
                song.optionsMenu(popup.menu)
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListItemOptimized
        = RecyclerListItemOptimized(parent, 3)

    fun updateData(newSongs: List<Song>, pos: Int = currentPosition) {
        if (currentPosition != pos) {
            currentPosition = pos
            notifyItemRangeChanged(0, data.size)
        }
        super.updateData(newSongs) {}
    }
}