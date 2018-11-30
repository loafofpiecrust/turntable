package com.loafofpiecrust.turntable.util

import android.support.annotation.UiThread
import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.whileSelect
import org.jetbrains.anko.sdk27.coroutines.onAttachStateChangeListener
import kotlin.coroutines.CoroutineContext


fun <T, R> ReceiveChannel<T>.switchMap(
    context: CoroutineContext = Dispatchers.Unconfined,
    transform: suspend (T) -> ReceiveChannel<R>?
): ReceiveChannel<R> {
    var currentJob: Job? = null
    return GlobalScope.produce(context) {
        try {
            consumeEach {
                currentJob?.cancelAndJoin()
                transform(it)?.let { newChan ->
                    currentJob = launch {
                        sendFrom(newChan)
                    }
                }
            }
        } finally {
            currentJob?.cancel()
            cancel()
        }
    }
}

fun <T> ReceiveChannel<T>.startWith(
    element: T
) = GlobalScope.produce(Dispatchers.Unconfined) {
    consume {
        if (isEmpty) {
            send(element)
        }
        for (e in this) {
            send(e)
        }
    }
}

suspend inline fun <T> SendChannel<T>.sendFrom(
    channel: ReceiveChannel<T>
) = channel.consumeEach { send(it) }

fun <T> ReceiveChannel<T>.replayOne(context: CoroutineContext = Dispatchers.Unconfined): ConflatedBroadcastChannel<T> {
//    return broadcast(Channel.CONFLATED) as ConflatedBroadcastChannel<T>

    val chan = ConflatedBroadcastChannel<T>()
    GlobalScope.launch(context) {
        consumeEach {
            chan.send(it)
        }
    }
    return chan
}


fun <E, R> combineLatest(
    a: ReceiveChannel<E>,
    b: ReceiveChannel<R>,
    context: CoroutineContext = Dispatchers.Unconfined
): ReceiveChannel<Pair<E, R>> = combineLatest(a, b, context) { a, b -> a to b }


fun <A, B, R> combineLatest(
    sourceA: ReceiveChannel<A>,
    sourceB: ReceiveChannel<B>,
    context: CoroutineContext = Dispatchers.Unconfined,
    combine: suspend (A, B) -> R
): ReceiveChannel<R> = GlobalScope.produce(context) {
    var latestA: A? = null
    var latestB: B? = null

    try {
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
    } finally {
        sourceA.cancel()
        sourceB.cancel()
    }
}

fun <A, B, C, R> combineLatest(
    sourceA: ReceiveChannel<A>,
    sourceB: ReceiveChannel<B>,
    sourceC: ReceiveChannel<C>,
    context: CoroutineContext = Dispatchers.Unconfined,
    combine: suspend (A, B, C) -> R
): ReceiveChannel<R> = GlobalScope.produce(context) {
    var latestA: A? = null
    var latestB: B? = null
    var latestC: C? = null

    try {
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
    } finally {
        sourceA.cancel()
        sourceB.cancel()
        sourceC.cancel()
    }
}


fun <T> ReceiveChannel<T>.skip(
    n: Int
): ReceiveChannel<T> {
    return GlobalScope.produce(Dispatchers.Unconfined) {
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


fun <T> ReceiveChannel<T>.interruptable(context: CoroutineContext = Dispatchers.Unconfined): ReceiveChannel<T> {
    var job: Job? = null
    return GlobalScope.produce(context) {
        consumeEach { e ->
            job?.cancelAndJoin()
            job = launch(coroutineContext) { send(e) }
        }
    }
}

fun <T> ReceiveChannel<T>.distinctSeq(
    context: CoroutineContext = Dispatchers.Unconfined
): ReceiveChannel<T> = distinctBySeq(context) { a, b -> a == b }

fun <T> ReceiveChannel<T>.distinctInstanceSeq(
    context: CoroutineContext = Dispatchers.Unconfined
): ReceiveChannel<T> = distinctBySeq(context) { a, b -> a === b }

fun <T> ReceiveChannel<T>.distinctBySeq(
    context: CoroutineContext = Dispatchers.Unconfined,
    areSame: suspend (T, T) -> Boolean
): ReceiveChannel<T> = GlobalScope.produce(context) {
    consume {
        var prev = receive().also { send(it) }
        for (elem in this) {
            if (!areSame(elem, prev)) send(elem)
            prev = elem
        }
    }
}

fun <T> ReceiveChannel<T>.changes(
    context: CoroutineContext = Dispatchers.Unconfined
) = GlobalScope.produce(context) {
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

//inline fun <E> BroadcastChannel<E>.consumeEach(
//    ctx: CoroutineContext,
//    crossinline action: suspend (E) -> Unit
//): Job = GlobalScope.async(ctx) { consumeEach {
//    try { action(it) } catch (e: Exception) {
//        e.printStackTrace()
//    }
//} }
//
//inline fun <E, R> BroadcastChannel<E>.consume(
//    ctx: CoroutineContext,
//    noinline action: suspend ReceiveChannel<E>.() -> R
//) = task(ctx) { consume { action(this) } }


inline fun <E> ReceiveChannel<E>.consumeEach(
    ctx: CoroutineContext,
    crossinline action: suspend (E) -> Unit
): Job = GlobalScope.async(ctx) { consumeEach {
    try {
        action(it)
    } catch (e: Exception) {
        e.printStackTrace()
    }
} }



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

operator fun <T> BroadcastChannel<T>.iterator(): ChannelIterator<T> = openSubscription().iterator()




// Sequences