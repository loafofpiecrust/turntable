package com.loafofpiecrust.turntable.util

import android.util.Base64
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.firebase.firestore.Blob
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongId
import com.mcxiaoke.koi.ext.closeQuietly
import org.nustaq.serialization.FSTConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

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

private fun Kryo.objectToBytes(obj: Any, expectedSize: Int = 256, compress: Boolean = false): ByteArray {
    val baos = ByteArrayOutputStream(expectedSize)
    val os = Output(if (compress) DeflaterOutputStream(baos) else baos)
    writeClassAndObject(os, obj)
    os.flush()
    return baos.toByteArray().also { os.closeQuietly() }
}

private inline fun <reified T: Any> Kryo.objectFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return (readClassAndObject(input) as T).also { input.closeQuietly() }
}

private inline fun <reified T: Any> Kryo.concreteFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return readObject(input, T::class.java).also { input.closeQuietly() }
}

//
// FST
//
private val fst = FSTConfiguration.createAndroidDefaultConfiguration().apply {
    isForceSerializable = true
    registerClass(
        Album::class.java,
        Song::class.java,
        SongId::class.java,
        AlbumId::class.java,
        CollaborativePlaylist.Operation::class.java
    )
}
fun serialize(obj: Any): ByteArray {
    val output = fst.objectOutput
    output.writeObject(obj)
    return output.buffer
}

fun serializeToString(obj: Any) = Base64.encodeToString(serialize(obj), Base64.DEFAULT)
inline fun <T: Any> deserialize(input: String): T = deserialize(Base64.decode(input, Base64.DEFAULT))

fun serialize(stream: OutputStream, obj: Any) {
    val output = fst.getObjectOutput(stream)
    output.writeObject(obj)
    output.flush()
    stream.close()
}

fun <T: Any> deserialize(bytes: ByteArray): T {
    @Suppress("UNCHECKED_CAST")
    return fst.asObject(bytes) as T
}

fun <T: Any> deserialize(stream: InputStream): T {
    val input = fst.getObjectInput(stream)
    @Suppress("UNCHECKED_CAST")
    return (input.readObject() as T).also {
        stream.close()
    }
}

fun <T: Any> Blob.toObject(): T {
    return deserialize(toByteString().newInput())
}


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