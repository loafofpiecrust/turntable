package com.loafofpiecrust.turntable.ui

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.sendBlocking
import kotlinx.coroutines.experimental.launch
import kotlin.coroutines.experimental.CoroutineContext


class SongsModel: ViewModel() {
    override fun onCleared() {
        super.onCleared()
    }
}

/**
 * Ideal data connection inside Fragment view:
 * onCreateView: make view and setup all subscriptions
 * onPause: don't react to any changes that'd change the view
 * onResume: allow ui to react to changes and apply the latest change (if any) while paused
 * onDestroy: cancel all running tasks and channels.
 */

fun <T> ReceiveChannel<T>.connect(
    lifecycle: Lifecycle,
    context: CoroutineContext = Unconfined
): ReceiveChannel<T> = produce(context) {
    var lastValue: T? = null

    class Observer: LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun onResume() {
            val v = lastValue
            if (v != null) {
                sendBlocking(v)
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() {
            cancel()
        }
    }

    launch(UI) { lifecycle.addObserver(Observer()) }

    consumeEach {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) {
            send(it)
        } else {
            lastValue = it
        }
    }
}