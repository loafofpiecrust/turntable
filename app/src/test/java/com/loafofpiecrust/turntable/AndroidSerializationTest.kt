package com.loafofpiecrust.turntable

import android.os.Bundle
import androidx.test.runner.AndroidJUnit4
import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.serialize.ParcelSerializer
import com.loafofpiecrust.turntable.serialize.TypedJson
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import org.junit.Test
import org.junit.runner.RunWith

//@Serializable(with = SuperTypeSerializer::class)
interface Above

@Serializable
data class GenericThing<T: Any>(val value: T)

@Serializable
private data class ChildThing(
    val a: Long,
    val b: Float
)

@Serializable
private data class TestThing(
    val child: ChildThing,
    val stuff: Int,
    val generic: GenericThing<Double>
): Above

@Serializable
private data class Concrete(val f: Int = 7): Above

@ImplicitReflectionSerializer
@RunWith(AndroidJUnit4::class)
class AndroidSerializationTest {
    @Test fun intoPolymorphicBundle() {
        val bundle = Bundle()
        val thing: Above = TestThing(ChildThing(7, 5.4f), 99, GenericThing(3.14))
        ParcelSerializer.serialize(bundle, PolymorphicSerializer, thing)
        val deser = ParcelSerializer.deserialize(bundle, PolymorphicSerializer)
        expect(deser).toBe(thing)
    }

    @Test fun intoJson() {
        val thing = TestThing(ChildThing(7, 5.4f), 99, GenericThing(3.14))
        val ser = JSON.stringify(thing)
        println(ser)
        val deser = JSON.parse<TestThing>(ser)
        expect(deser).toBe(thing)
    }

//    @Test fun polymorphicJson() {
//        val thing: Message = PlayerAction.QueuePosition(2)
//        val json = JSON().apply { install(TypedJson.module) }
//        val ser = json.stringify(thing)
//        println(ser)
//        val deser = json.parse<Message>(ser)
//        expect(deser).toBe(thing)
//    }
}