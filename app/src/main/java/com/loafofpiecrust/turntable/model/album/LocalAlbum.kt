package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.milliseconds
import com.loafofpiecrust.turntable.util.minutes

/**
 * Album composed of song files stored locally on the device.
 */
data class LocalAlbum(
    override val id: AlbumId,
    override val tracks: List<Song>
): Album {
    @Deprecated("Serializer use only")
    internal constructor(): this(AlbumId(), emptyList())

    override val year: Int
        get() = tracks.find { it.year > 0 }?.year ?: 0

    override val type by lazy {
        when {
            // We could have an EP of any length if it's marked as such.
            id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
            // Generally, A-side, B-side, extra
            tracks.size <= 3 -> Album.Type.SINGLE
            // Official iTunes/Spotify/international standard definition of EP
            // 4-6 tracks or under 30 min duration.
            tracks.size <= 6 || duration < 30.minutes -> Album.Type.EP
            // Very rough estimate of what's a compilation
            id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
            // Anything else should be an LP
            else -> Album.Type.LP
        }
    }

    private val duration get() = tracks.sumBy { it.duration }.milliseconds
}
