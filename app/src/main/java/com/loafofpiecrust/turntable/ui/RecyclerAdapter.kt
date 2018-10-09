package com.loafofpiecrust.turntable.ui

import android.graphics.Rect
import android.graphics.Typeface
import android.support.constraint.ConstraintSet
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.sdk27.coroutines.textChangedListener
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

// TODO: Make interface for music data types that has their shared qualities: mainly an uuid. Then, maybe that'll allow some other generalization too.
abstract class RecyclerAdapter<T, VH: RecyclerView.ViewHolder>: RecyclerView.Adapter<VH>(), CoroutineScope {
    protected val supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor

    protected var data: List<T> = listOf()
    private val pendingUpdates = ArrayDeque<List<T>>()
    protected var dataUpdateJob: Job? = null

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

//    open fun updateData(newData: List<T>, cb: () -> Unit = {}) {
//        synchronized(this) {
//            pendingUpdates.addLast(newData)
//            if (pendingUpdates.size == 1) {
//                internalUpdate()
//            }
//        }
//    }

//    private fun internalUpdate(): Unit = run {
//        val newData = pendingUpdates.first ?: return
//        if (data.isNotEmpty()) {
//            task {
//                synchronized(this) {
//                    DiffUtil.calculateDiff(Differ(data, newData))
//                }
//            }.then(UI) { diff ->
//                data = newData
//                diff.dispatchUpdatesTo(this@RecyclerAdapter)
//                pendingUpdates.removeFirst()
//                if (pendingUpdates.isNotEmpty()) {
////                if (pendingUpdates.size > 1) { // more than one update queued
////                    val lastList = pendingUpdates.last
////                    pendingUpdates.clear()
////                    pendingUpdates.add(lastList)
////                }
//                    internalUpdate()
//                }
//            }
//        } else runBlocking(UI) {
//            data = newData
//            notifyItemRangeInserted(0, newData.size)
//            pendingUpdates.removeFirst()
//            if (pendingUpdates.isNotEmpty()) {
//                internalUpdate()
//            }
//        }
//    }

    fun replaceData(newData: List<T>) = launch(Dispatchers.Main) {
        val newSize = newData.size
        val prevSize = data.size
        data = newData
        if (newSize <= 0) {
            if (prevSize > 0) {
                notifyItemRangeRemoved(0, prevSize)
            } // otherwise, was empty and still is
        } else {
            when {
                newSize == prevSize -> {
                    notifyItemRangeChanged(0, newSize)
                }
                newSize < prevSize -> {
                    notifyItemRangeChanged(0, newSize)
                    notifyItemRangeRemoved(newSize, prevSize - newSize)
                }
                newSize > prevSize -> {
                    if (prevSize > 0) notifyItemRangeChanged(0, prevSize)
                    notifyItemRangeInserted(prevSize, newSize - prevSize)
                }
            }
        }
    }

