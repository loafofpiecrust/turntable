package com.loafofpiecrust.turntable.util

import android.app.Activity
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.support.v4.app.BundleCompat
import android.support.v4.app.Fragment
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.objectFromBytes
import com.loafofpiecrust.turntable.objectToBytes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * Eases the Fragment.newInstance ceremony by marking the fragment's args with this delegate
 * Just write the property in newInstance and read it like any other property after the fragment has been created
 *
 * Inspired by Adam Powell, he mentioned it during his IO/17 talk about Kotlin
 */
class FragmentArgument<T: Any>(private val defaultValue: T?) : ReadWriteProperty<Fragment, T> {
    private var value: T? = null

    override operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            try {
                val args = thisRef.arguments
                    ?: throw IllegalStateException("Cannot read property ${property.name} if no arguments have been set")
                val storedValue = args[property.name]

                @Suppress("UNCHECKED_CAST")
                value = (storedValue as? T) ?: App.kryo.objectFromBytes(storedValue as ByteArray)
            } catch (e: Throwable) {
                // If the argument wasn't provided, attempt to fallback on the default value.
                value = defaultValue
            }
        }
        return value ?: throw IllegalStateException("Property ${property.name} could not be read")
    }

    override operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
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
            else -> args?.putByteArray(key, App.kryo.objectToBytes(value))
        }
    }
}

fun <T: Any> Fragment.arg(defaultValue: T? = null) = FragmentArgument(defaultValue)



class ActivityArgument<T: Any>(private val defaultValue: T?) : ReadWriteProperty<Activity, T> {
    private var value: T? = null

    override operator fun getValue(thisRef: Activity, property: KProperty<*>): T {
        if (value == null) {
            try {
                val args = thisRef.intent.extras
                    ?: throw IllegalStateException("Cannot read property ${property.name} if no arguments have been set")
                val storedValue = args[property.name]

                @Suppress("UNCHECKED_CAST")
                value = (storedValue as? T) ?: App.kryo.objectFromBytes(storedValue as ByteArray)
            } catch (e: Throwable) {
                // If the argument wasn't provided, attempt to fallback on the default value.
                value = defaultValue
            }
        }
        return value ?: throw IllegalStateException("Property ${property.name} could not be read")
    }

    override operator fun setValue(thisRef: Activity, property: KProperty<*>, value: T) {
        if (thisRef.intent == null) thisRef.intent = Intent()
        val args = thisRef.intent
        val key = property.name

        this.value = value

        when (value) {
            is String -> args?.putExtra(key, value)
            is Int -> args?.putExtra(key, value)
            is Short -> args?.putExtra(key, value)
            is Long -> args?.putExtra(key, value)
            is Byte -> args?.putExtra(key, value)
            is ByteArray -> args?.putExtra(key, value)
            is Char -> args?.putExtra(key, value)
            is CharArray -> args?.putExtra(key, value)
            is CharSequence -> args?.putExtra(key, value)
            is Float -> args?.putExtra(key, value)
            is Bundle -> args?.putExtra(key, value)
//            is Binder -> BundleCompat.putBinder(args!!, key, value)
            is android.os.Parcelable -> args?.putExtra(key, value)
//            is java.io.Serializable -> args?.putSerializable(key, value)
            else -> args?.putExtra(key, App.kryo.objectToBytes(value))
        }
    }
}