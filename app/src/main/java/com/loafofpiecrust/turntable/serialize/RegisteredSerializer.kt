package com.loafofpiecrust.turntable.serialize

import com.loafofpiecrust.turntable.sync.Message
import kotlinx.serialization.*
import kotlin.reflect.KClass

object RegisteredSerializer {
    val registry = mutableMapOf<String, KSerializer<*>>()

    inline fun <reified T> register(serializer: KSerializer<T>) {
        registry[T::class.qualifiedName!!] = serializer
    }


}