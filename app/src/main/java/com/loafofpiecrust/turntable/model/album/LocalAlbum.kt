package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.milliseconds
import com.loafofpiecrust.turntable.util.minutes

/**
 * Album composed of song files stored locally on the device.
 */
data class LocalAlbum(
    override val id: AlbumId,
    private val tracks: List<Song>
): Album {
    @Deprecated("Serializer use only")
    internal constructor(): this(AlbumId(), emptyList())

    override suspend fun resolveTracks() = tracks

    override val year: Int
        get() = tracks.find { it.year > 0 }?.year ?: 0

    @Transient
    override val type: Album.Type = when {
        // We could have an EP of any length if it's marked as such.
        id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
        // Generally, A-side, B-side, extra
        tracks.size <= MAX_SINGLE_TRACKS -> Album.Type.SINGLE
        // Official iTunes/Spotify/international standard definition of EP
        // 4-6 tracks or under 30 min duration.
        tracks.size <= MAX_EP_TRACKS || duration < MAX_EP_DURATION -> Album.Type.EP
        // Very rough estimate of what's a compilation
        id.name.contains(COMPILATION_PAT) -> Album.Type.COMPILATION
        // Anything else should be an LP
        else -> Album.Type.LP
    }

    private val duration get() =
        tracks.sumBy { it.duration }.milliseconds

    companion object {
        private const val MAX_SINGLE_TRACKS = 3
        private const val MAX_EP_TRACKS = 6
        private val MAX_EP_DURATION = 30.minutes
        private val COMPILATION_PAT = Regex(
            "\\b(Collection|Compilation|Best of|Greatest hits)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
