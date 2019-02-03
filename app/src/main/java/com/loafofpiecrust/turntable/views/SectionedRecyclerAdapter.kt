package com.loafofpiecrust.turntable.views

import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.afollestad.sectionedrecyclerview.SectionedRecyclerViewAdapter
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.loafofpiecrust.turntable.util.lazy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import java.util.*
import kotlin.coroutines.CoroutineContext


abstract class SectionedRecyclerAdapter<T, R: Comparable<R>, VH: SectionedViewHolder>(
    val groupBy: (T) -> R,
    val itemsSame: SectionedRecyclerAdapter<T, R, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a === b },
    val contentsSame: SectionedRecyclerAdapter<T, R, VH>.(T, T, Int, Int) -> Boolean = { a, b, aIdx, bIdx -> a == b }
): SectionedRecyclerViewAdapter<VH>(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = SupervisorJob()

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

    private fun internalUpdate() { launch {
        val newData = pendingUpdates.first ?: return@launch
        if (data.isNotEmpty()) {
            val diff = DiffUtil.calculateDiff(Differ(data, newData))
            launch(Dispatchers.Main) {
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
            launch(Dispatchers.Main) {
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



@Suppress("UNCHECKED_CAST")
abstract class SectionedAdapter<
    T,
    K: Comparable<K>,
    IV: RecyclerView.ViewHolder,
    HV: RecyclerView.ViewHolder
>(
    parentContext: CoroutineContext,
    sections: ReceiveChannel<Map<K, List<T>>>
): RecyclerBroadcastAdapter<SectionedAdapter.Entry, RecyclerView.ViewHolder>(
    parentContext,
    sections.map {
        it.entries.sortedBy { it.key }
    }.map {
        it.lazy.flatMap { (key, items) ->
            sequenceOf(Entry.Header(key)) + items.lazy.map { Entry.Data(it) }
        }.toList()
    }
) {
    var layoutManager: GridLayoutManager
        @Deprecated("No getter", level = DeprecationLevel.ERROR)
        get() = TODO()
        set(layout) {
            val fullWidth = layout.spanCount
            layout.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(pos: Int) =
                    when (getItemViewType(pos)) {
                        1 -> fullWidth
                        else -> 1
                    }
            }
        }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 1) {
            onCreateHeaderViewHolder(parent)
        } else {
            onCreateItemViewHolder(parent)
        }
    }

    abstract fun onCreateItemViewHolder(parent: ViewGroup): IV
    abstract fun onCreateHeaderViewHolder(parent: ViewGroup): HV

    final override fun getItemViewType(position: Int) = when (data[position]) {
        is Entry.Data<*> -> 0
        is Entry.Header<*> -> 1
    }

    override fun canMoveItem(index: Int): Boolean {
        return data[index] !is Entry.Header<*>
    }


    protected fun sectionForPosition(position: Int): Int {
        var total = 0
        for (idx in 0..position) {
            if (data[idx] is Entry.Header<*>) {
                total += 1
            }
        }
        return total
    }

    private fun indexInSection(position: Int): Int {
        for (index in position downTo 0) {
            if (data[index] is Entry.Header<*>) {
                return position - index - 1
            }
        }
        return position
    }

    final override fun RecyclerView.ViewHolder.onBind(entry: Entry, position: Int, job: Job) {
        when (entry) {
            is Entry.Header<*> ->
                (this as HV).onBindHeader(entry.key as K, job)
            is Entry.Data<*> -> {
//                val sectionNumber = sectionForPosition(position)
//                val origIndex = position - sectionNumber
                val origIndex = indexInSection(position)
                (this as IV).onBindItem(entry.item as T, origIndex, job)
            }
        }
    }

    abstract fun IV.onBindItem(item: T, position: Int, job: Job)
    abstract fun HV.onBindHeader(key: K, job: Job)

    final override fun itemsSame(a: Entry, b: Entry, aIdx: Int, bIdx: Int): Boolean {
        return if (a is Entry.Header<*> && b is Entry.Header<*>) {
            a.key == b.key
        } else if (a is Entry.Data<*> && b is Entry.Data<*>) {
            // to supply the indices, we have to map them to indices in the original data list.
            dataItemsSame(a.item as T, b.item as T)
        } else false
    }

    final override fun contentsSame(a: Entry, b: Entry, aIdx: Int, bIdx: Int): Boolean {
        return if (a is Entry.Header<*> && b is Entry.Header<*>) {
            a.key == b.key
        } else if (a is Entry.Data<*> && b is Entry.Data<*>) {
            // to supply the indices, we have to map them to indices in the original data list.
            dataContentsSame(a.item as T, b.item as T)
        } else false
    }

    open fun dataItemsSame(a: T, b: T): Boolean {
        return a === b
    }

    open fun dataContentsSame(a: T, b: T): Boolean {
        return a == b
    }

    sealed class Entry {
        class Header<K>(val key: K): Entry()
        class Data<T>(val item: T): Entry()
    }
}