package com.loafofpiecrust.turntable.model.playlist

import android.support.annotation.DrawableRes
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ServerTimestamp
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repeat
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.sync.Sync
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.warn
import java.util.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@Parcelize
data class PlaylistId(
    override val name: String,
    val owner: User = Sync.selfUser,
    val uuid: UUID = UUID.randomUUID()
): MusicId, Recommendable {
    private constructor(): this("")

    override val displayName get() = name

    override fun hashCode(): Int = Objects.hash(owner, uuid)
    override fun equals(other: Any?) =
        other is PlaylistId && owner == other.owner && uuid == other.uuid
}

interface Playlist: Music, HasTracks {
    override val id: PlaylistId

    var color: Int?

    @get:DrawableRes
    val icon: Int
}

interface MutablePlaylist: Playlist {
    var isPublished: Boolean

    /// The first time, publishes this playlist to the database.
    /// After that, this pushes updates to the database entry.
    fun publish()

    /// Removes this playlist from the database
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

abstract class AbstractPlaylist: Playlist {

    var createdTime: Date = Date()
        private set

    final override val tracks: List<Song>
        get() = runBlocking { tracksChannel.firstOrNull() } ?: emptyList()
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


    /// @return true if there were changes in our version since last server revision.
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

    companion object: AnkoLogger by AnkoLogger<Playlist>() {
        suspend fun find(id: UUID): Playlist? {
            val db = FirebaseFirestore.getInstance()
            return suspendCoroutine { cont ->
                db.collection("playlists").document(id.toString()).get().addOnCompleteListener { task ->
                    val doc = task.result
                    val err = task.exception
                    if (err != null) {
                        error { err.stackTrace }
                    }
                    if (task.isSuccessful && doc.exists()) {
                        val format = doc.getString("format")!!
                        cont.resume(when (format) {
                            "mixtape" -> MixTape.fromDocument(doc)
                            else -> CollaborativePlaylist.fromDocument(doc)
                        })
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }

        fun findChannel(id: UUID): ReceiveChannel<Playlist?> {
            val db = FirebaseFirestore.getInstance()
            var listener: ListenerRegistration? = null
            return GlobalScope.produce(onCompletion = { listener?.remove() }) {
                val first = find(id)
                send(first)
                if (first != null) suspendCancellableCoroutine<Unit> { cont ->
                    listener = db.playlists().document(id.toString()).addSnapshotListener { snapshot, err ->
                        if (err != null) {
                            cont.resumeWithException(err)
                        }
                        if (snapshot?.exists() == true) {
                            val localChange = snapshot.metadata.hasPendingWrites()
                            if (!localChange) {
                                val format = snapshot.getString("format")!!
                                offer(when (format) {
                                    "mixtape" -> MixTape.fromDocument(snapshot)
                                    else -> CollaborativePlaylist.fromDocument(snapshot)
                                })
                            }
                        }
                    }
                }
            }
        }

        suspend fun allByUser(owner: User): List<Playlist> {
            val db = FirebaseFirestore.getInstance()
            return suspendCoroutine { cont ->
                db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            cont.resume(it.result.mapNotNull {
                                val format = it.getString("format")
                                when (format) {
                                    "mixtape" -> MixTape.fromDocument(it)
                                    "playlist" -> CollaborativePlaylist.fromDocument(it)
                                    "albums" -> AlbumCollection.fromDocument(it)
                                    else -> {
                                        warn { "Unrecognized playlist format $format" }
                                        null
                                    }
                                }.apply {
//                                    createdTime = it.getDate("createdTime") ?: createdTime
                                }
                            })
                        } else {
                            error("Playlist query failed", it.exception)
                            cont.resume(listOf())
                        }
                    }
            }
        }
    }
}

fun FirebaseFirestore.playlists() = collection("playlists")