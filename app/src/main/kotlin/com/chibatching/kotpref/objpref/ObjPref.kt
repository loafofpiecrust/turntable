package com.chibatching.kotpref.objpref

import android.content.SharedPreferences
import android.util.Base64
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlin.reflect.KProperty


class objPref<T: Any>(val default: T): AbstractPref<T>() {
    override fun getValue(thisRef: KotprefModel, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (!subject.hasValue) {
            task(BG_POOL) {
                getFromPreference(property, thisRef.kotprefPreference).also {
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
                deserialize<T>(it)
            } catch(e: Throwable) {
                task(UI) { e.printStackTrace() }
                default
            }
        } ?: default
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
        try {
            val bytes = serializeToString(value)
            preference.edit()
//                .putString(property.id, bytes.toString(Charsets.ISO_8859_1))
                .putString(property.name, bytes)
                .apply()
        } catch (e: Throwable) {
            task(UI) { e.printStackTrace() }
        }
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
        try {
            val bytes = serializeToString(value)
            editor.putString(property.name, bytes)
        } catch (e: Throwable) {
            task(UI) { e.printStackTrace() }
        }
    }
}