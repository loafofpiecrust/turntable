package com.chibatching.kotpref

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import com.chibatching.kotpref.filepref.BaseObjFilePref
import com.chibatching.kotpref.filepref.objFilePref
import com.chibatching.kotpref.pref.*
import com.loafofpiecrust.turntable.awaitAll
import com.loafofpiecrust.turntable.awaitAllNotNull
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty



abstract class KotprefModel {

    internal var kotprefInTransaction: Boolean = false
    internal var kotprefTransactionStartTime: Long = 0

    /**
     * Context set to Kotpref
     */
    val context: Context by lazy { Kotpref.context!! }

    protected val files = mutableListOf<BaseObjFilePref<*>>()

    /**
     * Preference file id
     */
    protected open val kotprefName: String = javaClass.simpleName

    /**
     * Preference read/write mode
     */
    protected open val kotprefMode: Int = Context.MODE_PRIVATE

    /**
     * Internal shared preference.
     * This property will be initialized on use.
     */
    internal val kotprefPreference: KotprefPreferences by lazy {
        KotprefPreferences(context.getSharedPreferences(kotprefName, kotprefMode))
    }

    /**
     * Internal shared preference editor.
     */
    internal var kotprefEditor: KotprefPreferences.KotprefEditor? = null

    /**
     * Clear all preferences in this model
     */
    fun clear() {
        beginBulkEdit()
        kotprefEditor!!.clear()
        commitBulkEdit()
    }

    /**
     * Delegate string shared preference property.
     * @param default default string value
     * @param key custom preference key
     */
    protected fun stringPref(default: String = "", key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<String>> = StringPref(default, key)

    /**
     * Delegate string shared preference property.
     * @param default default string value
     * @param key custom preference key resource id
     */
    protected fun stringPref(default: String = "", key: Int)
            = stringPref(default, context.getString(key))

    /**
     * Delegate nullable string shared preference property.
     * @param default default string value
     * @param key custom preference key
     */
    protected fun nullableStringPref(default: String? = null, key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<String?>> = StringNullablePref(default, key)

    /**
     * Delegate nullable string shared preference property.
     * @param default default string value
     * @param key custom preference key resource id
     */
    protected fun nullableStringPref(default: String? = null, key: Int)
            = nullableStringPref(default, context.getString(key))

    /**
     * Delegate int shared preference property.
     * @param default default int value
     * @param key custom preference key
     */
    protected fun intPref(default: Int = 0, key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<Int>> = IntPref(default, key)

    /**
     * Delegate int shared preference property.
     * @param default default int value
     * @param key custom preference key resource id
     */
    protected fun intPref(default: Int = 0, key: Int)
            = intPref(default, context.getString(key))

    /**
     * Delegate long shared preference property.
     * @param default default long value
     * @param key custom preference key
     */
    protected fun longPref(default: Long = 0L, key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<Long>> = LongPref(default, key)

    /**
     * Delegate long shared preference property.
     * @param default default long value
     * @param key custom preference key resource id
     */
    protected fun longPref(default: Long = 0L, key: Int)
            = longPref(default, context.getString(key))

    /**
     * Delegate float shared preference property.
     * @param default default float value
     * @param key custom preference key
     */
    protected fun floatPref(default: Float = 0F, key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<Float>> = FloatPref(default, key)

    /**
     * Delegate float shared preference property.
     * @param default default float value
     * @param key custom preference key resource id
     */
    protected fun floatPref(default: Float = 0F, key: Int)
            = floatPref(default, context.getString(key))

    /**
     * Delegate boolean shared preference property.
     * @param default default boolean value
     * @param key custom preference key
     */
    protected fun booleanPref(default: Boolean = false, key: String? = null)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<Boolean>> = BooleanPref(default, key)

    /**
     * Delegate boolean shared preference property.
     * @param default default boolean value
     * @param key custom preference key resource id
     */
    protected fun booleanPref(default: Boolean = false, key: Int)
            = booleanPref(default, context.getString(key))

    /**
     * Delegate string set shared preference property.
     * @param default default string set value
     * @param key custom preference key
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected fun stringSetPref(default: Set<String> = LinkedHashSet(), key: String? = null)
            = stringSetPref(key) { default }

    /**
     * Delegate string set shared preference property.
     * @param default default string set value
     * @param key custom preference key resource id
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected fun stringSetPref(default: Set<String> = LinkedHashSet(), key: Int)
            = stringSetPref(context.getString(key)) { default }

    /**
     * Delegate string set shared preference property.
     * @param key custom preference key
     * @param default default string set value creation function
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected fun stringSetPref(key: String? = null, default: () -> Set<String>)
            : ReadOnlyProperty<KotprefModel, ConflatedBroadcastChannel<Set<String>>> = StringSetPref(default, key)

    /**
     * Delegate string set shared preference property.
     * @param key custom preference key resource id
     * @param default default string set value
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected fun stringSetPref(key: Int, default: () -> Set<String>)
            = stringSetPref(context.getString(key), default)


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected inline fun <reified T: Any> pref(default: T)
        = com.chibatching.kotpref.filepref.objFilePref(default).also { files.add(it) }

    /**
     * Begin bulk edit mode. You must commit or cancel after bulk edit finished.
     */
    fun beginBulkEdit() {
        kotprefInTransaction = true
        kotprefTransactionStartTime = System.currentTimeMillis()
        kotprefEditor = kotprefPreference.KotprefEditor(kotprefPreference.edit())
    }

    /**
     * Commit values set in the bulk edit mode to preferences.
     */
    fun commitBulkEdit() {
        kotprefEditor!!.apply()
        kotprefInTransaction = false
    }

    /**
     * Commit values set in the bulk edit mode to preferences immediately, in blocking manner.
     */
    fun blockingCommitBulkEdit() {
        kotprefEditor!!.commit()
        kotprefInTransaction = false
    }

    /**
     * Cancel bulk edit mode. Values set in the bulk mode will be rolled back.
     */
    fun cancelBulkEdit() {
        kotprefEditor = null
        kotprefInTransaction = false
    }

    suspend fun saveFiles() {
        files.mapNotNull { it.save() }.awaitAll()
    }
}

