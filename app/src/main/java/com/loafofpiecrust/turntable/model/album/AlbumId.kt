package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.util.compareTo
import kotlinx.android.parcel.Parcelize
import java.text.Collator
import java.util.*

/**
 * Unique identifier for an [Album]
 * Used to determine duplicate and mergeable albums in a discography.
 */
@Parcelize
//@Serializable
data class AlbumId(
    /**
    * Original name
    */
    override val name: String,
    val artist: ArtistId
): MusicId, Comparable<AlbumId> {
    @Deprecated("Serializer use only")
    internal constructor(): this("", ArtistId())

    @kotlinx.serialization.Transient
    val sortChar: Char get() = displayName.first().toUpperCase()

    @kotlinx.serialization.Transient
    val dbKey: String get() = "$displayName~${artist.dbKey}".toLowerCase()

    @Deprecated("Should be elsewhere")
    @kotlinx.serialization.Transient
    val discNumber: Int get() =
        DISC_SUFFIX.find(name)?.let { m ->
            m.groups[2]?.value?.toIntOrNull()
        } ?: 1

    /**
     * Cut out versions and types at the end for a CLEAN unique display name.
     *
     * Examples:
     * Whoa - Single => Whoa
     * Whoa - Fine & Single => Whoa - Fine & Single
     * I'm Still Single => I'm Still Single
     * What's Going On (Deluxe Edition) => What's Going On
     * Whatever (Maxi Edition) - EP => Whatever
     * What We... (Deluxe Version) => What We...
     * It's a Deluxe Edition => It's a Deluxe Edition
     */
    @delegate:Transient
    @kotlinx.serialization.Transient
    override val displayName: String by lazy {
        val toRemove = arrayOf(
            // First, remove " - $type" suffix
            TYPE_SUFFIX,
            // Then, remove "(Deluxe Edition)" suffix
            EDITION_SUFFIX,
            // Finally, remove "(Disc 1)" suffix (and set the disc # for all tracks?)
            DISC_SUFFIX
        )

        var name = this.name
        for (regex in toRemove) {
            regex.find(name)?.let { m ->
                name = name.removeRange(m.range)
            }
        }

        // TODO: Move quote simplification to remote API implementations.
        if (name.isNotEmpty()) {
            name
        } else {
            this.artist.name
        }.simplifyQuotes()
    }

    override fun toString() = "$displayName | $artist"
    override fun equals(other: Any?) = (other as? AlbumId)?.let { other ->
        this.displayName.equals(other.displayName, true) &&
                this.artist == other.artist
    } ?: false
    override fun hashCode() = Objects.hash(displayName.toLowerCase(), artist)
    override fun compareTo(other: AlbumId) =
        COMPARATOR.compare(this, other)

    @delegate:Transient
    @kotlinx.serialization.Transient
    private val collationKey by lazy {
        COLLATOR.getCollationKey(displayName)
    }

    companion object {
        val COLLATOR: Collator = Collator.getInstance().apply {
            strength = Collator.PRIMARY
        }
        val COMPARATOR = compareBy<AlbumId> {
            it.collationKey
        }.thenBy { it.artist }

        private val TYPE_SUFFIX = Regex(
            "\\b\\s*[-]?\\s*[(\\[]?(EP|Single|LP)[)\\]]?$",
            RegexOption.IGNORE_CASE
        )
        val EDITION_SUFFIX = Regex(
            "\\s+([(\\[][\\w\\s]*(Edition|Version|Deluxe|Release|Reissue|Mono|Stereo|Extended)[\\w\\s]*[)\\]])|(\\w+\\s+(Edition|Version|Release)$)",
            RegexOption.IGNORE_CASE
        )
        internal val SIMPLE_EDITION = Regex(
            "\\b(Deluxe|Expansion)\\b",
            RegexOption.IGNORE_CASE
        )
        private val DISC_SUFFIX = Regex(
            "\\s*[(\\[]?\\s*(Disc|Disk|CD)\\s*(\\d+)\\s*[)\\]]?$",
            RegexOption.IGNORE_CASE
        )
    }
}

val AlbumId.selfTitledAlbum: Boolean
    inline get() = displayName.compareTo(artist.displayName, true) == 0

fun String.simplifyQuotes(): String = this
    .replace(Regex("[“”]"), "\"")
    .replace(Regex("[‘’]"), "\'")