    fun subscribeData(obs: ReceiveChannel<List<T>>) {
        dataUpdateJob?.cancel()
        if (!obs.isEmpty && !obs.isClosedForReceive) {
            println("recycler restoring data?")
            data = runBlocking { obs.receive() }
        }
        dataUpdateJob = async {
            obs.consumeEach { newData ->
                val diff = DiffUtil.calculateDiff(Differ(data, newData))
                withContext(Dispatchers.Main) {
                    data = newData
                    diff.dispatchUpdatesTo(this@RecyclerAdapter)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        dataUpdateJob?.cancel()
        dataUpdateJob = null
        supervisor.cancel()
    }
}

abstract class RecyclerBroadcastAdapter<T, VH: RecyclerView.ViewHolder> : RecyclerAdapter<T, VH>() {
    private val itemJobs = mutableMapOf<VH, Job>()

    abstract val moveEnabled: Boolean
    abstract val dismissEnabled: Boolean

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
                data = data.without(position)
                notifyItemRemoved(position)
                onItemDismiss(viewHolder.adapterPosition)
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
    val mainLine: TextView = itemView.find(R.id.mainLine)
    val subLine: TextView = itemView.find(R.id.subLine)
    val card: View = itemView.find(R.id.card)
    val coverImage: ImageView? = itemView.findOptional(R.id.image)
    val header: View = itemView.findOptional(R.id.title) ?: mainLine

    open val transitionViews: List<View> get() = if (coverImage != null) {
        listOf(coverImage/*, header*/)
    } else listOf(header)

    override fun toString() = "${super.toString()} '${mainLine.text}'"
}

class RecyclerGridItem(
    parent: ViewGroup,
    maxTextLines: Int = 3,
    init: LinearLayout.() -> Unit = {}
) : RecyclerItem(
    AnkoContext.create(parent.context, parent).defaultGridItemOpt(maxTextLines, init)
)

//class RecyclerListItem(
//    parent: ViewGroup,
//    view: View
//) : RecyclerItem(
//    maxTextLines,
//    AnkoContext.create(parent.context, parent).defaultListItem(maxTextLines, useIcon)
//) {
//    val track: TextView = itemView.findViewById(R.uuid.track)
//    val popupMenu: View = itemView.findViewById(R.uuid.itemMenuDots)
//    val progress: View = itemView.findViewById(R.uuid.progressBg)
//    val playingIcon: ImageView = itemView.findViewById(R.uuid.playing_icon)
//    val statusIcon: View = itemView.findViewById(R.uuid.status_icon)
//}

class ItemOffsetDecoration(private val mItemOffset: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.set(mItemOffset, mItemOffset, mItemOffset, mItemOffset)
    }
}


fun ViewManager.defaultGridItem(maxTextLines: Int = 3, init: LinearLayout.() -> Unit = {}) = frameLayout {
    padding = dimen(R.dimen.grid_gutter) / 2
//    padding = dip(1)
    clipToPadding = false
    onApi(21) { clipToOutline = false }

    cardView {
        id = R.id.card
        cardElevation = dimen(R.dimen.low_elevation).toFloat()
//        setCardBackgroundColor(UserPrefs.primaryColor.value)
        radius = dimen(R.dimen.card_corner_radius).toFloat()

        linearLayout {
            orientation = LinearLayout.VERTICAL

            val itemBg = TypedValue()
            context.theme.resolveAttribute(R.attr.selectableItemBackground, itemBg, true)
            backgroundResource = itemBg.resourceId

            imageView(R.drawable.ic_default_album) {
                //                post { lparams(width = matchParent, height = measuredWidth) }
                id = R.id.image
                scaleType = ImageView.ScaleType.CENTER_CROP
//                    transitionName = "albumCover"
                adjustViewBounds = true
            }.lparams(width = matchParent, height = wrapContent)

            val textPadding = dimen(R.dimen.text_content_margin)
            linearLayout {
                orientation = LinearLayout.VERTICAL
                id = R.id.title
                gravity = Gravity.CENTER_VERTICAL
//                    padding = textPadding
                horizontalPadding = textPadding
                verticalPadding = textPadding / 2

                textView {
                    id = R.id.mainLine
                    textStyle = Typeface.BOLD
                    maxLines = maxTextLines - 1
                }.lparams(width=matchParent)

                textView {
                    id = R.id.subLine
                    textSizeDimen = R.dimen.small_text_size
                    maxLines = 1
                    textChangedListener {
                        afterTextChanged {
                            visibility = if (text.isEmpty()) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                        }
                    }
                }.lparams(width=matchParent)

                // Call this here for any added text or popupMenu or whatever?
                init()
            }.lparams {
                // Standardized height for the given line count
                height = (dimen(R.dimen.standard_text_size) * maxTextLines) + textPadding * 2
                width = matchParent
            }
        }
    }.lparams {
        width = matchParent
        height = matchParent
    }
}


fun ViewManager.defaultGridItemOpt(maxTextLines: Int = 3, init: LinearLayout.() -> Unit = {}) = cardView {
    id = R.id.card
    cardElevation = dimen(R.dimen.medium_elevation).toFloat()
    radius = dimen(R.dimen.card_corner_radius).toFloat()
//    lparams(width = matchParent, height = wrapContent)

    constraintLayout {
        val thumbnail = imageView(R.drawable.ic_default_album) {
            id = R.id.image
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = false
        }

        val mainLine = textView {
            id = R.id.mainLine
            textStyle = Typeface.BOLD
            maxLines = maxTextLines - 1
        }

        val subLine = textView {
            id = R.id.subLine
            textSizeDimen = R.dimen.small_text_size
            maxLines = 1
            textChangedListener {
                afterTextChanged {
                    visibility = if (text.isEmpty()) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
        }

        generateChildrenIds()
        applyConstraintSet {
            val textPadding = dimen(R.dimen.text_content_margin)
            val linesGap = dimen(R.dimen.text_lines_gap)
            thumbnail {
                connect(
                    TOP to TOP of PARENT_ID,
                    START to START of PARENT_ID,
                    END to END of PARENT_ID
                )
                size = matchConstraint
                dimensionRation = "H,1:1"
            }
            mainLine {
                connect(
                    START to START of PARENT_ID margin textPadding,
                    END to END of PARENT_ID margin textPadding,
                    TOP to BOTTOM of thumbnail margin linesGap,
                    BOTTOM to TOP of subLine
                )
                width = matchConstraint
                verticalChainStyle = ConstraintSet.CHAIN_PACKED
            }
            subLine {
                connect(
                    START to START of mainLine,
                    END to END of mainLine,
                    TOP to BOTTOM of mainLine,
                    BOTTOM to BOTTOM of PARENT_ID margin linesGap
                )
                width = matchConstraint
            }
        }
    }.lparams { size = matchParent }
}