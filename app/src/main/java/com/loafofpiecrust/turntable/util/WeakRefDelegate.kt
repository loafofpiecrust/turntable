package com.loafofpiecrust.turntable.util

import android.view.View
import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class WeakRefDelegate<T: Any>: ReadWriteProperty<Any, T> {
    private var ref = WeakReference<T>(null)

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return ref.get()!!
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        ref = WeakReference(value)
    }
}

inline fun <T: View> weak() = WeakRefDelegate<T>()