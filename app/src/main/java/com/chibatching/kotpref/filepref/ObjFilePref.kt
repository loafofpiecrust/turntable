package com.chibatching.kotpref.filepref

import android.content.SharedPreferences
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import java.io.FileOutputStream
import kotlin.reflect.KProperty

abstract class BaseObjFilePref<T: Any>(
    open val default: T? = null
): AbstractPref<T>(default), AnkoLogger {
    private var lastWrite = 0L
    protected var lastModify = 0L
    private var className: String? = null
    private var name: String? = null

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

    protected val fileName: String get() = "$className.$name.prop"

    fun save() {
        if (name == null || className == null || lastModify < lastWrite) return
        try {
            val file = App.instance.filesDir.resolve(fileName)
            if (!file.exists()) {
                file.createNewFile()
            }

            lastWrite = System.nanoTime()
            runBlocking { serialize(file.outputStream(), subject.value) }
        } catch (e: Throwable) {
            error("Preference '$name' failed to save.", e)
        }
    }


    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        className = thisRef.javaClass.simpleName
        name = property.name

        if (!subject.hasValue) {
            getFromPreference(property, UserPrefs.kotprefPreference).also {
                subject.offer(it)
            }
        }
        return subject
    }
}

class objFilePref<T: Any>(default: T): BaseObjFilePref<T>(default) {
    init {
        subject.consumeEach(BG_POOL) {
            lastModify = System.nanoTime()
        }
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
    }

    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
        val res = try {
            val file = App.instance.filesDir.resolve(fileName)
            if (file.exists()) {
                runBlocking { deserialize<T>(file.inputStream()) }
            } else default
        } catch (e: Throwable) {
            error("Preference '${property.name}' failed to load.", e)
            default
        }

        return res!!
    }
}