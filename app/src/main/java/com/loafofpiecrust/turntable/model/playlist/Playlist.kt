package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import android.support.annotation.DrawableRes
import android.view.Menu
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repeat
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.User
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.firstOrNull
import kotlinx.coroutines.experimental.runBlocking
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine


@Parcelize
data class PlaylistId(
    override val name: String,
    val uuid: UUID
): MusicId {
    override val displayName get() = name
}

abstract class Playlist: SavableMusic, HasTracks {
    abstract val owner: User
    abstract var name: String
    abstract var color: Int?
    abstract val uuid: UUID

    // TODO: Integrate directly.
    override val musicId get() = PlaylistId(name, uuid)

    var createdTime: Date = Date()
        private set

    final override val tracks: List<Song>
        get() = runBlocking { tracksChannel.firstOrNull() } ?: emptyList()
    abstract val tracksChannel: ReceiveChannel<List<Song>>

    @get:DrawableRes
    abstract val icon: Int

    @ServerTimestamp
    open var lastModified: Date = Date()
    /**
     * Used to store the playlist "locally" in Google Drive
     * TODO: Get rid of this in favor of centralizing _all_ playlists to Firestore.
     */
    open var remoteFileId: String? = null
    var isPublished: Boolean = false
    var isCompletable: Boolean = false

    /// The first time, publishes this playlist to the database.
    /// After that, this pushes updates to the database entry.
    abstract fun publish()

    /// Removes this playlist from the database
    fun unpublish() {
        if (isPublished) {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(uuid.toString())
                .delete()
            isPublished = false
        }
    }

    /// @return true if there were changes in our version since last server revision.
    open fun diffAndMerge(newer: Playlist): Boolean {
        this.name = newer.name
        this.color = newer.color
        this.lastModified = newer.lastModified
        this.remoteFileId = newer.remoteFileId
        return false
    }

    open fun updateLastModified() {
        lastModified = Date()
        UserPrefs.playlists.repeat()
    }


    open fun sendTo(user: User) {
        publish()
        SyncService.send(Message.Playlist(uuid), user)
    }

    companion object {
        suspend fun allByUser(owner: User): List<Playlist> {
            val db = FirebaseFirestore.getInstance()
            return suspendCoroutine { cont ->
                db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            cont.resume(it.result.mapNotNull {
                                when (it.getString("format")) {
                                    "mixtape" -> MixTape.fromDocument(it)
                                    "playlist" -> CollaborativePlaylist.fromDocument(it)
                                    "albums" -> AlbumCollection.fromDocument(it)
                                    else -> throw IllegalStateException("Unrecognized playlist format")
                                }.apply {
                                    createdTime = it.getDate("createdTime") ?: createdTime
                                }
                            })
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }
        }
    }
}