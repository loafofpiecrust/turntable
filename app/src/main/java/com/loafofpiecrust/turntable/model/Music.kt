package com.loafofpiecrust.turntable.model

import android.os.Parcelable

interface Music {
    val id: MusicId
}

interface Recommendation: Parcelable

interface MusicId: Parcelable, Recommendation {
    val name: String
    val displayName: String
}

/// Provides unique transition names
/// for all instances of any type of MusicId.
fun MusicId.transitionFor(elem: Any): String =
    elem.toString() + this.toString().toLowerCase()

val MusicId.nameTransition get() = transitionFor("name")
val MusicId.imageTransition get() = transitionFor("art")