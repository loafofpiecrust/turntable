package com.loafofpiecrust.turntable.util

import android.support.v4.widget.SwipeRefreshLayout
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consume
import kotlinx.coroutines.experimental.launch


fun SwipeRefreshLayout.stopRefreshOnReceive(channel: ReceiveChannel<*>, startRefreshing: Boolean = true) {
    isEnabled = false
    ViewScope(this).launch {
        channel.consume {
            if (isEmpty) {
                if (startRefreshing) {
                    isRefreshing = true
                }
                for (e in this@consume) {
                    isRefreshing = false
                }
            }
        }
    }
}