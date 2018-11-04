package com.loafofpiecrust.turntable.util

import android.util.Base64
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.firebase.firestore.Blob
import com.loafofpiecrust.turntable.App
import com.mcxiaoke.koi.ext.closeQuietly
//import org.nustaq.serialization.FSTConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.*

//
// KRYO
//
private fun Kryo.concreteToBytes(obj: Any, expectedSize: Int = 256, compress: Boolean = false): ByteArray {
    val baos = ByteArrayOutputStream(expectedSize)
    val os = Output(if (compress) DeflaterOutputStream(baos) else baos)
    writeObject(os, obj)
    os.flush()
    return baos.toByteArray().also {
        os.close()
    }
}

fun Kryo.objectToBytes(obj: Any?, expectedSize: Int = 512, compress: Boolean = false): ByteArray {
    return if (compress) {
        val baos = ByteArrayOutputStream(expectedSize)
        val os = Output(DeflaterOutputStream(baos))
        writeClassAndObject(os, obj)
        os.close()
        baos.toByteArray()
    } else {
        val os = Output(expectedSize, -1)
        writeClassAndObject(os, obj)
        os.toBytes().also { os.close() }
    }
}

fun <T> Kryo.objectFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return (readClassAndObject(input) as T).also { input.close() }
}

private inline fun <reified T> Kryo.concreteFromBytes(
    bytes: ByteArray,
    decompress: Boolean = false
): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return readObject(input, T::class.java).also { input.close() }
}

//
// FST
//
//private val fst by lazy {
//    FSTConfiguration.createAndroidDefaultConfiguration().apply {
//        isForceSerializable = true
//        registerClass(
//            Album::class.java,
//            Song::class.java,
//            SongId::class.java,
//            AlbumId::class.java,
//            CollaborativePlaylist.Operation::class.java
//        )
//    }
//}
suspend fun serialize(obj: Any?): ByteArray {
//    val output = fst.objectOutput
//    output.writeObject(obj)
//    return output.buffer
//    return App.kryo.acquire(coroutineContext) { it.objectToBytes(obj) }
    return App.kryo.objectToBytes(obj)
}

suspend fun serialize(stream: OutputStream, obj: Any) {
//    val output = fst.getObjectOutput(stream)
//    output.writeObject(obj)
//    output.flush()
//    stream.close()
//    App.kryo.acquire(coroutineContext) {
    App.kryo.let {
        val output = Output(stream)
        it.writeClassAndObject(output, obj)
        output.flush()
        output.close()
    }
}

suspend fun deserialize(bytes: ByteArray): Any {
//    @Suppress("UNCHECKED_CAST")
//    return fst.asObject(bytes) as T
    return App.kryo.objectFromBytes(bytes)
}

fun deserialize(stream: InputStream): Any {
//    val input = fst.getObjectInput(stream)
//    @Suppress("UNCHECKED_CAST")
//    return (input.readObject() as T).also {
//        stream.close()
//    }
    return App.kryo.let {
        val input = Input(stream)
        val res = it.readClassAndObject(input)
        input.close()
        res
    }
}

// Generic serialization abstractions
suspend inline fun <reified T> Blob.toObject(): T {
    return deserialize(toByteString().newInput()) as T
}
suspend fun serializeToString(obj: Any): String = serialize(obj).toBase64()
suspend fun deserialize(input: String): Any = deserialize(input.fromBase64())

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)



//
// COMPRESSION
//
fun String.compress(): String {
    val compressed = ByteArray(length)
    val len = Deflater().run {
        setInput(toByteArray(Charsets.UTF_8))
        finish()
        val len = deflate(compressed)
        end()
        len
    }
    return String(compressed, 0, len, Charsets.ISO_8859_1)
}

fun ByteArray.compress(): ByteArray {
    val compressed = ByteArray(size)
    val len = Deflater().let { deflater ->
        deflater.setInput(this)
        deflater.finish()
        val len = deflater.deflate(compressed)
        deflater.end()
        len
    }
    return compressed.sliceArray(0..len)
}
//fun ByteArray.compress(): OutputStream {
//    return DeflaterOutputStream(ByteArrayOutputStream(thi))
//}

fun String.decompress(): String {
    val decompressed = ByteArray(length * 2)
    val len = Inflater().run {
        val bytes = toByteArray(Charsets.ISO_8859_1)
        setInput(bytes, 0, bytes.size)
        val len = inflate(decompressed)
        end()
        len
    }
    return String(decompressed, 0, len, Charsets.UTF_8)
}

fun ByteArray.decompress(): InputStream {
    return InflaterInputStream(ByteArrayInputStream(this))
}
