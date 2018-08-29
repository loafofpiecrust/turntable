package com.loafofpiecrust.turntable.album

import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.MusicId
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.song.withoutArticle
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.util.*


@Parcelize
data class AlbumId(
    override val name: String,
    val artist: ArtistId
): MusicId, Comparable<AlbumId> {
    val sortName: String get() = displayName.withoutArticle()
    val dbKey: String get() = "$displayName~${artist.sortName}".toLowerCase()

    val discNumber: Int get() =
        DISC_SUFFIX_PAT.find(name)?.let { m ->
            m.groups[2]?.value?.toIntOrNull()
        } ?: 1

    /// Cut out versions and types at the end for a CLEAN id
    /// Examples:
    /// Whoa - Single => Whoa
    /// Whoa - Fine & Single => Whoa - Fine & Single
    /// I'm Still Single => I'm Still Single
    /// What's Going On (Deluxe Edition) => What's Going On
    /// Whatever (Maxi Edition) - EP => Whatever
    /// What We... (Deluxe Version) => What We...
    /// It's a Deluxe Edition => It's a Deluxe Edition
    @IgnoredOnParcel
    @Transient
    override val displayName: String = run {
        val toRemove = arrayOf(
            // First, remove " - $type" suffix
            TYPE_SUFFIX_PAT,
            // Then, remove "(Deluxe Edition)" suffix
            EDITION_SUFFIX_PAT,
            // Finally, remove "(Disc 1)" suffix (and set the disc # for all tracks?)
            DISC_SUFFIX_PAT
        )

        var name = this.name
        for (regex in toRemove) {
            regex.find(name)?.let { m ->
                name = name.removeRange(m.range)
            }
        }

        if (name.isNotEmpty()) {
            name
        } else {
            this.artist.name
        }.replace("“", "\"")
            .replace("”", "\"")
            .replace("‘", "\'")
            .replace("’", "\'")
    }


    override fun toString() = "$displayName | $artist"
    override fun equals(other: Any?) = (other as? AlbumId)?.let { other ->
        this.displayName.equals(other.displayName, true)
            && this.artist == other.artist
    } ?: false
    override fun hashCode() = Objects.hash(displayName.toLowerCase(), artist)
    override fun compareTo(other: AlbumId): Int
        = Library.ALBUM_COMPARATOR.compare(this, other)


    companion object {
        private val TYPE_SUFFIX_PAT = Regex(
            "\\b\\s*[-]?\\s*[(\\[]?(EP|Single|LP)[)\\]]?$",
            RegexOption.IGNORE_CASE
        )
        private val EDITION_SUFFIX_PAT = Regex(
            "\\s+([(\\[][\\w\\s]*(Edition|Version|Deluxe|Release|Reissue|Mono|Stereo|Extended)[\\w\\s]*[)\\]])|(\\w+\\s+(Edition|Version|Release)$)",
            RegexOption.IGNORE_CASE
        )
        val SIMPLE_EDITION_PAT = Regex(
            "\\b(Deluxe|Expansion)\\b",
            RegexOption.IGNORE_CASE
        )
        private val DISC_SUFFIX_PAT = Regex(
            "\\s*[(\\[]?\\s*(Disc|Disk|CD)\\s*(\\d+)\\s*[)\\]]?$",
            RegexOption.IGNORE_CASE
        )
    }
}


val AlbumId.selfTitledAlbum: Boolean get() = sortName.equals(artist.sortName, true)