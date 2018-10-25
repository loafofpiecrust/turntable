package com.loafofpiecrust.turntable.views

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.util.ViewScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoViewDslMarker
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.frameLayout

/**
 * A RecyclerView augmented to show
 * - Loading circle while data is loading.
 * - "Empty view" when the data set is empty.
 */
class RefreshableRecyclerView(
    context: Context
): SwipeRefreshLayout(context) {
    init {
        isEnabled = false
    }

    internal val container = frameLayout()

    private var emptyView: View? = null
    var channel: ReceiveChannel<List<*>>? = null

    fun emptyView(block: @AnkoViewDslMarker ViewManager.() -> View) {
        emptyView = container.block()
        emptyView!!.visibility = View.GONE
    }

    fun contentView(block: @AnkoViewDslMarker ViewManager.() -> RecyclerView) {
        container.block()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewScope(this).launch {
            channel?.consumeEach {
                // Every time we receive data, ensure we stop showing the load circle.
                isRefreshing = false
                // Show the "empty view" if there's no data
                emptyView?.visibility = if (it.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }
}

inline fun ViewManager.refreshableRecyclerView(init: @AnkoViewDslMarker RefreshableRecyclerView.() -> Unit = {}): RefreshableRecyclerView =
    ankoView({ RefreshableRecyclerView(it) }, theme = 0, init = init)