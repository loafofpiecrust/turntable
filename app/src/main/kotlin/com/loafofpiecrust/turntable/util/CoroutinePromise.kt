package com.loafofpiecrust.turntable.util

import com.loafofpiecrust.turntable.tryOr
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ChannelIterator
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.selects.SelectClause1
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
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
    return task(ctx) {
        block()
    }.toChannel()
//    return cont.promise
}
fun <T> produceSingle(v: T): ReceiveChannel<T> {
//    val cont = deferred<T, Throwable>()
    return ReceiveImmediateChannel(v)
//    return cont.promise
}

class ReceiveImmediateChannel<E>(val value: E): ReceiveChannel<E> {
    private var usedUp = false

    override val isClosedForReceive: Boolean get() = usedUp
    override val isEmpty: Boolean get() = usedUp
    override val onReceive: SelectClause1<E>
        get() = TODO()
    override val onReceiveOrNull: SelectClause1<E?>
        get() = TODO()

    override fun cancel(cause: Throwable?): Boolean {
        return if (usedUp) {
            false
        } else {
            usedUp = true
            true
        }
    }

    override fun iterator(): ChannelIterator<E> {
        return object: ChannelIterator<E> {
            override suspend fun hasNext(): Boolean = !usedUp
            override suspend fun next(): E = value.also { usedUp = true }
        }
    }

    override fun poll(): E? = if (usedUp) null else value
    override suspend fun receive(): E = if (usedUp) {
        throw CancellationException()
    } else value.also { usedUp = true }
    override suspend fun receiveOrNull(): E? = if (usedUp) {
        null
    } else value.also { usedUp = true }
}

class ReceiveDeferredChannel<E>(val deferred: Deferred<E>): ReceiveChannel<E> {
    private var usedUp = false

    override val isClosedForReceive: Boolean
        get() = deferred.isCompleted
    override val isEmpty: Boolean
        get() = deferred.isActive || deferred.isCompletedExceptionally
    override val onReceive: SelectClause1<E>
        get() = deferred.onAwait
    override val onReceiveOrNull: SelectClause1<E?>
        get() = deferred.onAwait

    override fun cancel(cause: Throwable?): Boolean {
//        usedUp = true
        return deferred.cancel(cause)
    }

    override fun iterator(): ChannelIterator<E> {
        return object: ChannelIterator<E> {
            override suspend fun hasNext(): Boolean = !usedUp && !isEmpty
            override suspend fun next(): E = deferred.await().also { usedUp = true }
        }
    }

    override fun poll(): E? = if (usedUp) null else tryOr(null) { deferred.getCompleted() }
    override suspend fun receive(): E = if (usedUp) throw CancellationException() else deferred.await().also { usedUp = true }
    override suspend fun receiveOrNull(): E? = if (usedUp) null else tryOr(null) { deferred.await().also { usedUp = true } }
}
fun <T> Deferred<T>.toChannel(): ReceiveChannel<T> = ReceiveDeferredChannel(this)

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
