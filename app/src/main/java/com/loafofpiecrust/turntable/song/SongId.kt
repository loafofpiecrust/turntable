package com.loafofpiecrust.turntable.song

import android.os.Parcelable
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.service.Library
import kotlinx.android.parcel.Parcelize
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.*
import java.util.regex.Pattern


@Parcelize
data class SongId(
    override val name: String,
    val album: AlbumId,
    val artist: ArtistId = album.artist,
    var features: List<ArtistId> = listOf()
): MusicId, Parcelable, Comparable<SongId> {
    private constructor(): this("", "", "")
    constructor(title: String, album: String, artist: String, songArtist: String = artist):
        this(title, AlbumId(album, ArtistId(artist)), ArtistId(songArtist))

    companion object {
        val FEATURE_PAT by lazy {
            Pattern.compile("\\(?\\b(ft|feat|featuring|features)\\.?\\s+(([^,&)]+,?\\s*&?\\s*)*)\\)?$", Pattern.CASE_INSENSITIVE)!!
        }
    }

    override fun toString() = "$displayName | $album"
    override fun equals(other: Any?) = this === other || (other is SongId
        && this.displayName.equals(other.displayName, true)
        && this.album == other.album
        && this.artist == other.artist
    )

    fun fuzzyEquals(other: SongId)
        = FuzzySearch.ratio(name, other.name) >= 88
        && FuzzySearch.ratio(album.name, other.album.name) >= 88
        && FuzzySearch.ratio(album.artist.name, other.album.artist.name) >= 88

    override fun hashCode() = Objects.hash(
        displayName.toLowerCase(),
        album,
        artist
    )

    override fun compareTo(other: SongId): Int =
        Library.SONG_COMPARATOR.compare(this, other)

    val dbKey: String get() = "$name~${album.sortName}~$artist".toLowerCase()
    val filePath: String get() = "${album.artist.name.toFileName()}/${album.displayName.toFileName()}/${name.toFileName()}"


    @delegate:Transient
    override val displayName by lazy {
        // remove features!
        val m = FEATURE_PAT.matcher(name)
        if (m.find()) {
            val res = m.replaceFirst("").trim()
            features = m.group(2).split(",").mapNotNull {
                val s = it.trim().removeSuffix("&").removeSuffix(",").trimEnd()
                if (s.isNotEmpty()) {
                    ArtistId(s)
                } else null
            }
            res
        } else name
    }
}