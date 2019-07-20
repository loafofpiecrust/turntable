package com.loafofpiecrust.turntable.serialize

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.google.gson.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class CBCTypeAdapter<T: Any>: JsonSerializer<ConflatedBroadcastChannel<T>>, JsonDeserializer<ConflatedBroadcastChannel<T>> {
    override fun serialize(
        src: ConflatedBroadcastChannel<T>,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement? {
        val inner = src.valueOrNull
        return if (inner != null) {
            val par = typeOfSrc as ParameterizedType
            val innerType = par.actualTypeArguments[0]
            context.serialize(inner, innerType)
        } else EMPTY_STATE
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ConflatedBroadcastChannel<T> {
        // special object { "": 0 } signifies an empty channel.
        return if (json == null || json.isJsonNull || (json.isJsonObject && json.obj == EMPTY_STATE)) {
            ConflatedBroadcastChannel()
        } else {
            val par = typeOfT as ParameterizedType
            val innerType = par.actualTypeArguments[0]
            ConflatedBroadcastChannel(context!!.deserialize<T>(json, innerType))
        }
    }

    companion object {
        // Never change this empty state to retain backwards compatibility.
        private val EMPTY_STATE = jsonObject("" to 0)
    }
}