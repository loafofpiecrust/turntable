package com.loafofpiecrust.turntable.util

import android.os.Binder
import android.os.Bundle
import android.support.v4.app.BundleCompat
import android.support.v4.app.Fragment
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KProperty

/**
 * Eases the Fragment.newInstance ceremony by marking the fragment's args with this delegate
 * Just write the property in newInstance and read it like any other property after the fragment has been created
 *
 * Inspired by Adam Powell, he mentioned it during his IO/17 talk about Kotlin
 */
class FragmentArgument<T: Any>(val defaultValue: (() -> T)?) {
    var value: T? = null

    operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        if (thisRef.arguments == null) thisRef.arguments = android.os.Bundle()

        val args = thisRef.arguments
        val key = property.name

        this.value = value

        when (value) {
            is String -> args?.putString(key, value)
            is Int -> args?.putInt(key, value)
            is Short -> args?.putShort(key, value)
            is Long -> args?.putLong(key, value)
            is Byte -> args?.putByte(key, value)
            is ByteArray -> args?.putByteArray(key, value)
            is Char -> args?.putChar(key, value)
            is CharArray -> args?.putCharArray(key, value)
            is CharSequence -> args?.putCharSequence(key, value)
            is Float -> args?.putFloat(key, value)
            is Bundle -> args?.putBundle(key, value)
            is Binder -> BundleCompat.putBinder(args!!, key, value)
            is android.os.Parcelable -> args?.putParcelable(key, value)
//            is java.io.Serializable -> args?.putSerializable(key, value)
            else -> args?.putByteArray(key, runBlocking { serialize(value) })
        }
    }
}

inline operator fun <reified T: Any> FragmentArgument<T>.getValue(thisRef: Fragment, property: KProperty<*>): T {
    if (value == null) {
        value = thisRef.arguments?.get(property.name)?.let { storedValue ->
            if (storedValue is T) {
                storedValue
            } else runBlocking {
                deserialize(storedValue as ByteArray) as T
            }
        } ?: defaultValue?.invoke()
    }
    return value ?: throw IllegalStateException("Property ${property.name} could not be read")
}

fun <T: Any> Fragment.arg() = FragmentArgument<T>(null)
fun <T: Any> Fragment.arg(defaultValue: T) = FragmentArgument { defaultValue }
fun <T: Any> Fragment.arg(defaultValue: () -> T) = FragmentArgument(defaultValue)