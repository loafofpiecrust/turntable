package com.loafofpiecrust.turntable

import android.os.Bundle
import androidx.test.runner.AndroidJUnit4
import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.util.ParcelSerializer
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import org.junit.Test
import org.junit.runner.RunWith

interface Above

@Serializable
private data class ChildThing(
    val a: Long,
    val b: Float
)

@Serializable
private data class TestThing(
    val child: ChildThing,
    val stuff: Int
): Above

@ImplicitReflectionSerializer
@RunWith(AndroidJUnit4::class)
class AndroidSerializationTest {
    @Test fun intoPolymorphicBundle() {
        val bundle = Bundle()
        val thing: Above = TestThing(ChildThing(7, 5.4f), 99)
        ParcelSerializer.serialize(bundle, PolymorphicSerializer, thing)
        val deser = ParcelSerializer.deserialize(bundle, PolymorphicSerializer)
        expect(deser).toBe(thing)
    }

    @Test fun intoJson() {
        val thing = TestThing(ChildThing(7, 5.4f), 99)
        val ser = JSON.stringify(PolymorphicSerializer, thing)
        println(ser)
        val deser = JSON.parse(PolymorphicSerializer, ser)
        expect(deser).toBe(thing)
    }
}