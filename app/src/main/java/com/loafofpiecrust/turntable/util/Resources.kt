package com.loafofpiecrust.turntable.util

import android.content.Context
import android.support.annotation.StringRes
import kotlin.reflect.KClass

private val Class<*>.scopedNameParts: Sequence<String> get() =
    name.splitToSequence('.', '$')
        .dropWhile { it[0].isLowerCase() }

private val KClass<*>.scopedNameParts: Sequence<String>? get() =
    qualifiedName?.splitToSequence('.', '$')
        ?.dropWhile { it[0].isLowerCase() }

val Class<*>.scopedName: String get() {
    requireNotNull(name) { "Cannot get resource name for local or anonymous class $this" }
    return scopedNameParts.joinToString(".")
}

val KClass<*>.scopedName: String get() {
    requireNotNull(qualifiedName) { "Cannot get resource name for local or anonymous class $this" }
    return scopedNameParts!!.joinToString(".")
}

@StringRes
private fun Class<*>.nameResource(context: Context): Int {
    val localName = scopedNameParts.joinToString("_")
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
    // skip an initial dot to prevent hidden files
    val iter = if (first() == '.') drop(1) else this
    for (c in iter) {
        val valid = c !in "|\\/?*<>\",:;@%="
        if (valid) {
            builder.append(c)
        } else if (builder.last() != ' ') {
            // condense multiple replacement spaces
            builder.append(' ')
        }
    }
    return builder.toString()
}
