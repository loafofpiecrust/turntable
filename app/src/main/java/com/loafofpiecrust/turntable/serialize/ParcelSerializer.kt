package com.loafofpiecrust.turntable.serialize

import android.os.Bundle
import kotlinx.serialization.*


object ParcelSerializer {
    fun <T> serialize(bundle: Bundle, saver: SerializationStrategy<T>, obj: T) {
        BundleOutput(bundle).encode(saver, obj)
    }

    fun <T> deserialize(bundle: Bundle, loader: DeserializationStrategy<T>): T {
        return BundleInput(bundle).decode(loader)
    }
}

class BundleOutput(
    private val rootBundle: Bundle
): NamedValueEncoder() {
    private val stack = mutableListOf<Pair<String, Bundle>>()

    private val topBundle: Bundle
        get() = stack.lastOrNull()?.second ?: rootBundle

    override fun encodeTaggedInt(tag: String, value: Int) {
        topBundle.putInt(tag, value)
    }

    override fun encodeTaggedBoolean(tag: String, value: Boolean) {
        topBundle.putBoolean(tag, value)
    }

    override fun encodeTaggedByte(tag: String, value: Byte) {
        topBundle.putByte(tag, value)
    }

    override fun encodeTaggedChar(tag: String, value: Char) {
        topBundle.putChar(tag, value)
    }

    override fun encodeTaggedDouble(tag: String, value: Double) {
        topBundle.putDouble(tag, value)
    }

    override fun encodeTaggedString(tag: String, value: String) {
        topBundle.putString(tag, value)
    }

    override fun encodeTaggedFloat(tag: String, value: Float) {
        topBundle.putFloat(tag, value)
    }

    override fun encodeTaggedLong(tag: String, value: Long) {
        topBundle.putLong(tag, value)
    }

    override fun encodeTaggedShort(tag: String, value: Short) {
        topBundle.putShort(tag, value)
    }

    override fun encodeTaggedNull(tag: String) {
        topBundle.putSerializable(tag, null)
    }

    /// Beginning of sub-object
    override fun beginStructure(
        desc: SerialDescriptor,
        vararg typeParams: KSerializer<*>
    ): CompositeEncoder {
        val tag = currentTagOrNull
        if (tag != null) {
            stack.add(tag to Bundle())
        }
        return this
    }

    override fun endEncode(desc: SerialDescriptor) {
        if (stack.isNotEmpty()) {
            val (key, child) = stack.removeAt(stack.size - 1)
            topBundle.putBundle(key, child)
        }
    }
}

class BundleInput(
    private val rootBundle: Bundle
): NamedValueDecoder() {
    private val stack = mutableListOf<Bundle>()
    private val topBundle: Bundle
        get() = stack.lastOrNull() ?: rootBundle

    override fun decodeTaggedValue(tag: String): Any {
        val res = topBundle.getSerializable(tag)
        return res ?: super.decodeTaggedValue(tag)
    }

    override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
        val tag = currentTagOrNull
        if (tag != null) {
            stack.add(topBundle.getBundle(tag)!!)
        }
        return this
    }

    override fun endStructure(desc: SerialDescriptor) {
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.size - 1)
        }
    }
}