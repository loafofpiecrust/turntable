package com.chibatching.kotpref.filepref

import android.content.SharedPreferences
import com.chibatching.kotpref.pref.AbstractPref
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import kotlin.reflect.KProperty

abstract class BaseObjFilePref<T: Any>(
    val default: T
): AbstractPref<T>(), AnkoLogger {
    private var lastWrite = 0L
    private val lastModify = subject.openSubscription().map {
        System.nanoTime()
    }.replayOne()
    private var className: String? = null
    private var name: String? = null


//    private sealed class Action {
//        object Save: Action()
//        data class Load(
//            val thisRef: KotprefModel,
//            val property: KProperty<*>
//        ): Action()
//    }
    private val saveActor = GlobalScope.actor<Unit>(capacity = Channel.CONFLATED) {
        consumeEach {
            doSave()
        }
    }

    protected val fileName: String get() = "$className.$name.prop"

    private fun doSave() {
        if (name == null || className == null || lastModify.value < lastWrite) return
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

    fun save() {
        saveActor.offer(Unit)
    }


    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        className = thisRef.javaClass.scopedName
        name = property.name

        if (!subject.hasValue) {
            getFromPreference(property, UserPrefs.kotprefPreference).also {
                subject.offer(it)
            }
        }
        return subject
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
        save()
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
        save()
    }
}

inline fun <reified T: Any> objFilePref(default: T) = object: BaseObjFilePref<T>(default) {
    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
        return try {
            val file = App.instance.filesDir.resolve(fileName)
            if (file.exists()) {
                deserialize(file.inputStream()) as T
            } else default
        } catch (e: Exception) {
            error("Preference '${property.name}' failed to load.", e)
            default
        }
    }
}