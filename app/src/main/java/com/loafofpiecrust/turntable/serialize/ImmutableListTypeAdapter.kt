package com.loafofpiecrust.turntable.serialize

import com.github.salomonbrys.kotson.*
import com.google.gson.*
import com.loafofpiecrust.turntable.util.lazy
import kotlinx.collections.immutable.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


class ImmutableListTypeAdapter<T: Any>: JsonSerializer<ImmutableList<T>>, JsonDeserializer<ImmutableList<T>> {
    override fun serialize(src: ImmutableList<T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val par = typeOfSrc as ParameterizedType
        val innerType = par.actualTypeArguments[0]
        return JsonArray(src.size).apply {
            for (e in src) {
                add(context.serialize(e, innerType))
            }
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ImmutableList<T> {
        val par = typeOfT as ParameterizedType
        val innerType = par.actualTypeArguments[0]
        return json.array.lazy.map { e ->
            context.deserialize<T>(e, innerType)
        }.asIterable().toImmutableList()
    }
}

class ImmutableMapTypeAdapter<K: Any, T: Any>: JsonSerializer<ImmutableMap<K, T>>, JsonDeserializer<ImmutableMap<K, T>> {
    override fun serialize(src: ImmutableMap<K, T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val par = typeOfSrc as ParameterizedType
        val keyType = par.actualTypeArguments[0]
        val valueType = par.actualTypeArguments[1]
        return JsonArray(src.size).apply {
            for ((key, value) in src) {
                add(jsonArray(
                    context.serialize(key, keyType),
                    context.serialize(value, valueType)
                ))
            }
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ImmutableMap<K, T> {
        val par = typeOfT as ParameterizedType
        val keyType = par.actualTypeArguments[0]
        val valueType = par.actualTypeArguments[1]
        return immutableMapOf(*json.array.map { e ->
            val keyObj = e.array[0]
            val valueObj = e.array[1]
            context.deserialize<K>(keyObj, keyType) to
                context.deserialize<T>(valueObj, valueType)
        }.toTypedArray())
    }
}

class ImmutableSetTypeAdapter<T: Any>: JsonSerializer<ImmutableSet<T>>, JsonDeserializer<ImmutableSet<T>> {
    override fun serialize(src: ImmutableSet<T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val par = typeOfSrc as ParameterizedType
        val innerType = par.actualTypeArguments[0]
        return JsonArray(src.size).apply {
            for (e in src) {
                add(context.serialize(e, innerType))
            }
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ImmutableSet<T> {
        val par = typeOfT as ParameterizedType
        val innerType = par.actualTypeArguments[0]
        return json.array.lazy.map { e ->
            context.deserialize<T>(e, innerType)
        }.asIterable().toImmutableSet()
    }
}