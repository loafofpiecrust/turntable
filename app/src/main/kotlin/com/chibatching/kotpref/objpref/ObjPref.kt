package com.chibatching.kotpref.objpref

import android.content.SharedPreferences
import android.util.Base64
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.InputChunked
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.io.OutputChunked
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.objectFromBytes
import com.loafofpiecrust.turntable.objectToBytes
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.hasValue
import com.loafofpiecrust.turntable.util.task
import com.mcxiaoke.koi.ext.closeQuietly
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
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
                App.kryo.objectFromBytes<T>(Base64.decode(it, Base64.NO_WRAP))
            } catch(e: Throwable) {
                task(UI) { e.printStackTrace() }
                default
            }
        } ?: default
    }

    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
        try {
            val bytes = App.kryo.objectToBytes(value)
            preference.edit()
//                .putString(property.id, bytes.toString(Charsets.ISO_8859_1))
                .putString(property.name, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
        } catch (e: Throwable) {
            task(UI) { e.printStackTrace() }
        }
    }

    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
        try {
            val bytes = App.kryo.objectToBytes(value)
//            editor.putString(property.id, bytes.toString(Charsets.ISO_8859_1))
            editor.putString(property.name, Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (e: Throwable) {
            task(UI) { e.printStackTrace() }
        }
    }
}