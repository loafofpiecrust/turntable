package com.loafofpiecrust.turntable.model

import android.os.Parcelable
import java.io.Serializable

interface Music {
    val musicId: MusicId
}

interface SavableMusic: Music, Serializable
interface MusicId: Parcelable {
    val name: String
    val displayName: String
}

/// Provides unique transition names
/// for all instances of any type of MusicId.
fun MusicId.transitionFor(elem: Any): String =
    elem.toString() + this.toString().toLowerCase()

val MusicId.nameTransition get() = transitionFor("name")
val MusicId.imageTransition get() = transitionFor("art")