package com.loafofpiecrust.turntable.util

import android.content.Context
import android.support.annotation.StringRes
import kotlin.reflect.KClass

val Class<*>.scopedName: String get() {
    val name = canonicalName
    requireNotNull(name) { "Cannot get resource name for local or anonymous class $this" }
    return name.splitToSequence('.', '$')
        .dropWhile { it[0].isLowerCase() }
        .joinToString(".")
}

@StringRes
private fun Class<*>.nameResource(context: Context): Int {
    val localName = scopedName.replace('.', '_')
    val pkg = context.packageName
    return context.resources.getIdentifier(localName, "string", pkg)
}

fun Class<*>.localizedName(context: Context): String {
    return try {
        context.getString(nameResource(context))
    } catch (e: Exception) {
        scopedName
    }
}

fun KClass<*>.localizedName(context: Context) = java.localizedName(context)

fun Enum<*>.localizedName(context: Context): String {
    return try {
        context.getString(nameResource(context))
    } catch (e: Exception) {
        name
    }
}

fun Enum<*>.pluralResource(context: Context): Int =
    nameResource(context, "plurals")

fun Enum<*>.nameResource(context: Context, type: String = "string"): Int {
    val className = javaClass.scopedName.replace('.', '_')
    val stringId = "${className}_$name"
    return context.resources.getIdentifier(stringId, type, context.packageName)
}



fun String.toFileName(): String {
    val builder = StringBuilder(this.length)
    forEach { c ->
        val valid = c !in "|\\/?*<\":"
        if (valid) {
            builder.append(c)
        } else {
            builder.append(' ')
        }
    }
    return builder.toString()
}
