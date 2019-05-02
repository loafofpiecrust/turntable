package com.loafofpiecrust.turntable.model.artist

import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.song.withoutArticle
import com.loafofpiecrust.turntable.util.compareTo
import kotlinx.android.parcel.Parcelize

@Parcelize
//@Serializable
data class ArtistId(
    override val name: String,
    val altName: String? = null,
    var features: List<ArtistId> = emptyList()
) : MusicId, Comparable<ArtistId> {
    @Deprecated("Serializer use only")
    internal constructor(): this("")

    @kotlinx.serialization.Transient
    private val sortName: CharSequence
        get() = displayName.withoutArticle()

    @kotlinx.serialization.Transient
    val dbKey: String get() = sortName.toString()

    /** Character used for alphabetized scrollbars and section titles */
    @kotlinx.serialization.Transient
    val sortChar: Char get() = sortName.first().toUpperCase()

    @kotlinx.serialization.Transient
    val featureList: String get() = if (features.isNotEmpty()) {
        App.instance.getString(R.string.artist_features, features.joinToString(", "))
    } else ""

    @delegate:Transient
    @kotlinx.serialization.Transient
    override val displayName: String by lazy {
        val feat = SongId.FEATURE_PAT.find(name)
        if (feat != null) {
            val res = name.removeRange(feat.range).trim()
            features = feat.groups[2]!!.value.split(',', '&').mapNotNull {
                val s = it.trim()
                    .removeSuffix("&")
                    .removeSuffix(",")
                    .trimEnd()
                if (s.isNotEmpty()) {
                    ArtistId(s)
                } else null
            }
            res
        } else name.trim()
    }

    override fun toString(): String = displayName

    override fun equals(other: Any?): Boolean =
        (other as? ArtistId)?.let { other ->
            this.compareTo(other) == 0
        } ?: false

    override fun hashCode() =
        sortName.toString().toLowerCase().hashCode()

    override fun compareTo(other: ArtistId) =
        sortName.compareTo(other.sortName, true)
}