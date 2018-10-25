package com.chibatching.kotpref.objpref

import android.content.SharedPreferences
import android.util.Base64
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.error
import kotlin.reflect.KProperty


class objPref<T: Any>(val default: T): AbstractPref<T>() {
    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (!subject.hasValue) {
            GlobalScope.launch {
                getFromPreference(property, UserPrefs.kotprefPreference).also {
                    subject.offer(it)
                }
            }
        }
        return subject
    }

    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
        return preference.getString(property.name, null)?.let {
            try {
//                App.kryo.objectFromBytes<T>(it.toByteArray(Charsets.ISO_8859_1))
                runBlocking { deserialize(it) as T }
            } catch(e: Throwable) {
                error("Property ${property.name} failed to load, using default value.", e)
                default
            }
        } ?: default
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
        try {
            val bytes = runBlocking { serializeToString(value) }
            preference.edit()
//                .putString(property.uuid, bytes.toString(Charsets.ISO_8859_1))
                .putString(property.name, bytes)
                .apply()
        } catch (e: Throwable) {
            error(null, e)
        }
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
        try {
            val bytes = runBlocking { serializeToString(value) }
            editor.putString(property.name, bytes)
        } catch (e: Throwable) {
            error(null, e)
        }
    }
}