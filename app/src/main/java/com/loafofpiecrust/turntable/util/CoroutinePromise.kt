package com.loafofpiecrust.turntable.util

import com.loafofpiecrust.turntable.tryOr
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.SelectClause1
import kotlin.coroutines.*

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

inline fun <T> CoroutineScope.broadcastSingle(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): BroadcastChannel<T> {
    val channel = ConflatedBroadcastChannel<T>()
    launch(context) {
        channel.send(block())
    }
    return channel
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
fun <T> Deferred<T>.broadcast(): BroadcastChannel<T> = ConflatedBroadcastChannel<T>().also { chan ->
    GlobalScope.launch {
        chan.send(await())
    }
}

inline fun <T> CoroutineScope.suspendAsync(ctx: CoroutineContext = EmptyCoroutineContext, crossinline block: (Continuation<T>) -> Unit): Deferred<T> {
    return async(ctx) {
        suspendCoroutine<T> { cont ->
            block(cont)
        }
    }
}

fun <T> Deferred<T>.get() = runBlocking { await() }

suspend inline fun <T> Deferred<T>.awaitOrElse(alternative: () -> T): T {
    return try {
        await()
    } catch (e: Exception) {
        alternative()
    }
}
suspend inline fun <T> Deferred<T>.awaitOr(alternative: T): T {
    return try {
        await()
    } catch (e: Exception) {
        alternative
    }
}

inline fun <T, R> Deferred<T>.then(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.(T) -> R
): Deferred<R> {
    return GlobalScope.async(context) {
        block(await())
    }
}

inline fun Job.then(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = GlobalScope.launch(context) {
    join()
    block()
}