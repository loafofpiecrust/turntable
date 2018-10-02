package com.loafofpiecrust.turntable.util

import com.loafofpiecrust.turntable.tryOr
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.selects.SelectClause1
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

val BG_POOL: CoroutineDispatcher = if (Runtime.getRuntime().availableProcessors() <= 2) {
    newFixedThreadPoolContext(2 * Runtime.getRuntime().availableProcessors(), "bg")
} else Dispatchers.Default
val ALT_BG_POOL = newSingleThreadContext("alt-bg")


fun Job.cancelSafely() = try {
    cancel()
} catch (e: Throwable) {}

fun <T> produceSingle(v: T): ReceiveChannel<T> {
    return CompletableDeferred(v).toChannel()
}

fun <T> CoroutineScope.produceSingle(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): ReceiveChannel<T> {
    return produce(context) {
        send(block())
    }
}

class ReceiveDeferredChannel<E>(val deferred: Deferred<E>): ReceiveChannel<E> {
    private var usedUp = false

    override val isClosedForReceive: Boolean
        get() = deferred.isCompleted
    override val isEmpty: Boolean
        get() = deferred.isActive || deferred.isCancelled
    override val onReceive: SelectClause1<E>
        get() = deferred.onAwait
    override val onReceiveOrNull: SelectClause1<E?>
        get() = deferred.onAwait

    override fun cancel() = deferred.cancel()
    override fun cancel(cause: Throwable?) = deferred.cancel(cause)

    override fun iterator(): ChannelIterator<E> {
        return object: ChannelIterator<E> {
            override suspend fun hasNext(): Boolean = !usedUp && !isEmpty
            override suspend fun next(): E = deferred.await().also { usedUp = true }
        }
    }

    override fun poll(): E? {
        return if (!usedUp && deferred.isCompleted && !deferred.isCancelled) {
            deferred.getCompleted()
        } else null
    }
    override suspend fun receive(): E {
        return if (usedUp) {
            throw ClosedReceiveChannelException("Deferred result already received")
        } else {
            deferred.await().also { usedUp = true }
        }
    }
    override suspend fun receiveOrNull(): E? {
        return if (usedUp) {
            null
        } else tryOr(null) {
            deferred.await().also { usedUp = true }
        }
    }
}
fun <T> Deferred<T>.toChannel(): ReceiveChannel<T> = ReceiveDeferredChannel(this)

inline fun <T> CoroutineScope.suspendAsync(ctx: CoroutineContext = EmptyCoroutineContext, crossinline block: (Continuation<T>) -> Unit): Deferred<T> {
    return async(ctx) {
        suspendCoroutine<T> { cont ->
            block(cont)
        }
    }
}

fun <T> Deferred<T>.get() = runBlocking { await() }
