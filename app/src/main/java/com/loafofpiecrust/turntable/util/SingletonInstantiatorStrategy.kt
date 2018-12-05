package com.loafofpiecrust.turntable.util

import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.BaseInstantiatorStrategy
import org.objenesis.strategy.InstantiatorStrategy

/**
 * Instantiates classes that are Kotlin `object`s, via the static INSTANCE field.
 */
class SingletonInstantiatorStrategy(
    val fallback: InstantiatorStrategy
): BaseInstantiatorStrategy() {
    override fun <T : Any?> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
        return try {
            Instantiator(type.getDeclaredField("INSTANCE").get(null) as T)
        } catch (e: Exception) {
            fallback.newInstantiatorOf(type)
        }
    }

    private class Instantiator<T: Any?>(
        private val instance: T
    ): ObjectInstantiator<T> {
        override fun newInstance(): T = instance
    }
}