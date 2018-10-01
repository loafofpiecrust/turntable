package com.loafofpiecrust.turntable.util

import java.math.BigInteger
import java.nio.charset.Charset

object UrlShortener {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:/_=-?&%"
    private const val BASE = ALPHABET.length
    private val BIG_BASE = BigInteger.valueOf(BASE.toLong())!!
//    private val ALPHABET_BREAKER = BASE / 2 // Values above this are positive, below are negative
    private const val BREAKER = Long.MAX_VALUE - 2
    private val CHARSET = Charset.forName("ISO-8859-1")

    private fun fromBase10(i: BigInteger): String {
        var i = i
        val sb = StringBuilder()
        if (i == BigInteger.ZERO) {
            return "a"
        }
        while (i > BigInteger.ZERO) {
            i = fromBase10(i, sb)
        }
        return sb.reverse().toString()
    }

    private fun fromBase10(i: BigInteger, sb: StringBuilder): BigInteger {
        val rem = i % BIG_BASE
        // rem could be [-BASE/2, +BASE/2], since java keeps negatives for remainder (yay!)
        // convert to [0, BASE] by adding BASE/2 (= ALPHABET_BREAKER)
        sb.append(ALPHABET[rem.toInt()])
        return i / BIG_BASE
    }

    private fun toBase10(str: String): BigInteger =
        toBase10(str.reversed().toCharArray())

    private fun toBase10(chars: CharArray): BigInteger {
        return chars.indices.reversed()
            .fold(BigInteger.ZERO) { acc, it ->
                val letterIdx = ALPHABET.indexOf(chars[it])
                acc + toBase10(letterIdx, it)
            }
    }

    private fun toBase10(n: Int, pow: Int): BigInteger =
        BIG_BASE.pow(pow) * BigInteger.valueOf(n.toLong())


    fun compress(url: String): String {
        val v = toBase10(url)
        val bytes = v.toByteArray()
        return String(bytes, CHARSET)
    }

    fun expand(encoded: String): String {
        val bytes = encoded.toByteArray(CHARSET)
        val num = BigInteger(bytes)
        return fromBase10(num)
    }
}
