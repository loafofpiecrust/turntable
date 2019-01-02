package com.loafofpiecrust.turntable.model.playlist

import android.support.annotation.DrawableRes
import com.github.ajalt.timberkt.Timber
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repeat
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.util.startWith
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.channels.mapNotNull
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.resumeWithException

@Parcelize
data class PlaylistId(
    override val name: String,
    val owner: User? = null,
    val uuid: UUID = UUID.randomUUID()
): MusicId, Recommendable {
    private constructor(): this("")

    override val displayName get() = name

    override fun hashCode(): Int = Objects.hash(owner, uuid)
    override fun equals(other: Any?) =
        other is PlaylistId && owner == other.owner && uuid == other.uuid
}

interface Playlist : Music, HasTracks {
    override val id: PlaylistId

    var color: Int?

    @get:DrawableRes
    val icon: Int
}

interface MutablePlaylist: Playlist {
    var isPublished: Boolean

    /**
     * The first time, publishes this playlist to the database.
     * After that, this pushes updates to the database entry.
     */
    fun publish()

    /**
     * Removes this playlist from the database
     */
    fun unpublish() {
        if (isPublished) {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(id.uuid.toString())
                .delete()
            isPublished = false
        }
    }
}

abstract class AbstractPlaylist : Playlist {
    var createdTime: Date = Date()
        private set

    val tracks: List<Song>
        get() = runBlocking { tracksChannel.firstOrNull() } ?: emptyList()

    final override suspend fun resolveTracks(): List<Song> {
        return tracksChannel.firstOrNull() ?: listOf()
    }

    abstract val tracksChannel: ReceiveChannel<List<Song>>

    @ServerTimestamp
    open var lastModified: Date = Date()

    /**
     * Used to store the playlist "locally" in Google Drive
     * TODO: Get rid of this in favor of centralizing _all_ playlists to Firestore.
     */
    open var remoteFileId: String? = null

    var isPublished: Boolean = false

    var isCompletable: Boolean = false

    /**
     * @return true if there were changes in our version since last server revision.
     */
    open fun diffAndMerge(newer: AbstractPlaylist): Boolean {
//        this.id = newer.id
        this.color = newer.color
        this.lastModified = newer.lastModified
        this.remoteFileId = newer.remoteFileId
        return false
    }

    open fun updateLastModified() {
        lastModified = Date()
        UserPrefs.playlists.repeat()
    }

    companion object {
        suspend fun find(id: UUID): Playlist? {
            val db = FirebaseFirestore.getInstance()
            val doc = db.playlists().document(id.toString()).get().await()
            return if (doc.exists()) {
                val format = doc.getString("format")!!
                when (format) {
                    "mixtape" -> MixTape.fromDocument(doc)
                    else -> CollaborativePlaylist.fromDocument(doc)
                }
            } else null
        }

        suspend fun findChannel(id: UUID): ReceiveChannel<Playlist?> {
            val db = FirebaseFirestore.getInstance()
            val updates = db.playlists().document(id.toString()).snapshots()
            return updates.mapNotNull { snapshot ->
                val localChange = snapshot.metadata.hasPendingWrites()
                if (!localChange) {
                    val format = snapshot.getString("format")!!
                    when (format) {
                        "mixtape" -> MixTape.fromDocument(snapshot)
                        else -> CollaborativePlaylist.fromDocument(snapshot)
                    }
                } else null
            }
        }

        suspend fun allByUser(owner: User): List<Playlist> {
            val db = FirebaseFirestore.getInstance()

            return try {
                val result = db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .get().await()

                result.mapNotNull { doc ->
                    val format = doc.getString("format")
                    when (format) {
                        "mixtape" -> MixTape.fromDocument(doc)
                        "playlist" -> CollaborativePlaylist.fromDocument(doc)
                        "albums" -> AlbumCollection.fromDocument(doc)
                        else -> {
                            Timber.w { "Unrecognized playlist format $format" }
                            null
                        }
                    }.apply {
//                        createdTime = it.getDate("createdTime") ?: createdTime
                    }
                }
            } catch (err: Exception) {
                Timber.e(err) { "Playlist query failed" }
                listOf()
            }
        }
    }
}

fun FirebaseFirestore.playlists() = collection("playlists")

suspend fun DocumentReference.snapshots(): ReceiveChannel<DocumentSnapshot> = coroutineScope {
    produce<DocumentSnapshot> {
        suspendCancellableCoroutine { cont ->
            val listener = addSnapshotListener { snapshot, err ->
                if (err != null) {
                    cont.resumeWithException(err)
                } else if (snapshot?.exists() == true) {
                    offer(snapshot)
                }
            }
            cont.invokeOnCancellation {
                listener.remove()
            }
        }
    }.startWith(get().await())
}