package com.loafofpiecrust.turntable

import android.app.Activity
import ch.tutteli.atrium.api.cc.en_GB.isA
import ch.tutteli.atrium.creating.Assert
import kotlinx.coroutines.experimental.runBlocking
import org.robolectric.Robolectric
import pl.miensol.shouldko.internal.addSourceLineToAssertionError
import kotlin.test.assertEquals


inline fun <reified TSub : Any> Assert<Any>.isA() = isA<TSub> {}
inline fun test(crossinline block: suspend () -> Unit) = addSourceLineToAssertionError {
    runBlocking { block() }
}

fun <T> T.shouldBe(other: T) = addSourceLineToAssertionError {
    assertEquals(other, this)
}