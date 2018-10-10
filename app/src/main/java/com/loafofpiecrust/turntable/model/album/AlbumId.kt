package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.song.withoutArticle
import com.loafofpiecrust.turntable.util.compareTo
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.text.Collator
import java.util.*


@Parcelize
data class AlbumId(
    override val name: String,
    val artist: ArtistId
): MusicId, Comparable<AlbumId> {
    private constructor(): this("", ArtistId(""))

    val sortChar: Char get() = sortName.first().toUpperCase()
    private val sortName: CharSequence get() = displayName.withoutArticle()
    val dbKey: String get() = "$displayName~${artist.dbKey}".toLowerCase()

    val discNumber: Int get() =
        DISC_SUFFIX_PAT.find(name)?.let { m ->
            m.groups[2]?.value?.toIntOrNull()
        } ?: 1

    /// Cut out versions and types at the end for a CLEAN uuid
    /// Examples:
    /// Whoa - Single => Whoa
    /// Whoa - Fine & Single => Whoa - Fine & Single
    /// I'm Still Single => I'm Still Single
    /// What's Going On (Deluxe Edition) => What's Going On
    /// Whatever (Maxi Edition) - EP => Whatever
    /// What We... (Deluxe Version) => What We...
    /// It's a Deluxe Edition => It's a Deluxe Edition
    @IgnoredOnParcel
    @delegate:Transient
    override val displayName: String by lazy {
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
            // TODO: Move quote simplification to remote API implementations.
        }.simplifyQuotes()
    }


    override fun toString() = "$displayName | $artist"
    override fun equals(other: Any?) = (other as? AlbumId)?.let { other ->
        this.displayName.equals(other.displayName, true)
            && this.artist == other.artist
    } ?: false
    override fun hashCode() = Objects.hash(displayName.toLowerCase(), artist)
    override fun compareTo(other: AlbumId) =
        COMPARATOR.compare(this, other)


    companion object {
        val COLLATOR: Collator = Collator.getInstance().apply {
            strength = Collator.PRIMARY
        }
        val COMPARATOR = compareBy<AlbumId, String>(COLLATOR) {
            it.displayName
        }.thenBy { it.artist }

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


val AlbumId.selfTitledAlbum: Boolean
    inline get() = displayName.compareTo(artist.displayName, true) == 0

fun String.simplifyQuotes() = this
    .replace(Regex("[“”]"), "\"")
    .replace(Regex("[‘’]"), "\'")