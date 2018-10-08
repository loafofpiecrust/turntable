package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import android.support.annotation.DrawableRes
import android.view.Menu
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repeat
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.sync.User
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

abstract class Playlist: SavableMusic {
    abstract val owner: User
    abstract var name: String
    abstract var color: Int?
    abstract val id: UUID

    var createdTime: Date = Date()
        private set
    abstract val tracks: ReceiveChannel<List<Song>>

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

    override val displayName: String get() = name
    override fun optionsMenu(context: Context, menu: Menu) {

    }

    /// The first time, publishes this playlist to the database.
    /// After that, this pushes updates to the database entry.
    abstract fun publish()

    /// Removes this playlist from the database
    fun unpublish() {
        if (isPublished) {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(id.toString())
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
        SyncService.send(Message.Playlist(id), user)
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