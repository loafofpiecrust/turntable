package com.loafofpiecrust.result


sealed class Result<out T, out E> {
}

data class Ok<out T, out E>(val ok: T): Result<T, E>()
data class Err<out T, out E>(val err: E): Result<T, E>()
