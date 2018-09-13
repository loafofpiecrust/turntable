package com.loafofpiecrust.turntable.model.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.MusicId
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.song.withoutArticle
import com.loafofpiecrust.turntable.util.compareByIgnoreCase
import com.loafofpiecrust.turntable.util.compareTo
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.util.*


@Parcelize
data class ArtistId(
    override val name: String,
    val altName: String? = null,
    var features: List<ArtistId> = listOf()
): MusicId, Parcelable, Comparable<ArtistId> {
    private constructor(): this("")

    val dbKey: String get() = sortName.toString()

    @IgnoredOnParcel
    @delegate:Transient
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
        } else name
    }

    /// Character used for alphabetized scrollbars and section titles
    val sortChar: Char get() = sortName.first().toUpperCase()

    private val sortName: CharSequence get() = displayName.withoutArticle()
    val featureList: String get() = if (features.isNotEmpty()) {
        // TODO: Localize the comma-based join (is this possible/feasible?)
        App.instance.getString(R.string.artist_features, features.joinToString(", "))
    } else ""


    override fun toString() = displayName
    override fun equals(other: Any?) = (other as? ArtistId)?.let { other ->
        this.sortName.compareTo(other.sortName, true) == 0
    } ?: false
    override fun hashCode() = Objects.hash(
        sortName.toString().toLowerCase()//,
//        altName?.toLowerCase(),
//        features
    )
    override fun compareTo(other: ArtistId) = COMPARATOR.compare(this, other)

    companion object {
        val COMPARATOR = compareByIgnoreCase<ArtistId>({ it.sortName })
    }
}