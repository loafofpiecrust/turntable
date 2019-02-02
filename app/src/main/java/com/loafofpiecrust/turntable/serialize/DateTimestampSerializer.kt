package com.loafofpiecrust.turntable.serialize

import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

class DateTimestampSerializer: JsonSerializer<Date>, JsonDeserializer<Date> {
    override fun serialize(src: Date?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
        return src?.time?.let { JsonPrimitive(it) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Date? {
        return json?.asLong?.let { Date(it) }
    }
}