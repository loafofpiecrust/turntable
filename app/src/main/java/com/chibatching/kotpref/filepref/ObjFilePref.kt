package com.chibatching.kotpref.filepref

import android.content.SharedPreferences
import com.chibatching.kotpref.pref.AbstractPref
import com.github.ajalt.timberkt.Timber
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.typedToJson
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.hasValue
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.map
import kotlinx.io.IOException
import kotlin.reflect.KProperty

abstract class ObjFilePref<T: Any>(
    val key: String,
    val default: T
): AbstractPref<T>() {
    private var lastWrite = 0L
    private val lastModify = subject.openSubscription().map {
        System.nanoTime()
    }.replayOne()


    private val saveActor = GlobalScope.actor<Unit>(capacity = Channel.CONFLATED) {
        for (e in channel) {
            doSave()
        }
    }

    protected val fileName: String get() = "$key.prop"

    private fun doSave() {
        if (lastModify.value < lastWrite) return
        try {
            val file = App.instance.filesDir.resolve(fileName)
            if (!file.exists()) {
                file.createNewFile()
            }

            lastWrite = System.nanoTime()
            file.writeText(serialize(subject.value))
        } catch (e: IOException) {
            Timber.e(e) { "Preference '$key' failed to save." }
        }
    }

    protected abstract fun serialize(obj: T): String

    fun save() {
        saveActor.offer(Unit)
    }


    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
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

    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
        return try {
            val file = App.instance.filesDir.resolve(fileName)
            if (file.exists()) {
                deserialize(file.readText())
            } else default
        } catch (e: IOException) {
            Timber.e(e) { "Preference '$key' failed to load." }
            default
        } catch (e: ClassCastException) {
            Timber.e(e) { "Preference '$key' had invalid data" }
            default
        }
    }

    protected abstract fun deserialize(input: String): T
}

inline fun <reified T: Any> objFilePref(
    key: String,
    default: T
) = object: ObjFilePref<T>(key, default) {
    override fun deserialize(input: String): T = try {
        App.gson.fromJson(input)
    } catch (e: Throwable) {
        Timber.e(e) { "Json failed to parse" }
        default
    }
    override fun serialize(obj: T): String = App.gson.typedToJson(obj)
}