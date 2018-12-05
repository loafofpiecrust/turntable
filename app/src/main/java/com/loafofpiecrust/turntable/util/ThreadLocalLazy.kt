package com.loafofpiecrust.turntable.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private class ThreadLocalLazy<T>(
    val provider: () -> T
) : ThreadLocal<T>(), ReadOnlyProperty<Any?, T> {
    override fun initialValue(): T? = provider()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()!!
}

fun <T> threadLocalLazy(provider: () -> T): ReadOnlyProperty<Any?, T> =
    ThreadLocalLazy(provider)