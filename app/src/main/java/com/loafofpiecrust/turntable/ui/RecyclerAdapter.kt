package com.loafofpiecrust.turntable.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
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
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.success
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.sdk25.coroutines.textChangedListener
import java.util.*

// TODO: Make interface for music data types that has their shared qualities: mainly an id. Then, maybe that'll allow some other generalization too.
abstract class RecyclerAdapter<T, VH: RecyclerView.ViewHolder>(
    val itemsSame: RecyclerAdapter<T, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a === b },
    val contentsSame: RecyclerAdapter<T, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a == b }
): RecyclerView.Adapter<VH>() {
    protected var data: List<T> = listOf()
        private set
//    private var diffTask: Job? = null
    private val pendingUpdates = ArrayDeque<List<T>>()
    private var _subscription: Job? = null

    override fun getItemCount(): Int = data.size

    open fun addItem(item: T) = synchronized(this) {
        updateData(if (pendingUpdates.isNotEmpty()) {
            pendingUpdates.last
        } else {
            data
        } + item)
    }

    private inner class Differ(val old: List<T>, val new: List<T>) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size

        // TODO: Provide better diffing
        /// compare existence
        override fun areItemsTheSame(oldIdx: Int, newIdx: Int)
            = itemsSame(old[oldIdx], new[newIdx], oldIdx, newIdx)

        override fun areContentsTheSame(oldIdx: Int, newIdx: Int) // compare metadata
            = contentsSame(old[oldIdx], new[newIdx], oldIdx, newIdx)
    }

    open fun updateData(newData: List<T>, cb: () -> Unit = {}) {
        synchronized(this) {
            pendingUpdates.addLast(newData)
            if (pendingUpdates.size == 1) {
                internalUpdate()
            }
        }
    }

    private fun internalUpdate(): Unit = run {
        val newData = pendingUpdates.first ?: return
        if (data.isNotEmpty()) {
            task {
                synchronized(this) {
                    DiffUtil.calculateDiff(Differ(data, newData))
                }
            }.success(UI) { diff ->
                data = newData
                diff.dispatchUpdatesTo(this@RecyclerAdapter)
                pendingUpdates.removeFirst()
                if (pendingUpdates.isNotEmpty()) {
//                if (pendingUpdates.size > 1) { // more than one update queued
//                    val lastList = pendingUpdates.last
//                    pendingUpdates.clear()
//                    pendingUpdates.add(lastList)
//                }
                    internalUpdate()
                }
            }
        } else {
            task(UI) {
                data = newData
                notifyItemRangeInserted(0, newData.size)
                pendingUpdates.removeFirst()
                if (pendingUpdates.isNotEmpty()) {
                    internalUpdate()
                }
            }
        }
    }

    fun replaceData(newData: List<T>) = task(UI) {
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
        _subscription?.cancel()
        _subscription = obs.consumeEach(BG_POOL) {
            updateData(it)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        _subscription?.cancel()
    }

    fun onItemMove(fromIdx: Int, toIdx: Int) {
        updateData(data.shifted(fromIdx, toIdx))
    }

    fun onItemDismiss(idx: Int) {
        updateData(data.without(idx))
    }
}

class StaticRecyclerAdapter<T, VH: RecyclerView.ViewHolder>(
    private val ctx: Context,
    private val viewHolder: VH
): RecyclerAdapter<T, VH>() {
    override fun onBindViewHolder(holder: VH, position: Int) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        ctx.kryo.copy(viewHolder)

}

open class RecyclerItem(view: View) : SectionedViewHolder(view) {
    val mainLine: TextView = itemView.findViewById(R.id.mainLine)
    val subLine: TextView = itemView.findViewById(R.id.subLine)
    val card: View = itemView.findViewById(R.id.card)
    val coverImage: ImageView? = itemView.findViewById(R.id.image)
    val header: View = itemView.findViewById(R.id.title) ?: mainLine

    open val transitionViews: List<View> get() = if (coverImage != null) {
        listOf(coverImage, header)
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
//    val track: TextView = itemView.findViewById(R.id.track)
//    val menu: View = itemView.findViewById(R.id.itemMenuDots)
//    val progress: View = itemView.findViewById(R.id.progressBg)
//    val playingIcon: ImageView = itemView.findViewById(R.id.playing_icon)
//    val statusIcon: View = itemView.findViewById(R.id.status_icon)
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
        cardElevation = dip(3).toFloat()
//        setCardBackgroundColor(UserPrefs.primaryColor.value)
        radius = dip(2).toFloat()

        verticalLayout {
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

            val textPadding = dip(16)
            verticalLayout {
                id = R.id.title
                gravity = Gravity.CENTER_VERTICAL
//                    padding = textPadding
                horizontalPadding = dip(16)
                verticalPadding = dip(8)

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

                // Call this here for any added text or menu or whatever?
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
    radius = dip(2).toFloat()
    clipToOutline = false
    lparams(width = matchParent, height = wrapContent)

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
            thumbnail {
                connect(
                    TOP to TOP of this@constraintLayout,
                    START to START of this@constraintLayout,
                    END to END of this@constraintLayout
                )
                height = matchConstraint
                width = matchConstraint
                dimensionRation = "H,1:1"
            }
            mainLine {
                connect(
                    START to START of this@constraintLayout margin dip(16),
                    END to END of this@constraintLayout margin dip(16),
                    TOP to BOTTOM of thumbnail margin dip(8)
                )
                width = matchConstraint
            }
            subLine {
                connect(
                    START to START of mainLine,
                    END to END of mainLine,
                    TOP to BOTTOM of mainLine,
                    BOTTOM to BOTTOM of this@constraintLayout margin dip(8)
                )
                width = matchConstraint
            }
        }
    }.lparams(matchParent, matchParent)
}