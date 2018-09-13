package com.loafofpiecrust.turntable.ui

import android.support.v7.util.DiffUtil
import com.afollestad.sectionedrecyclerview.SectionedRecyclerViewAdapter
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.android.UI
import java.util.*


abstract class SectionedRecyclerAdapter<T, R: Comparable<R>, VH: SectionedViewHolder>(
    val groupBy: (T) -> R,
    val itemsSame: SectionedRecyclerAdapter<T, R, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a === b },
    val contentsSame: SectionedRecyclerAdapter<T, R, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a == b }
): SectionedRecyclerViewAdapter<VH>() {
    protected var data: List<T> = listOf()
        set(value) {
            groupedData = value.groupBy(groupBy).toList().sortedBy { it.first }
            field = value
        }
    private val pendingUpdates = ArrayDeque<List<T>>()

    protected var groupedData: List<Pair<R, List<T>>> = listOf()

    override fun getItemCount(section: Int): Int = run {
        groupedData[section].second.size
    }

    override fun getSectionCount(): Int = groupedData.size

    open fun addItem(item: T) {
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

    private fun internalUpdate() { task {
        val newData = pendingUpdates.first ?: return@task
        if (data.isNotEmpty()) {
            val diff = DiffUtil.calculateDiff(Differ(data, newData))
            task(UI) {
                data = newData
                diff.dispatchUpdatesTo(this@SectionedRecyclerAdapter)
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
    } }

    fun replaceData(newData: List<T>) {
//        val newSize = newData.size
//        val prevSize = data.size
        synchronized(this) {
            data = newData
            notifyDataSetChanged()
        }
//        if (newSize <= 0) {
//            if (prevSize > 0) {
//                notifyItemRangeRemoved(0, prevSize)
//            } // otherwise, was empty and still is
//        } else {
//            when {
//                newSize == prevSize -> {
//                    notifyItemRangeChanged(0, newSize)
//                }
//                newSize < prevSize -> {
//                    notifyItemRangeChanged(0, newSize)
//                    notifyItemRangeRemoved(newSize, prevSize - newSize)
//                }
//                newSize > prevSize -> {
//                    if (prevSize > 0) notifyItemRangeChanged(0, prevSize)
//                    notifyItemRangeInserted(prevSize, newSize - prevSize)
//                }
//            }
//        }
    }
}