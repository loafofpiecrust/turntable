package com.loafofpiecrust.turntable.util

import android.support.v4.view.ViewPager
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.sdk27.coroutines.onAttachStateChangeListener
import org.jetbrains.anko.support.v4.onPageChangeListener
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


fun <T> bindOneWay(obs: ReceiveChannel<T>, setter: (T) -> Unit) {
    GlobalScope.launch {
        obs.consumeEach { setter.invoke(it) }
    }
}

fun <T> View.bindTwoWay(obs: BroadcastChannel<T>, initial: T, getter: ((T) -> Unit) -> Unit, setter: (T) -> Unit) {
    obs.offer(initial)
    getter.invoke { obs.offer(it) }
    ViewScope(this).launch { obs.consumeEach { setter.invoke(it) } }
}

class ViewScope(view: View): CoroutineScope {
    private var supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisor

    init {
        view.onAttachStateChangeListener {
            onViewDetachedFromWindow {
                supervisor.cancel()
            }
        }
    }
}

fun <T> View.bindTwoWay(obs: BroadcastChannel<T>, prop: KMutableProperty0<T>, getter: ((T) -> Unit) -> Unit) {
    obs.offer(prop.get())
    getter.invoke { obs.offer(it) }
    ViewScope(this).launch {
        obs.consumeEach { prop.set(it) }
    }
}

fun ViewPager.bindCurrentPage(obs: BroadcastChannel<Int>) {
    bindTwoWay(obs, currentItem, {
        onPageChangeListener {
            onPageSelected(it)
        }
    }, { setCurrentItem(it, true) })
}


//fun TextView.bindText(chan: ReceiveChannel<String>, ctx: Job) {
//    bindOneWay(chan, ctx) { text = it }
//}
fun TextView.bindText(chan: ReceiveChannel<String>) {
    bindOneWay(chan.bind(this)) { text = it }
}

suspend inline fun <T> ReceiveChannel<T>.bindTo(prop: KMutableProperty0<T>) {
    consumeEach {
        prop.setter.call(it)
    }
}
suspend inline fun <T> ReceiveChannel<T>.bindTo(prop: KFunction<T>) {
    consumeEach {
        prop.call(it)
    }
}
