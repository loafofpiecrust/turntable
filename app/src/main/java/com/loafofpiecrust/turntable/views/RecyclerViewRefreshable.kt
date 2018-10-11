package com.loafofpiecrust.turntable.views

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewManager
import android.widget.FrameLayout
import com.loafofpiecrust.turntable.ui.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerBroadcastAdapter
import com.loafofpiecrust.turntable.util.ViewScope
import com.loafofpiecrust.turntable.util.stopRefreshOnReceive
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.SupervisorJob
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.broadcast
import kotlinx.coroutines.experimental.channels.consume
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoViewDslMarker
import org.jetbrains.anko._FrameLayout
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.recyclerview.v7._RecyclerView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.swipeRefreshLayout
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * A RecyclerView augmented to show
 * - Loading circle while data is loading.
 * - "Empty view" when the data set is empty.
 */
//inline fun <T> ViewManager.refreshRecyclerView(
//    adapter: RecyclerBroadcastAdapter<T, *>,
//    block: @AnkoViewDslMarker _RecyclerView.() -> Unit
//) = swipeRefreshLayout {
//    val scope = ViewScope(this)
//    val channel = adapter.channel.broadcast()
//
//    isEnabled = false
//
//    recyclerView {
//        this.adapter = adapter
//        block()
//    }
//
//
//
//    scope.launch {
//        channel.openSubscription().consumeEach {
//
//        }
//    }
//}

class RecyclerViewRefreshable(
    context: Context
): SwipeRefreshLayout(context), CoroutineScope {
    init {
        isEnabled = false
    }

    override val coroutineContext = ViewScope(this).coroutineContext

    // Stacked in the order initialized.
    lateinit var recycler: RecyclerView
    private val emptyView = frameLayout()


    private val adapterJob = SupervisorJob()
    var adapter: RecyclerAdapter<*, *>
        get() = recycler.adapter as RecyclerAdapter<*, *>
        set(adapter) {
            recycler.adapter = adapter

            val channel = adapter.channel.broadcast()

            adapterJob.cancelChildren()
            launch(adapterJob) {
                channel.consumeEach {
                    // Every time we receive data, ensure we stop showing the load circle.
                    isRefreshing = false
                    // Show the "empty view" if there's no data
                    emptyView.visibility = if (it.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }
        }

    var layoutManager get() = recycler.layoutManager
        set(v) { recycler.layoutManager = v }

    fun emptyView(block: FrameLayout.() -> Unit) {
        emptyView.removeAllViews()
        emptyView.block()
    }
}

inline fun ViewManager.refreshRecyclerView(init: @AnkoViewDslMarker RecyclerViewRefreshable.() -> Unit = {}): RecyclerViewRefreshable =
    ankoView({ RecyclerViewRefreshable(it) }, theme = 0, init = init)