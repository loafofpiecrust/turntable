package com.loafofpiecrust.turntable.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


class threadLocalLazy<out T>(val provider: () -> T) : ReadOnlyProperty<Any?, T> {
    private val threadLocal = object : ThreadLocal<T>() {
        override fun initialValue(): T = provider()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        threadLocal.get()
}