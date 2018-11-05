package com.loafofpiecrust.turntable.util

import kotlin.math.roundToInt
import kotlin.math.roundToLong

interface Duration: Comparable<Duration> {
    val value: Double
    val longValue: Long get() = value.roundToLong()
    val intValue: Int get() = value.roundToInt()
    fun toMillis(): Double

    override fun compareTo(other: Duration): Int =
        toMillis().compareTo(other.toMillis())
}

fun Duration.toMicroseconds(): Double = toMillis() * 1000
fun Duration.toNanoseconds(): Double = toMicroseconds() * 1000

private inline fun Duration.op(other: Duration, block: (Double, Double) -> Double): Duration =
    Milliseconds(block(toMillis(), other.toMillis()))

operator fun Duration.minus(other: Duration): Duration =
    op(other) { a, b -> a - b }

operator fun Duration.plus(other: Duration): Duration =
    op(other) { a, b -> a + b }

operator fun Duration.times(other: Duration): Duration =
    op(other) { a, b -> a * b }

operator fun Duration.div(other: Duration): Duration =
    op(other) { a, b -> a / b }

/**
 * TODO: Use inline classes when they are stabilized.
 */

data class Milliseconds(override val value: Double): Duration {
    override fun toMillis(): Double = value
}
inline val Number.milliseconds get() = Milliseconds(toDouble())


data class Seconds(override val value: Double): Duration {
    override fun toMillis(): Double = value * 1000
}
inline val Number.seconds get() = Seconds(toDouble())
fun Duration.inSeconds() = Seconds(toMillis() / 1000)


data class Minutes(override val value: Double): Duration {
    override fun toMillis(): Double =
        Seconds(value * 60).toMillis()
}
inline val Number.minutes get() = Minutes(toDouble())
fun Duration.inMinutes() = Minutes(toMillis() / 60000)


data class Hours(override val value: Double): Duration {
override fun toMillis(): Double =
    Minutes(value * 60).toMillis()
}
inline val Number.hours get() = Hours(toDouble())


data class Days(override val value: Double): Duration {
    override fun toMillis(): Double =
        Hours(value * 24).toMillis()
}
inline val Number.days get() = Days(toDouble())



fun currentTime(): Duration {
    return System.currentTimeMillis().milliseconds
}