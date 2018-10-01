package com.chibatching.kotpref.pref

import android.content.SharedPreferences
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlin.reflect.KProperty


internal class IntPref(val default: Int, val key: String?) : AbstractPref<Int>() {

    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): Int {
        async(UI) { println("music: getting pref from '${property.name}'") }
        return preference.getInt(key ?: property.name, default)
    }

    override fun setToPreference(property: KProperty<*>, value: Int, preference: SharedPreferences) {
        preference.edit().putInt(key ?: property.name, value).apply()
    }

    override fun setToEditor(property: KProperty<*>, value: Int, editor: SharedPreferences.Editor) {
        editor.putInt(key ?: property.name, value)
    }
}
