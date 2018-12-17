package com.chibatching.kotpref.gsonpref


//inline fun <reified T : Any> gsonNullablePrefObj(default: T?, key: String?) =
//    object: AbstractPref<T?>() {
//        override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T? {
//            return preference.getString(key ?: property.uuid, null)?.let { json ->
//                deserializeFromJson(json) ?: default
//            }
//        }
//
//        override fun setToPreference(property: KProperty<*>, value: T?, preference: SharedPreferences) {
//            serializeToJson(value).let { json ->
//                preference.edit().putString(key ?: property.uuid, json).apply()
//            }
//        }
//
//        override fun setToEditor(property: KProperty<*>, value: T?, editor: SharedPreferences.Editor) {
//            serializeToJson(value).let { json ->
//                editor.putString(key ?: property.uuid, json)
//            }
//        }
//
//        private fun serializeToJson(value: T?): String? {
//            return App.gson.toJson(value)
//        }
//
//        private fun deserializeFromJson(json: String): T? {
//            return App.gson.fromJson(json)
////            return KotprefGsonHolder.mapper.let {
////                it.readValue(json)
////            }
//        }
//    }
