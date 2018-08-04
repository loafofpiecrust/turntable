package com.loafofpiecrust.turntable.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

val BG_POOL = newFixedThreadPoolContext(2 * Runtime.getRuntime().availableProcessors(), "bg")
//val BG_POOL = CommonPool
val ALT_BG_POOL = newSingleThreadContext("alt-bg")

fun <T> task(ctx: CoroutineContext = BG_POOL, block: suspend () -> T): Deferred<T> {
//    val cont = deferred<T, Throwable>()
    return async(ctx) {
        block()
    }
//    return cont.promise
}

suspend fun <T> produceTask(block: suspend () -> T): ReceiveChannel<T> {
//    val cont = deferred<T, Throwable>()
    return produce(coroutineContext) {
        send(block())
    }
//    return cont.promise
}
suspend fun <T> produceSingle(v: T): ReceiveChannel<T> {
//    val cont = deferred<T, Throwable>()
    return produce(coroutineContext) {
        send(v)
    }
//    return cont.promise
}


inline fun <T> suspendedTask(ctx: CoroutineContext = BG_POOL, crossinline block: (Continuation<T>) -> Unit): Deferred<T> {
    return task(ctx) {
        suspendCoroutine<T> { cont ->
            block(cont)
        }
    }
}

fun <T, R> Deferred<T>.then(ctx: CoroutineContext = Unconfined, block: suspend (T) -> R): Deferred<R> {
    return async(ctx) {
        block(await())
    }
}

fun <T> Deferred<T>.always(ctx: CoroutineContext = Unconfined, block: suspend (T?) -> Unit): Deferred<T> {
    launch(ctx) {
        val v = try { await() } catch (e: Throwable) { null }
        block(v)
    }
    return this
}

fun <T> Deferred<T>.success(ctx: CoroutineContext = Unconfined, block: suspend (T) -> Unit): Deferred<T> {
    launch(ctx) {
        block(await())
    }
    return this
}

fun <T> Deferred<T>.fail(ctx: CoroutineContext = Unconfined, block: suspend (Throwable) -> Unit): Deferred<T> {
    launch(ctx) {
        try {
            await()
        } catch (e: Throwable) {
            block(e)
        }
    }
    return this
}

fun <T> Deferred<T>.get() = runBlocking { await() }