package com.loafofpiecrust.turntable.util

import android.support.v4.view.ViewPager
import android.widget.TextView
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.support.v4.onPageChangeListener
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


fun <T> bindOneWay(obs: ReceiveChannel<T>, ctx: Job, setter: (T) -> Unit) = run {
    task(UI + ctx) { obs.consumeEach { setter.invoke(it) } }
}

fun <T> bindTwoWay(obs: BroadcastChannel<T>, ctx: Job, initial: T, getter: ((T) -> Unit) -> Unit, setter: (T) -> Unit) = run {
    obs.offer(initial)
    getter.invoke { obs.offer(it) }
    task(UI + ctx) { obs.consumeEach { setter.invoke(it) } }
}
fun <T> bindTwoWay(obs: BroadcastChannel<T>, ctx: Job, prop: KMutableProperty0<T>, getter: ((T) -> Unit) -> Unit) = run {
    obs.offer(prop.get())
    getter.invoke { obs.offer(it) }
    task(UI + ctx) { obs.consumeEach { prop.set(it) } }
}

fun ViewPager.bindCurrentPage(obs: BroadcastChannel<Int>, ctx: Job) = run {
    bindTwoWay(obs, ctx, currentItem, {
        onPageChangeListener {
            onPageSelected(it)
        }
    }, { setCurrentItem(it, true) })
}


fun TextView.bindText(chan: ReceiveChannel<String>, ctx: Job) {
    bindOneWay(chan, ctx) { text = it }
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