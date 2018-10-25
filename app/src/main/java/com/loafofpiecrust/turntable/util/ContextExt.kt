package com.loafofpiecrust.turntable.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


//val Context.firestore get() = FirebaseFirestore.getInstance()

inline val <T> T.exhaustive get(): T = this


operator fun <T> KMutableProperty0<T>.getValue(holder: Any, property: KProperty<*>): T {
    return get()
}
operator fun <T> KMutableProperty0<T>.setValue(holder: Any, property: KProperty<*>, value: T) {
    return set(value)
}


class ColorableContext(base: Context): ContextWrapper(base) {
    override fun getResources(): Resources {
        return super.getResources()
    }
}
