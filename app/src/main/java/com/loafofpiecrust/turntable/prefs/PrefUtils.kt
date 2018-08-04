package com.loafofpiecrust.turntable.prefs

import android.preference.*
import com.jaredrummler.android.colorpicker.ColorPreference
import com.loafofpiecrust.turntable.util.hasValue
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.ctx


fun PreferenceFragment.preferences(cb: PreferenceScreen.() -> Unit) {
    val screen = preferenceManager.createPreferenceScreen(ctx)
    preferenceScreen = screen
    cb(screen)
}

fun PreferenceGroup.category(title: String? = null, cb: PreferenceCategory.() -> Unit): PreferenceCategory {
    val cat = PreferenceCategory(context)
    if (title != null) {
        cat.title = title
    }
    addPreference(cat)
    cb(cat)
    return cat
}

fun PreferenceGroup.screen(title: String? = null, cb: PreferenceScreen.() -> Unit): PreferenceScreen {
    val screen = preferenceManager.createPreferenceScreen(context)
    if (title != null) {
        screen.title = title
    }
    addPreference(screen)
    cb(screen)
    return screen
}


fun <T: Preference, R: Any> PreferenceGroup.basicPref(inst: T, pref: ConflatedBroadcastChannel<R>, title: String, cb: T.() -> Unit): T {
//    inst.key = key
    inst.title = title
    cb(inst)
    if (pref.hasValue) {
        inst.setDefaultValue(pref.value)
    }
    inst.setOnPreferenceChangeListener { _, valueObj ->
        @Suppress("UNCHECKED_CAST") // I know what I'm doing
        val value = valueObj as R
        runBlocking { pref.send(valueObj) }
        true
    }
    addPreference(inst)
    return inst
}

fun <T: Preference, R: Any, P: Any> PreferenceGroup.basicPref(inst: T, pref: ConflatedBroadcastChannel<R>, title: String, transform: (P) -> R, transformBack: (R) -> P, cb: T.() -> Unit): T {
//    inst.key = key
    inst.title = title
    cb(inst)
    if (pref.hasValue) {
        inst.setDefaultValue(transformBack(pref.value))
    }
    inst.setOnPreferenceChangeListener { _, valueObj ->
        @Suppress("UNCHECKED_CAST") // I know what I'm doing
        val value = valueObj as P
        runBlocking {
            pref.send(transform(valueObj))
        }
        true
    }
    addPreference(inst)
    return inst
}


fun PreferenceGroup.checkBox(
    pref: ConflatedBroadcastChannel<Boolean>,
    title: String,
    cb: CheckBoxPreference.() -> Unit = {}
) = basicPref(CheckBoxPreference(context), pref, title, cb)

fun PreferenceGroup.editText(
    pref: ConflatedBroadcastChannel<String>,
    title: String,
    cb: EditTextPreference.() -> Unit = {}
) = basicPref(EditTextPreference(context), pref, title, cb)

fun PreferenceGroup.list(
    pref: ConflatedBroadcastChannel<String>,
    title: String,
    cb: ListPreference.() -> Unit = {}
) = basicPref(ListPreference(context), pref, title, cb)

fun <T: Any> PreferenceGroup.list(
    pref: ConflatedBroadcastChannel<T>,
    title: String,
    transform: (String) -> T,
    cb: ListPreference.() -> Unit = {}
) = basicPref(ListPreference(context), pref, title, transform, { it.toString() }, cb)

fun PreferenceGroup.multiSelectList(
    pref: ConflatedBroadcastChannel<Set<String>>,
    title: String,
    cb: MultiSelectListPreference.() -> Unit = {}
) = basicPref(
    MultiSelectListPreference(context),
    pref, title,
    cb
)

fun PreferenceGroup.switch(
    pref: ConflatedBroadcastChannel<Boolean>,
    title: String,
    cb: SwitchPreference.() -> Unit = {}
) = basicPref(SwitchPreference(context), pref, title, cb)

fun PreferenceGroup.color(
    pref: ConflatedBroadcastChannel<Int>,
    title: String,
    cb: ColorPreference.() -> Unit = {}
) = basicPref(ColorPreference(context, null), pref, title, cb)
