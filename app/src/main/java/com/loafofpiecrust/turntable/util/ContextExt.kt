package com.loafofpiecrust.turntable.util

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


//val Context.firestore get() = FirebaseFirestore.getInstance()

inline val Any.exhaustive get(): Any = this


operator fun <T> KMutableProperty0<T>.getValue(holder: Any, property: KProperty<*>): T {
    return get()
}
operator fun <T> KMutableProperty0<T>.setValue(holder: Any, property: KProperty<*>, value: T) {
    return set(value)
}