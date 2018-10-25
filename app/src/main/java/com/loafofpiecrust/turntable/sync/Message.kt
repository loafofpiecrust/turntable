package com.loafofpiecrust.turntable.sync

import android.os.Parcelable
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.model.Recommendation
import com.loafofpiecrust.turntable.model.playlist.AbstractPlaylist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import kotlinx.android.parcel.Parcelize
import java.util.*
import java.util.concurrent.TimeUnit

interface Message {
    fun minimize(): Message = this
    /// seconds
    val timeout: Long get() = TimeUnit.MINUTES.toSeconds(3)
    val requiresSession: Boolean get() = false

    suspend fun onReceive(sender: User)


    // Recommendations
    data class Recommend(
        val content: Recommendation
    ): Message {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
        override suspend fun onReceive(sender: User) {
            UserPrefs.recommendations appends content
        }
    }

    data class Playlist(val id: UUID): Message {
        override suspend fun onReceive(sender: User) {
            AbstractPlaylist.find(id)?.let {
                UserPrefs.recommendations appends it.id
            }
        }
    }

//    class StatusRequest: Message {
//        override val timeout get() = 5L // seconds
//        override suspend fun onReceive(sender: User) {
//        }
//    }
//    data class Status(
//        val currentSong: Song
//    ): Message {
//        override val timeout get() = 5L // seconds
//    }
}

sealed class PlayerAction: Message, Parcelable {
    override val requiresSession get() = true

    override suspend fun onReceive(sender: User) {
        MusicService.offer(this, false)
    }

    /**
     * TODO: Remove dependency on MusicService to directly use MusicPlayer
     */
    abstract fun MusicService.enact(): Boolean

    /**
     * Multi-platform way:
     * expect class Play: PlayerAction()
     *
     * actual class Play: PlayerAction() {
     *     override fun MusicService.enact() = ...
     * }
     */
    @Parcelize
    class Play: PlayerAction() {
        override fun MusicService.enact(): Boolean =
            requestFocus() && player.play()
    }

    @Parcelize
    class Pause: PlayerAction() {
        override fun MusicService.enact() =
            player.pause() && abandonFocus()
    }

    @Parcelize
    class TogglePause: PlayerAction() {
        override fun MusicService.enact() =
            player.togglePause()
    }

    @Parcelize
    class Stop: PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.stop()
            return true
        }
    }

    @Parcelize
    data class QueuePosition(val pos: Int): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.shiftQueuePosition(pos)
            return true
        }
    }

    @Parcelize
    data class RelativePosition(val diff: Int): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.shiftQueuePositionRelative(diff)
            return true
        }
    }

    @Parcelize
    data class Enqueue(
        val songs: List<Song>,
        val mode: MusicPlayer.EnqueueMode
    ): PlayerAction() {
        override fun MusicService.enact() : Boolean {
            player.enqueue(songs, mode)
            return true
        }
    }

    @Parcelize
    data class ShiftQueueItem(
        val fromIdx: Int,
        val toIdx: Int
    ): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.shiftQueueItem(fromIdx, toIdx)
            return true
        }
    }

    @Parcelize
    data class RemoveFromQueue(val pos: Int): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.removeFromQueue(pos)
            return true
        }
    }

    @Parcelize
    data class PlaySongs(
        val songs: List<Song>,
        val pos: Int = 0,
        val mode: MusicPlayer.OrderMode = MusicPlayer.OrderMode.SEQUENTIAL
    ): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.playSongs(songs, pos, mode)
            return true
        }
    }

    @Parcelize
    data class SeekTo(val pos: Long): PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.seekTo(pos)
            return true
        }
    }

    @Parcelize
    class ClearQueue: PlayerAction() {
        override fun MusicService.enact(): Boolean {
            player.clearQueue()
            return true
        }
    }
}