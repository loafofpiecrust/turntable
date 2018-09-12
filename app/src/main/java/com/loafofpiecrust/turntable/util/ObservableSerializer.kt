package com.loafofpiecrust.turntable.util

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel


//class BehaviorSubjectSerializer<T: Any>(
//    t: Class<BehaviorSubject<T>>
//) : StdDelegatingSerializer<BehaviorSubject<T>>(t) {
//    override fun serialize(subject: BehaviorSubject<T>, gen: JsonGenerator, provider: SerializerProvider) {
//        if (!subject.hasValue()) {
//            gen.writeNull()
//        } else {
//            val value = subject.value
//            gen.writeObject(value)
//        }
//    }
//}

//class BehaviorSubjectSerializer<T: Any>: Serializer<BehaviorSubject<T>>() {
//    override fun write(kryo: Kryo, output: Output, subject: BehaviorSubject<T>) {
//        if (subject.hasValue()) {
//            kryo.writeClassAndObject(output, subject.value)
//        }
//    }
//
//    override fun read(kryo: Kryo, input: Input, c: Class<BehaviorSubject<T>>): BehaviorSubject<T> {
//        val value = kryo.readClassAndObject(input) as? T
//        return if (value != null) {
//            BehaviorSubject.createDefault(value)
//        } else {
//            BehaviorSubject.create()
//        }
//    }
//}

class CBCSerializer<T: Any>: Serializer<ConflatedBroadcastChannel<T>>() {
    override fun write(kryo: Kryo, output: Output, subject: ConflatedBroadcastChannel<T>) {
        if (subject.hasValue) {
            kryo.writeClassAndObject(output, subject.value)
        }
    }

    override fun read(kryo: Kryo, input: Input, c: Class<ConflatedBroadcastChannel<T>>): ConflatedBroadcastChannel<T> {
        @Suppress("UNCHECKED_CAST")
        val value = kryo.readClassAndObject(input) as? T
        return if (value != null) {
            ConflatedBroadcastChannel(value)
        } else {
            ConflatedBroadcastChannel()
        }
    }
}