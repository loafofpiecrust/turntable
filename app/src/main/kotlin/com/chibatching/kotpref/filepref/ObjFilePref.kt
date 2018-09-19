package com.chibatching.kotpref.filepref

import android.content.SharedPreferences
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import java.io.FileOutputStream
import kotlin.reflect.KProperty

abstract class BaseObjFilePref<T: Any>(
    open val default: T? = null
): AbstractPref<T>(default) {
    private var lastWrite = 0L
    protected var lastModify = 0L
    var name: String? = null

//    private sealed class Action {
//        object Save: Action()
//        data class Load(
//            val thisRef: KotprefModel,
//            val property: KProperty<*>
//        ): Action()
//    }
//    private val saveActor = actor<Action>(BG_POOL, capacity = Channel.CONFLATED) {
//        consumeEach { e ->
//            if (e is Action.Save) {
//            }
//        }
//    }


    @Synchronized
    fun save() {
        if (name == null) return
        try {
            val file = App.instance.filesDir.resolve("$name.prop")
            if (!file.exists()) {
                file.createNewFile()
            }

            runBlocking { serialize(file.outputStream(), subject.value) }
            lastWrite = System.nanoTime()
        } catch (e: Throwable) {
            task(UI) { e.printStackTrace() }
        }
//        return runBlocking { saveActor.send(Action.Save) }
    }


    override fun getValue(thisRef: KotprefModel, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (!subject.hasValue) {
            getFromPreference(property, thisRef.kotprefPreference).also {
                subject.offer(it)
            }
        }
        return subject
    }
}

class objFilePref<T: Any>(default: T): BaseObjFilePref<T>(default) {
//    init {
//        subject.consumeEach(BG_POOL) {
//            lastModify = System.nanoTime()
//        }
//    }

    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
        name = property.name
        val res = try {
            val file = App.instance.filesDir.resolve("$name.prop")
            if (file.exists()) {
                runBlocking { deserialize<T>(file.inputStream()) }
            } else default
        } catch (e: Exception) {
            task(UI) { e.printStackTrace() }
            default
        }

        return res!!
//        return preference.getString(property.id, null)?.let {
//            try {
//                val input = Input(it.toByteArray(Charsets.ISO_8859_1))
//                val res = App.kryo.readClassAndObject(input) as? T
//                input.close()
//                res
//            } catch(e: Exception) {
//                default
//            }
//        } ?: default
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
//        lastValue = value
        name = property.name
//        val now = System.nanoTime()
//        if ((now - lastWrite) > TimeUnit.SECONDS.toNanos(10)) {
//            save()
//        }
//        val os = Output(2048, -1)
//        App.kryo.writeClassAndObject(os, value)
//        preference.edit()
//            .putString(property.id, os.buffer.toString(Charsets.ISO_8859_1))
//            .apply()
//        os.closeQuietly()
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
//        lastValue = value
//        val os = Output(2048, -1)
//        App.kryo.writeClassAndObject(os, value)
//        editor.putString(property.id, os.buffer.toString(Charsets.ISO_8859_1))
//        os.closeQuietly()
        name = property.name
//        val now = System.nanoTime()
//        if ((now - lastWrite) > TimeUnit.SECONDS.toNanos(10)) {
//            save()
//        }
    }
}