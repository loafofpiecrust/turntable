package com.chibatching.kotpref.objpref


//class objPref<T: Any>(val default: T): AbstractPref<T>() {
//    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
//        if (!subject.hasValue) {
//            GlobalScope.launch {
//                getFromPreference(property, UserPrefs.kotprefPreference).also {
//                    subject.offer(it)
//                }
//            }
//        }
//        return subject
//    }
//
//    override fun getFromPreference(property: KProperty<*>, preference: SharedPreferences): T {
//        return preference.getString(property.name, null)?.let {
//            try {
//                App.gson.fromJson<Any>(it, property.returnType.javaType) as T
//            } catch(e: Throwable) {
//                error("Property ${property.name} failed to load, using default value.", e)
//                default
//            }
//        } ?: default
//    }
//
//    override fun setToPreference(property: KProperty<*>, value: T, preference: SharedPreferences) {
//        try {
//            val bytes = App.gson.toJson(value, property.returnType.javaType)
//            preference.edit()
////                .putString(property.uuid, bytes.toString(Charsets.ISO_8859_1))
//                .putString(property.name, bytes)
//                .apply()
//        } catch (e: Throwable) {
//            error(null, e)
//        }
//    }
//
//    override fun setToEditor(property: KProperty<*>, value: T, editor: SharedPreferences.Editor) {
//        try {
//            val bytes = App.gson.toJson(value, property.returnType.javaType)
//            editor.putString(property.name, bytes)
//        } catch (e: Throwable) {
//            error(null, e)
//        }
//    }
//}