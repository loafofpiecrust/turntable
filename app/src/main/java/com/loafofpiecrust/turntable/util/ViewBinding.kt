package com.loafofpiecrust.turntable.util

import android.support.v4.view.ViewPager
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import org.jetbrains.anko.support.v4.onPageChangeListener
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.reflect.KMutableProperty0


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