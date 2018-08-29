package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.MusicId
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.song.withoutArticle
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

    @IgnoredOnParcel
    @delegate:Transient
    override val displayName: String by lazy {
        val feat = SongId.FEATURE_PAT.matcher(name)
        if (feat.find()) {
            val res = feat.replaceFirst("").trim()
            features = feat.group(2).split(',', '&').mapNotNull {
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
    val sortName: String get() = displayName.withoutArticle()
    val featureList: String get() = if (features.isNotEmpty()) {
        " (ft. " + features.joinToString(", ") + ")"
    } else ""


    fun forAlbum(album: String) = AlbumId(album, this)

    override fun toString() = displayName
    override fun equals(other: Any?) = (other as? ArtistId)?.let { other ->
        this.sortName.equals(other.sortName, true)
    } ?: false
    override fun hashCode() = Objects.hash(
        sortName.toLowerCase()//,
//        altName?.toLowerCase(),
//        features
    )
    override fun compareTo(other: ArtistId): Int =
        Library.ARTIST_COMPARATOR.compare(this, other)
}