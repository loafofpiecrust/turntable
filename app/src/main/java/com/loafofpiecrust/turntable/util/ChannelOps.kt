package com.loafofpiecrust.turntable.util

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import kotlin.coroutines.experimental.CoroutineContext


fun <T, R> ReceiveChannel<T>.switchMap(
    context: CoroutineContext = Unconfined,
    f: suspend (T) -> ReceiveChannel<R>
): ReceiveChannel<R> = produce(context) {
    var lastJob: Job? = null
    consumeEach {
        lastJob?.cancel()
        lastJob = async(context) {
            f(it).consumeEach { send(it) }
        }
    }
}

fun <T> ReceiveChannel<T>.replayOne(context: CoroutineContext = BG_POOL): ConflatedBroadcastChannel<T> {
    val chan = ConflatedBroadcastChannel<T>()
    task(context) {
        consumeEach {
            chan.offer(it)
        }
    }
    return chan
}


inline fun <E, R> ReceiveChannel<E>.combineLatest(
    other: ReceiveChannel<R>,
    context: CoroutineContext = Unconfined
): ReceiveChannel<Pair<E, R>> = combineLatest(other, context) { a, b -> a to b }

inline fun <A, B, C> ReceiveChannel<A>.combineLatest(
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
    var latestB: B

//    try {
//        latestA = sourceA.receive()
//        latestB = sourceB.receive()
//        send(combineFunction(latestA, latestB))
//    } catch (e: Exception) {
//        if (!sourceA.isClosedForReceive) {
//            sourceA.cancel(e)
//        }
//        if (!sourceB.isClosedForReceive) {
//            sourceB.cancel(e)
//        }
//        return@produce
//    }

    var job: Job? = null
    sourceB.consumeEach {
        job?.cancel()
        latestB = it
        if (latestA != null) {
            send(combineFunction(latestA!!, it))
        }
        job = async(context) {
            sourceA.consumeEach {
                latestA = it
                send(combineFunction(it, latestB))
            }
        }
    }
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

    val atask = async(context) {
        sourceA.consumeEach {
            a = it
            send(combineFunction(it, b, c))
        }
    }
    val ctask = async(context) {
        sourceC.consumeEach {
            c = it
            send(combineFunction(a, b, it))
        }
    }
    sourceB.consumeEach {
        b = it
        send(combineFunction(a, it, c))
    }
    atask.await()
    ctask.await()
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
                job = task(context) { receive() }
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

fun <E> BroadcastChannel<E>.consumeEach(
    ctx: CoroutineContext,
    action: suspend (E) -> Unit
) = task(ctx) { consumeEach {
    try { action(it) } catch (e: Throwable) {
        e.printStackTrace()
    }
} }

inline fun <E, R> BroadcastChannel<E>.consume(
    ctx: CoroutineContext,
    noinline action: suspend SubscriptionReceiveChannel<E>.() -> R
) = task(ctx) { consume { action(this) } }


fun <E> ReceiveChannel<E>.consumeEach(
    ctx: CoroutineContext,
    action: suspend (E) -> Unit
) = task(ctx) { consumeEach {
    try {
        action(it)
    } catch (e: Throwable) {
        e.printStackTrace()
    }
} }

inline fun <E, R> ReceiveChannel<E>.consume(
    ctx: CoroutineContext,
    noinline action: suspend ReceiveChannel<E>.() -> R
) = task(ctx) { consume { action(this) } }
