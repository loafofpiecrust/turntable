package com.loafofpiecrust.turntable.util

import android.content.Context
import android.support.annotation.StringRes
import kotlin.reflect.KClass

@StringRes
fun Class<*>.nameResource(context: Context): Int {
    val name = canonicalName
    requireNotNull(name) { "Cannot get resource name for local or anonymous class $this" }
    val localName = name!!.splitToSequence('.', '$').dropWhile { it[0].isLowerCase() }.joinToString("_")
    val pkg = context.packageName
    return context.resources.getIdentifier(localName, "string", pkg)
}

fun Class<*>.localizedName(context: Context): String {
    return context.getString(nameResource(context))
}

inline fun KClass<*>.localizedName(context: Context) = java.localizedName(context)

inline fun <reified T> Context.className() = T::class.localizedName(this)