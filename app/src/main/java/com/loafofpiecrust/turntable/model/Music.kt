package com.loafofpiecrust.turntable.model

import android.os.Parcelable

interface Music {
    val id: MusicId
}

/**
 * Can be sent to another user as a recommendation.
 */
interface Recommendation: Parcelable

/**
 * Identifies a piece of [Music] content that can be displayed in the UI.
 * Any [MusicId] can be sent as a recommendation.
 */
interface MusicId: Recommendation {
    val name: String
    val displayName: String
}

/// Provides unique transition names for music content.
private fun MusicId.transitionFor(elem: String): String =
    elem + this.toString().toLowerCase()

val MusicId.nameTransition get() = transitionFor("name")
val MusicId.imageTransition get() = transitionFor("art")