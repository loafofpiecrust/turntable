package com.loafofpiecrust.turntable.sync

import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicPlayer
import java.util.*
import java.util.concurrent.TimeUnit

sealed class Message {
    internal open fun minimize(): Message = this
    /// seconds
    internal open val timeout: Long get() = TimeUnit.MINUTES.toSeconds(3)

    class Ping: Message()

    // Sync setup
    class SyncRequest: Message() {
        override val timeout get() = TimeUnit.MINUTES.toSeconds(20)
    }
    data class SyncResponse(val accept: Boolean): Message()
    class EndSync: Message()

    // Friendship
    class FriendRequest: Message() {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
    }
    data class FriendResponse(val accept: Boolean): Message() {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
    }

    // Recommendations
    data class Recommendation(val content: SavableMusic): Message() {
        override val timeout get() = TimeUnit.DAYS.toSeconds(28)
    }
    data class Playlist(val id: UUID): Message()
}

sealed class PlayerAction: Message() {
    class Play: PlayerAction()
    class Pause: PlayerAction()
    class TogglePause: PlayerAction()
    class Stop: PlayerAction()
    data class QueuePosition(val pos: Int): PlayerAction()
    data class RelativePosition(val diff: Int): PlayerAction()
    data class Enqueue(
        val songs: List<Song>,
        val mode: MusicPlayer.EnqueueMode
    ): PlayerAction()
    data class ShiftQueueItem(
        val fromIdx: Int,
        val toIdx: Int
    ): PlayerAction()
    data class RemoveFromQueue(val pos: Int): PlayerAction()
    data class PlaySongs(
        val songs: List<Song>,
        val pos: Int = 0,
        val mode: MusicPlayer.OrderMode = MusicPlayer.OrderMode.SEQUENTIAL
    ): PlayerAction() {
//            override fun minimize() = copy(songs = songs.map { it.minimizeForSync() })
    }
    data class SeekTo(val pos: Long): PlayerAction()
    class ClearQueue: PlayerAction()
}