package com.loafofpiecrust.turntable.util

import android.util.Base64
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.firebase.firestore.Blob
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.mcxiaoke.koi.ext.closeQuietly
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.selects.select
import org.nustaq.serialization.FSTConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.coroutines.experimental.coroutineContext

//
// KRYO
//
private fun Kryo.concreteToBytes(obj: Any, expectedSize: Int = 256, compress: Boolean = false): ByteArray {
    val baos = ByteArrayOutputStream(expectedSize)
    val os = Output(if (compress) DeflaterOutputStream(baos) else baos)
    writeObject(os, obj)
    os.flush()
    return baos.toByteArray().also {
        os.closeQuietly()
    }
}

fun Kryo.objectToBytes(obj: Any?, expectedSize: Int = 512, compress: Boolean = false): ByteArray {
    return if (compress) {
        val baos = ByteArrayOutputStream(expectedSize)
        val os = Output(DeflaterOutputStream(baos))
        writeClassAndObject(os, obj)
        os.closeQuietly()
        baos.toByteArray()
    } else {
        val os = Output(expectedSize, -1)
        writeClassAndObject(os, obj)
        os.toBytes().also { os.closeQuietly() }
    }
}

fun <T> Kryo.objectFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return (readClassAndObject(input) as T).also { input.closeQuietly() }
}

private inline fun <reified T> Kryo.concreteFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return readObject(input, T::class.java).also { input.closeQuietly() }
}

//
// FST
//
private val fst by lazy {
    FSTConfiguration.createAndroidDefaultConfiguration().apply {
        isForceSerializable = true
        registerClass(
            Album::class.java,
            Song::class.java,
            SongId::class.java,
            AlbumId::class.java,
            CollaborativePlaylist.Operation::class.java
        )
    }
}
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

suspend fun <T> deserialize(bytes: ByteArray): T {
//    @Suppress("UNCHECKED_CAST")
//    return fst.asObject(bytes) as T
    return App.kryo.objectFromBytes(bytes)
}

suspend fun <T> deserialize(stream: InputStream): T {
//    val input = fst.getObjectInput(stream)
//    @Suppress("UNCHECKED_CAST")
//    return (input.readObject() as T).also {
//        stream.close()
//    }
    return App.kryo.let {
        val input = Input(stream)
        @Suppress("UNCHECKED_CAST")
        val res = it.readClassAndObject(input) as T
        input.close()
        res
    }
}

// Generic serialization abstractions
suspend fun <T: Any> Blob.toObject(): T {
    return deserialize(toByteString().newInput())
}
suspend fun serializeToString(obj: Any): String = Base64.encodeToString(serialize(obj), Base64.NO_WRAP)
suspend fun <T: Any> deserialize(input: String): T = deserialize(Base64.decode(input, Base64.NO_WRAP))



//
// COMPRESSION
//
fun compress(input: String): String {
    val compressed = ByteArray(input.length)
    val len = Deflater().run {
        setInput(input.toByteArray(Charsets.UTF_8))
        finish()
        val len = deflate(compressed)
        end()
        len
    }
    return String(compressed, 0, len, Charsets.ISO_8859_1)
}

fun decompress(input: String): String {
    val decompressed = ByteArray(input.length * 2)
    val len = Inflater().run {
        val bytes = input.toByteArray(Charsets.ISO_8859_1)
        setInput(bytes, 0, bytes.size)
        val len = inflate(decompressed)
        end()
        len
    }
    return String(decompressed, 0, len, Charsets.UTF_8)
}

