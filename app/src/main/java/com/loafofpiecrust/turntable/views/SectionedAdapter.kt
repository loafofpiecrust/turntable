package com.loafofpiecrust.turntable.views

import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.loafofpiecrust.turntable.util.lazy
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlin.coroutines.CoroutineContext


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