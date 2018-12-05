package com.loafofpiecrust.turntable.serialize

import kotlinx.serialization.KSerializer
import kotlin.collections.mutableMapOf
import kotlin.collections.set

object RegisteredSerializer {
    val registry = mutableMapOf<String, KSerializer<*>>()

    inline fun <reified T> register(serializer: KSerializer<T>) {
        registry[T::class.qualifiedName!!] = serializer
    }


}