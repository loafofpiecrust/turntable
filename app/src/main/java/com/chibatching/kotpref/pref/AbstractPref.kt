package com.chibatching.kotpref.pref

import android.content.SharedPreferences
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.hasValue
import com.loafofpiecrust.turntable.util.skip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


abstract class AbstractPref<T: Any?>: ReadOnlyProperty<Any, ConflatedBroadcastChannel<T>> {
    protected var subject = ConflatedBroadcastChannel<T>()

    override operator fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (!subject.hasValue) {
//            launch(BG_POOL) {
                getFromPreference(property, UserPrefs.kotprefPreference).also {
                    subject.offer(it)
                }
//            }

            GlobalScope.launch(Dispatchers.Main) {
                subject.openSubscription()
                    .skip(1)
                    .consumeEach { value ->
                        if (UserPrefs.kotprefInTransaction) {
                            setToEditor(property, value, UserPrefs.kotprefEditor!!)
                        } else {
                            setToPreference(property, value, UserPrefs.kotprefPreference)
                        }
                    }
            }
        }
        return subject
    }

    abstract fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T
    abstract fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences)
    abstract fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor)
}