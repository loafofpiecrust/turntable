package com.loafofpiecrust.turntable.views

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.util.ViewScope
import kotlinx.coroutines.CoroutineStart
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

    val container = frameLayout()

    private var emptyView: View? = null
    private var nullView: View? = null
    var channel: ReceiveChannel<*>? = null

    fun emptyState(block: @AnkoViewDslMarker ViewGroup.() -> View) {
        emptyView = container.block()
        emptyView!!.visibility = View.INVISIBLE
    }

    fun nullState(block: @AnkoViewDslMarker ViewGroup.() -> View) {
        nullView = container.block()
        nullView!!.visibility = View.INVISIBLE
    }

    inline fun contents(block: @AnkoViewDslMarker ViewGroup.() -> View) {
        container.block()
    }

    private fun showIsEmpty() {
        if (emptyView != null) {
            emptyView!!.visibility = View.VISIBLE
            nullView?.visibility = View.INVISIBLE
        } else if (nullView != null) {
            showIsNull()
        }
    }

    private fun showIsNull() {
        if (nullView != null) {
            nullView!!.visibility = View.VISIBLE
            emptyView?.visibility = View.INVISIBLE
        } else if (emptyView != null) {
            showIsEmpty()
        }
    }

    private fun showContents() {
        nullView?.visibility = View.INVISIBLE
        emptyView?.visibility = View.INVISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewScope(this).launch(start = CoroutineStart.UNDISPATCHED) {
//            if (channel?.isEmpty == true) {
//                isRefreshing = true
//            }
            channel?.consumeEach {
                // Every time we receive data, ensure we stop showing the load circle.
                isRefreshing = false
                // Show the "empty view" if there's no data
                if (it == null) {
                    showIsNull()
                } else if (it is Collection<*> && it.isEmpty()) {
                    showIsEmpty()
                } else {
                    showContents()
                }
            }
        }
    }
}

inline fun ViewManager.refreshableRecyclerView(init: @AnkoViewDslMarker RefreshableRecyclerView.() -> Unit = {}): RefreshableRecyclerView =
    ankoView({ RefreshableRecyclerView(it) }, theme = 0, init = init)