package com.chibatching.kotpref.pref

import android.content.SharedPreferences
import com.chibatching.kotpref.KotprefModel
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoLogger
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


abstract class AbstractPref<T: Any?>: ReadOnlyProperty<Any, ConflatedBroadcastChannel<T>>, AnkoLogger {
    protected var subject = ConflatedBroadcastChannel<T>()

    override operator fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (!subject.hasValue) {
//            launch(BG_POOL) {
                getFromPreference(property, UserPrefs.kotprefPreference).also {
                    subject.offer(it)
                }
//            }

//            subject.openSubscription()
//                .skip(1)
//                .consumeEach(UI) { value ->
//                    if (thisRef.kotprefInTransaction) {
//                        setToEditor(property, value, thisRef.kotprefEditor!!)
//                    } else {
//                        setToPreference(property, value, thisRef.kotprefPreference)
//                    }
//                }
        }
        return subject
    }

    abstract fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T
    abstract fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences)
    abstract fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor)
}