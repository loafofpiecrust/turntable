package com.chibatching.kotpref.gsonpref

import android.content.SharedPreferences
import com.chibatching.kotpref.Kotpref
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.pref.AbstractPref
import com.google.gson.Gson
import java.io.StringWriter
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * Gson object to serialize and deserialize delegated property
 */
//var Kotpref.gson: Gson?
//    get() {
//        return KotprefGsonHolder.gson
//    }
//    set(value) {
//        KotprefGsonHolder.gson = value
//    }

//val Kotpref.mapper get() = KotprefGsonHolder.mapper

/**
 * Delegate shared preference property serialized and deserialized by gson
 * @param default default gson object value
 * @param key custom preference key
 */
//inline fun <reified T : Any> KotprefModel.jsonPref(default: T, key: String? = null)
//        : ReadOnlyProperty<KotprefModel, BehaviorSubject<T>> = gsonPrefObj(default, key)

/**
 * Delegate shared preference property serialized and deserialized by gson
 * @param default default gson object value
 * @param key custom preference key resource id
 */
//inline fun <reified T : Any> KotprefModel.jsonPref(default: T, key: Int)
//        : ReadOnlyProperty<KotprefModel, BehaviorSubject<T>> = gsonPrefObj(default, context.getString(key))

//inline fun <reified T : Any> KotprefModel.gsonPref(default: T, key: Int)
//    : ReadOnlyProperty<KotprefModel, BehaviorSubject<T>> = GsonPref(T::class, default, context.getString(key))

/**
 * Delegate shared preference property serialized and deserialized by gson
 * @param default default gson object value
 * @param key custom preference key
 */
//inline fun <reified T : Any> KotprefModel.jsonNullablePref(default: T? = null, key: String? = null)
//        : ReadOnlyProperty<KotprefModel, BehaviorSubject<T?>> = gsonNullablePrefObj(default, key)

/**
 * Delegate shared preference property serialized and deserialized by gson
 * @param default default gson object value
 * @param key custom preference key resource id
 */
//inline fun <reified T : Any> KotprefModel.jsonNullablePref(default: T? = null, key: Int)
//        : ReadOnlyProperty<KotprefModel, BehaviorSubject<T?>> = gsonNullablePrefObj(default, context.getString(key))


//inline fun <reified T: Any> KotprefModel.jsonListPref(default: List<T> = listOf(), key: String? = null) =
//    object : AbstractPref<List<T>>() {
//        //class jsonListPref<T: Any>(val default: List<T> = listOf(), val key: String? = null): AbstractPref<List<T>>() {
//        override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): List<T> {
//            return preference.getString(key ?: property.id, null)?.let { json ->
//                try {
//                    deserializeFromJson(json) ?: default
//                } catch(e: Exception) {
//                    e.printStackTrace()
//                    default
//                }
//            } ?: default
//        }
//
//        override fun setToPreference(property: KProperty<*>, value: List<T>, preference: SharedPreferences) {
//            serializeToJson(value).let { json ->
//                preference.edit().putString(key ?: property.id, json).apply()
//            }
//        }
//
//        override fun setToEditor(property: KProperty<*>, value: List<T>, editor: SharedPreferences.Editor) {
//            serializeToJson(value).let { json ->
//                editor.putString(key ?: property.id, json)
//            }
//        }
//
//        private fun serializeToJson(value: List<T>): String? {
//            val stb = StringWriter()
//            return KotprefGsonHolder.mapper.let {
//                it.writeValue(stb, value)
//                stb.toString()
//            }
//        }
//
//        private fun deserializeFromJson(json: String): List<T>? {
//            return KotprefGsonHolder.mapper.let {
//                it.readValue(json)
//            }
//        }
//    }