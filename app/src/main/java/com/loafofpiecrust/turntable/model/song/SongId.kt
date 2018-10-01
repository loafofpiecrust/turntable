package com.loafofpiecrust.turntable.model.song

import android.os.Parcelable
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.util.compareByIgnoreCase
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.*


@Parcelize
data class SongId(
    override val name: String,
    val album: AlbumId,
    val artist: ArtistId = album.artist,
    var features: List<ArtistId> = emptyList()
): MusicId, Parcelable, Comparable<SongId> {
    private constructor(): this("", "", "")
    constructor(title: String, album: String, artist: String, songArtist: String = artist):
        this(title, AlbumId(album, ArtistId(artist)), ArtistId(songArtist))


    override fun toString() = "$displayName | $album"
    override fun equals(other: Any?) = this === other || (other is SongId
        && this.displayName.equals(other.displayName, true)
        && this.album == other.album
        && this.artist == other.artist
    )

    fun fuzzyEquals(other: SongId) =
        FuzzySearch.ratio(name, other.name) >= 88
        && FuzzySearch.ratio(album.name, other.album.name) >= 88
        && FuzzySearch.ratio(album.artist.name, other.album.artist.name) >= 88

    override fun hashCode() = Objects.hash(
        displayName.toLowerCase(),
        album,
        artist
    )

    override fun compareTo(other: SongId): Int = COMPARATOR.compare(this, other)

    val dbKey: String get() = "$displayName~${album.dbKey}".toLowerCase()
    val filePath: String get() = "${album.artist.name.toFileName()}/${album.displayName.toFileName()}/${name.toFileName()}"

    @IgnoredOnParcel
    override val displayName = run {
        // remove features!
        val m = FEATURE_PAT.find(name)
        if (m != null) {
            val res = name.removeRange(m.range).trim()
            features = m.groups[2]!!.value.split(',', '&').mapNotNull {
                val s = it.trim().removeSuffix("&").removeSuffix(",").trimEnd()
                if (s.isNotEmpty()) {
                    ArtistId(s)
                } else null
            }
            res
        } else name
    }

    companion object {
        val COMPARATOR = compareByIgnoreCase<SongId>(
            { it.name }
        ).thenBy { it.album }

        val FEATURE_PAT by lazy {
            Regex("\\(?\\b(ft|feat|featuring|features)\\.?\\s+(([^,&)]+,?\\s*&?\\s*)*)\\)?$", RegexOption.IGNORE_CASE)
        }
    }
}