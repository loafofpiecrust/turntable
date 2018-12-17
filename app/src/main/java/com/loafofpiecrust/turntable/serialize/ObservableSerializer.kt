package com.loafofpiecrust.turntable.serialize

import com.github.salomonbrys.kotson.jsonNull
import com.google.gson.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class CBCTypeAdapter<T: Any>: JsonSerializer<ConflatedBroadcastChannel<T>>, JsonDeserializer<ConflatedBroadcastChannel<T>> {
    override fun serialize(src: ConflatedBroadcastChannel<T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val inner = src.valueOrNull
        return if (inner != null) {
            val par = typeOfSrc as ParameterizedType
            val innerType = par.actualTypeArguments[0]
            context.serialize(inner, innerType)
        } else jsonNull
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConflatedBroadcastChannel<T> {
        return if (json.isJsonNull) {
            ConflatedBroadcastChannel()
        } else {
            val par = typeOfT as ParameterizedType
            val innerType = par.actualTypeArguments[0]
            ConflatedBroadcastChannel(context.deserialize<T>(json, innerType))
        }
    }
}