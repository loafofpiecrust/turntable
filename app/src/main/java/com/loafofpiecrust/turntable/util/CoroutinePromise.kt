package com.loafofpiecrust.turntable.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import org.jetbrains.anko.custom.async
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

val BG_POOL: CoroutineDispatcher = if (Runtime.getRuntime().availableProcessors() <= 2) {
    newFixedThreadPoolContext(2 * Runtime.getRuntime().availableProcessors(), "bg")
} else CommonPool
val ALT_BG_POOL = newSingleThreadContext("alt-bg")


class CoroutineSafelyEndedException: RuntimeException() {
    override val message: String?
        get() = "This coroutine was safely cancelled"
}

fun Job.cancelSafely() = try {
    cancel()
} catch (e: Throwable) {}

inline fun <T> task(ctx: CoroutineContext = BG_POOL, noinline block: suspend CoroutineScope.() -> T): Deferred<T> {
//    val cont = deferred<T, Throwable>()
    return async(ctx, block = block)
//    return cont.promise
}

fun <T> produceTask(ctx: CoroutineContext = BG_POOL, block: suspend () -> T): ReceiveChannel<T> {
//    val cont = deferred<T, Throwable>()
    return produce(ctx) {
        offer(block())
    }
//    return cont.promise
}
fun <T> produceSingle(v: T): ReceiveChannel<T> {
//    val cont = deferred<T, Throwable>()
    return produce(Unconfined) {
        offer(v)
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
    async(ctx) {
        var v: T? = null
        try {
            v = await()
        } finally {
            block(v)
        }
    }
    return this
}

fun <T> Deferred<T>.success(ctx: CoroutineContext = Unconfined, block: suspend (T) -> Unit): Deferred<T> {
    async(ctx) {
        block(await())
    }
    return this
}

fun <T> Deferred<T>.fail(ctx: CoroutineContext = Unconfined, block: suspend (Throwable) -> Unit): Deferred<T> {
    async(ctx) {
        try {
            await()
        } catch (e: Throwable) {
            block(e)
        }
    }
    return this
}

fun <T> Deferred<T>.get() = runBlocking { await() }
