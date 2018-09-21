package com.loafofpiecrust.turntable.model

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.util.produceTask
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.test.Test
import kotlin.test.expect


class ChannelOpsTests {
    @Test fun `switch map`() = runBlocking<Unit> {
        val channel = ConflatedBroadcastChannel<Int>()
        val mapped = channel.openSubscription().switchMap {
            when (it) {
                1 -> produce { send(10) }
                2 -> produce { send(20) }
                else -> produce { send(30) }
            }
        }
        channel.send(2)
        expect(mapped.receive()).toBe(20)
        channel.send(1)
        expect(mapped.receive()).toBe(10)
        channel.send(3)
        expect(mapped.receive()).toBe(30)
    }

    @Test fun `it switchMaps`() = runBlocking<Unit> {
        val source = produce(coroutineContext) {
            send(1)
            delay(10)
            send(2)
            delay(100)
            send(3)
        }

        val switchMapped = source.switchMap(coroutineContext) { i ->
            produce {
                send("${i}A")
                send("${i}B")
                delay(15)
                send("${i}C")
            }
        }

        expect(switchMapped.toList()).toBe(listOf("1A", "1B", "2A", "2B", "2C"))
    }
}