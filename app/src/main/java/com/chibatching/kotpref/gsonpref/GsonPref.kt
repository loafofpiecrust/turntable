package com.chibatching.kotpref.gsonpref


//class GsonPref<T : Any>(val targetClass: KClass<T>, val default: T, val key: String?) : AbstractPref<T>() {
//
//    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
//        return preference.getString(key ?: property.uuid, null)?.let { json ->
//            deserializeFromJson(json) ?: default
//        } ?: default
//    }
//
//    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
//        serializeToJson(value).let { json ->
//            preference.edit().putString(key ?: property.uuid, json).apply()
//        }
//    }
//
//    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
//        serializeToJson(value).let { json ->
//            editor.putString(key ?: property.uuid, json)
//        }
//    }
//
//    private fun serializeToJson(value: T?): String? {
//        return Kotpref.gson.let {
//            if (it == null) throw IllegalStateException("Gson has not been set to Kotpref")
//
//            it.toJson(value)
//        }
//    }
//
//    private fun deserializeFromJson(json: String): T? {
//        return Kotpref.gson.let {
//            if (it == null) throw IllegalStateException("Gson has not been set to Kotpref")
//
//            it.fromJson(json, targetClass.java)
//        }
//    }
//}


//inline fun <reified T: Any> KotprefModel.jsonPref(default: T, key: String? = null) =
//    object : AbstractPref<T>() {
//        override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
//            return preference.getString(key ?: property.uuid, null)?.let { json ->
//                try {
//                    deserializeFromJson(json) ?: default
//                } catch(e: Exception) {
//                    e.printStackTrace()
//                    default
//                }
//            } ?: default
//        }
//
//        override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
//            serializeToJson(value).let { json ->
//                preference.edit().putString(key ?: property.uuid, json).apply()
//            }
//        }
//
//        override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
//            serializeToJson(value).let { json ->
//                editor.putString(key ?: property.uuid, json)
//            }
//        }
//
//        private fun serializeToJson(value: T): String? =
//            App.gson.typedToJson(value)
//
//        private fun deserializeFromJson(json: String): T? =
//            App.gson.fromJson(json)
//    }

