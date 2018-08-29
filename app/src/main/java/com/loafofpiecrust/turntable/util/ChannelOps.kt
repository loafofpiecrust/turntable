package com.loafofpiecrust.turntable.util

import android.support.annotation.UiThread
import android.view.View
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.selects.whileSelect
import org.jetbrains.anko.sdk25.coroutines.onAttachStateChangeListener
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext


fun <T, R> ReceiveChannel<T>.switchMap(
    context: CoroutineContext = Unconfined,
    transform: suspend (T) -> ReceiveChannel<R>
): ReceiveChannel<R> = produce(context, onCompletion = consumes()) {
    val input = this@switchMap

    var current: ReceiveChannel<R> = transform(input.receive())
    val output = this

    whileSelect {
        input.onReceiveOrNull { t ->
            t?.also { current = transform(it) } != null
        }

        current.onReceiveOrNull { r ->
            r?.also { output.send(it) } != null
        }
    }
    current.cancel()
}

fun <T> ReceiveChannel<T>.replayOne(context: CoroutineContext = BG_POOL): ConflatedBroadcastChannel<T> {
//    return broadcast(Channel.CONFLATED) as ConflatedBroadcastChannel<T>
    val chan = ConflatedBroadcastChannel<T>()
    launch(context) {
        consumeEach {
            chan.send(it)
        }
    }
    return chan
}


fun <E, R> combineLatest(
    a: ReceiveChannel<E>,
    b: ReceiveChannel<R>,
    context: CoroutineContext = Unconfined
): ReceiveChannel<Pair<E, R>> = combineLatest(a, b, context) { a, b -> a to b }

fun <A, B, C> ReceiveChannel<A>.combineLatest(
    b: ReceiveChannel<B>,
    c: ReceiveChannel<C>,
    context: CoroutineContext = Unconfined
) = combineLatest(b, c, context) { a, b, c -> Triple(a, b, c) }


fun <A, B, R> combineLatest(
    sourceA: ReceiveChannel<A>,
    sourceB: ReceiveChannel<B>,
    context: CoroutineContext = Unconfined,
    combine: suspend (A, B) -> R
): ReceiveChannel<R> = produce(context, onCompletion = {
    sourceA.cancel(it)
    sourceB.cancel(it)
}) {
    var latestA: A? = null
    var latestB: B? = null

    whileSelect {
        sourceA.onReceiveOrNull { a ->
            latestA = a
            val b = latestB
            if (a != null && b != null) {
                send(combine(a, b))
            }
            a != null
        }
        sourceB.onReceiveOrNull { b ->
            latestB = b
            val a = latestA
            if (b != null && a != null) {
                send(combine(a, b))
            }
            b != null
        }
    }
}

fun <A, B, C, R> ReceiveChannel<A>.combineLatest(
    sourceB: ReceiveChannel<B>,
    sourceC: ReceiveChannel<C>,
    context: CoroutineContext = Unconfined,
    combine: suspend (A, B, C) -> R
): ReceiveChannel<R> = produce(context, onCompletion = {
    cancel(it)
    sourceB.cancel(it)
    sourceC.cancel(it)
}) {
    val sourceA = this@combineLatest

    var latestA: A? = null
    var latestB: B? = null
    var latestC: C? = null

    whileSelect {
        sourceA.onReceiveOrNull { a ->
            latestA = a
            val b = latestB
            val c = latestC
            if (a != null && b != null && c != null) {
                send(combine(a, b, c))
            }
            a != null
        }
        sourceB.onReceiveOrNull { b ->
            latestB = b
            val a = latestA
            val c = latestC
            if (a != null && b != null && c != null) {
                send(combine(a, b, c))
            }
            b != null
        }
        sourceC.onReceiveOrNull { c ->
            latestC = c
            val a = latestA
            val b = latestB
            if (a != null && b != null && c != null) {
                send(combine(a, b, c))
            }
            c != null
        }
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
        consume {
            var prev = receive().also { send(it) }
            for (elem in this) {
                if (elem != prev) send(elem)
                prev = elem
            }
        }
    }
}

fun <T> ReceiveChannel<T>.changes(context: CoroutineContext = Unconfined) = produce(context) {
    consume {
        var prev = receive()
        for (elem in this) {
            if (prev != elem) {
                send(prev to elem)
            }
            prev = elem
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
