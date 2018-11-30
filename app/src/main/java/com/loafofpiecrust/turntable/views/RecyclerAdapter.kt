package com.loafofpiecrust.turntable.views

import android.graphics.Rect
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.util.without
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.find
import org.jetbrains.anko.findOptional
import java.util.*
import kotlin.coroutines.CoroutineContext

// TODO: Make interface for music data types that has their shared qualities: mainly an uuid. Then, maybe that'll allow some other generalization too.
abstract class RecyclerAdapter<T, VH: RecyclerView.ViewHolder>(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<T>>
): RecyclerView.Adapter<VH>(), CoroutineScope {
    protected val supervisor = SupervisorJob(parentContext[Job])
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor

    protected var data: List<T> = emptyList()
    private val pendingUpdates = ArrayDeque<List<T>>()
    private var dataUpdateJob: Job? = null

    init {
        subscribeData(channel)
    }

    override fun getItemCount(): Int = data.size

    open fun itemsSame(a: T, b: T, aIdx: Int, bIdx: Int) = (a === b)
    open fun contentsSame(a: T, b: T, aIdx: Int, bIdx: Int) = (a == b)

    protected inner class Differ(val old: List<T>, val new: List<T>) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size

        // TODO: Provide better diffing
        /// compare existence
        override fun areItemsTheSame(oldIdx: Int, newIdx: Int)
            = itemsSame(old[oldIdx], new[newIdx], oldIdx, newIdx)

        override fun areContentsTheSame(oldIdx: Int, newIdx: Int) // compare metadata
            = contentsSame(old[oldIdx], new[newIdx], oldIdx, newIdx)
    }

    private fun subscribeData(obs: ReceiveChannel<List<T>>) {
        dataUpdateJob?.cancel()
        if (!obs.isEmpty && !obs.isClosedForReceive) {
            println("recycler restoring data? $this")
            data = runBlocking { obs.receive() }
        }
        dataUpdateJob = launch {
            obs.consumeEach { newData ->
                val diff = DiffUtil.calculateDiff(Differ(data, newData))
                withContext(Dispatchers.Main) {
                    println("recycler updating data $this")
                    data = newData
                    diff.dispatchUpdatesTo(this@RecyclerAdapter)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        supervisor.cancelChildren()
        dataUpdateJob?.cancel()
        dataUpdateJob = null
    }
}

abstract class RecyclerBroadcastAdapter<T, VH: RecyclerView.ViewHolder>(
    parentContext: CoroutineContext,
    channel: ReceiveChannel<List<T>>
) : RecyclerAdapter<T, VH>(parentContext, channel) {
    private val itemJobs = mutableMapOf<VH, Job>()

    open val moveEnabled: Boolean get() = false
    open val dismissEnabled: Boolean get() = false

    open fun onItemMove(fromIdx: Int, toIdx: Int) {}
    open fun canMoveItem(index: Int): Boolean = false
    open fun canMoveItem(fromIdx: Int, toIdx: Int): Boolean = canMoveItem(fromIdx) && canMoveItem(toIdx)
    open fun onItemDismiss(idx: Int) {}

    override fun getItemCount(): Int = data.size

    final override fun onBindViewHolder(holder: VH, position: Int) {
        val job = Job(supervisor)
        itemJobs.put(holder, job)?.cancel()
        data.getOrNull(position)?.let { item ->
            holder.onBind(item, position, job)
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        itemJobs.remove(holder)?.cancel()
    }

    final override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
    }

    abstract fun VH.onBind(item: T, position: Int, job: Job)


    private inner class TouchCallback : ItemTouchHelper.Callback() {
        private var dragFrom = -1
        private var dragTo = -1

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (canMoveItem(viewHolder.adapterPosition)) {
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.START or ItemTouchHelper.END
                )
            } else 0
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val currFrom = viewHolder.adapterPosition
            val currTo = target.adapterPosition
            if (dragFrom == -1) {
                dragFrom = currFrom
            }
            dragTo = currTo
            return if (canMoveItem(dragFrom, dragTo)) {
                data = data.shifted(currFrom, currTo)
                notifyItemMoved(currFrom, currTo)
                return true
            } else false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            if (canMoveItem(position)) {
                onItemDismiss(position)
                data = data.without(position)
                notifyItemRemoved(position)
            }
        }

        override fun isLongPressDragEnabled() = moveEnabled
        override fun isItemViewSwipeEnabled() = dismissEnabled

        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (dragFrom == -1) {
                dragFrom = current.adapterPosition
            }
            return canMoveItem(dragFrom, target.adapterPosition)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                if (canMoveItem(dragFrom, dragTo)) {
                    onItemMove(dragFrom, dragTo)
                }
            }
            dragFrom = -1
            dragTo = -1
        }
    }

    final override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        ItemTouchHelper(TouchCallback()).attachToRecyclerView(recyclerView)
    }
}

open class RecyclerItem(view: View) : SectionedViewHolder(view) {
    open val mainLine: TextView = itemView.find(R.id.mainLine)
    open val subLine: TextView = itemView.find(R.id.subLine)
    open val card: View = itemView.find(R.id.card)
    open val coverImage: ImageView? = itemView.findOptional(R.id.image)
    open val header: View by lazy {
        itemView.findOptional(R.id.title) ?: mainLine
    }

    open val transitionViews: List<View> get() = if (coverImage != null) {
        listOf(coverImage as View/*, header*/)
    } else listOf(header)

    override fun toString() = "${super.toString()} '${mainLine.text}'"
}

class RecyclerGridItem(
    parent: ViewGroup,
    maxTextLines: Int = 3
) : RecyclerItem(
    GridItemView(parent.context, maxTextLines)
//    AnkoContext.create(parent.context, parent).defaultGridItemOpt(maxTextLines, init)
) {
    val view get() = itemView as GridItemView
    override val coverImage get() = view.thumbnail
    override val mainLine get() = view.mainLine
    override val subLine get() = view.subLine
    override val card get() = view
    override val header get() = mainLine
}

class ItemOffsetDecoration(private val margin: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.set(margin, margin, margin, margin)
    }
}

