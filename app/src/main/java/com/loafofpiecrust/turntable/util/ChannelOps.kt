package com.loafofpiecrust.turntable.util

import android.support.annotation.UiThread
import android.view.View
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.selects.selectUnbiased
import org.jetbrains.anko.sdk25.coroutines.onAttachStateChangeListener
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext


fun <T, R> ReceiveChannel<T>.switchMap(
    context: CoroutineContext = Unconfined,
    f: suspend (T) -> ReceiveChannel<R>
): ReceiveChannel<R> = produce(context) {
    var lastJob: Job? = null
    consumeEach {
        lastJob?.cancel()
        lastJob = async(coroutineContext) {
            f(it).consumeEach { send(it) }
        }
    }
}

fun <T> ReceiveChannel<T>.replayOne(context: CoroutineContext = BG_POOL): ConflatedBroadcastChannel<T> {
//    return broadcast(Channel.CONFLATED) as ConflatedBroadcastChannel<T>
    val chan = ConflatedBroadcastChannel<T>()
    launch(context) {
        consumeEach {
            chan.offer(it)
        }
    }
    return chan
}


fun <E, R> ReceiveChannel<E>.combineLatest(
    other: ReceiveChannel<R>,
    context: CoroutineContext = Unconfined
): ReceiveChannel<Pair<E, R>> = combineLatest(other, context) { a, b -> a to b }

fun <A, B, C> ReceiveChannel<A>.combineLatest(
    b: ReceiveChannel<B>,
    c: ReceiveChannel<B>,
    context: CoroutineContext = Unconfined
) = combineLatest(b, c, context) { a, b, c -> Triple(a, b, c) }


fun <A, B, R> ReceiveChannel<A>.combineLatest(
    sourceB: ReceiveChannel<B>,
    context: CoroutineContext = Unconfined,
    combineFunction: suspend (A, B) -> R
): ReceiveChannel<R> = produce(context) {
    val sourceA = this@combineLatest

    var latestA: A? = null
    var latestB: B? = null

//    var job: Job? = null

    while (isActive && !sourceA.isClosedForReceive && !sourceB.isClosedForReceive) {
        try {
            selectUnbiased<Unit> {
                sourceA.onReceiveOrNull { a ->
                    latestA = a
                    val b = latestB
                    if (a != null && b != null) {
                        send(combineFunction(a, b))
                    }
                }
                sourceB.onReceiveOrNull { b ->
                    latestB = b
                    val a = latestA
                    if (b != null && a != null) {
                        send(combineFunction(a, b))
                    }
                }
            }
        } catch (e: Throwable) {}
    }

//    sourceB.consumeEach {
//        job?.cancel()
//        latestB = it
//        if (latestA != null) {
//            send(combineFunction(latestA!!, it))
//        }
//        job = async(coroutineContext) {
//            sourceA.consumeEach {
//                latestA = it
//                send(combineFunction(it, latestB))
//            }
//        }
//    }
}

fun <A, B, C, R> ReceiveChannel<A>.combineLatest(
    sourceB: ReceiveChannel<B>,
    sourceC: ReceiveChannel<C>,
    context: CoroutineContext = Unconfined,
    combineFunction: suspend (A, B, C) -> R
): ReceiveChannel<R> = produce(context) {
    val sourceA = this@combineLatest

    var a: A
    var b: B
    var c: C

    try {
        a = sourceA.receive()
        b = sourceB.receive()
        c = sourceC.receive()
        send(combineFunction(a, b, c))
    } catch (e: Exception) {
        sourceA.cancel(e)
        sourceB.cancel(e)
        sourceC.cancel(e)
        return@produce
    }

    val atask = async(coroutineContext) {
        sourceA.consumeEach {
            a = it
            send(combineFunction(it, b, c))
        }
    }
    val ctask = async(coroutineContext) {
        sourceC.consumeEach {
            c = it
            send(combineFunction(a, b, it))
        }
    }
    val btask = async(coroutineContext) {
        sourceB.consumeEach {
            b = it
            send(combineFunction(a, it, c))
        }
    }
    // wait until *all* tasks are completed or cancelled.
    arrayOf(atask, btask, ctask).forEach {
        try {
            it.join()
        } catch (e: Throwable) {}
    }
}


fun <T> ReceiveChannel<T>.skip(n: Int, context: CoroutineContext = Unconfined): ReceiveChannel<T> {
    return produce(context) {
        consume {
            for (_rem in 0 until n) {
                receive()
            }
            for (e in this) {
                send(e)
            }
        }
    }
}


fun <T> ReceiveChannel<T>.interrupt(context: CoroutineContext = Unconfined): ReceiveChannel<T> {
    var job: Job? = null
    return produce(context) {
        consume {
            for (e in this) {
                job?.cancel()
                job = launch(coroutineContext) { send(e) }
            }
        }
    }
}

fun <T> ReceiveChannel<T>.distinctSeq(context: CoroutineContext = Unconfined): ReceiveChannel<T> {
    return produce(context) {
        var prev = receive().also { send(it) }
        consumeEach {
            if (it != prev) send(it)
            prev = it
        }
    }
}

val <T> ConflatedBroadcastChannel<T>.hasValue inline get() = (valueOrNull != null)

inline fun <E> BroadcastChannel<E>.consumeEach(
    ctx: CoroutineContext,
    crossinline action: suspend (E) -> Unit
): Job = async(ctx) { consumeEach {
    try { action(it) } catch (e: Throwable) {
        e.printStackTrace()
    }
} }
//
//inline fun <E, R> BroadcastChannel<E>.consume(
//    ctx: CoroutineContext,
//    noinline action: suspend ReceiveChannel<E>.() -> R
//) = task(ctx) { consume { action(this) } }


inline fun <E> ReceiveChannel<E>.consumeEach(
    ctx: CoroutineContext,
    crossinline action: suspend (E) -> Unit
): Job = async(ctx) { consumeEach {
    try {
        action(it)
    } catch (e: Throwable) {
        e.printStackTrace()
    }
} }

inline fun <E, R> ReceiveChannel<E>.consume(
    ctx: CoroutineContext,
    crossinline action: suspend ReceiveChannel<E>.() -> R
): Job = async(ctx) { consume { action(this) } }



/// Subscribe to the broadcast channel and close it when the given view is destroyed.
@UiThread
fun <T> BroadcastChannel<T>.bind(view: View) = openSubscription().bind(view)

@UiThread
fun <T> ReceiveChannel<T>.bind(view: View) = apply {
    view.onAttachStateChangeListener {
        onViewDetachedFromWindow {
            cancel()
        }
    }
}
