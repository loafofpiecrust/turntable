package com.loafofpiecrust.turntable.model.playlist

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.replayOne
import com.loafofpiecrust.turntable.util.serialize
import com.loafofpiecrust.turntable.util.toObject
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.suspendCoroutine

/**
 * Optionally collaborative playlist that syncs to the user's Google Drive storage.
 * It can be shared with other users and edited by them as well.
 */
class CollaborativePlaylist(
    override val owner: SyncService.User,
    override var name: String,
    override var color: Int?,
    override val id: UUID
) : Playlist() {
    override val typeName: String
        get() = "Playlist"

    sealed class Operation(
        open val timestamp: Long
    ): Serializable {
        abstract val songId: SongId

        data class Add(
            val song: Song,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp) {
            override val songId: SongId get() = song.id
        }
        data class Remove(
            override val songId: SongId,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp)
        data class Move(
            override val songId: SongId,
            val to: Int,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp)
    }

    companion object: AnkoLogger {
        fun fromDocument(doc: DocumentSnapshot): CollaborativePlaylist = runBlocking {
            CollaborativePlaylist(
                doc.getBlob("owner")!!.toObject(),
                doc.getString("name")!!,
                doc.getLong("color")?.toInt(),
                UUID.fromString(doc.id)
            ).apply {
                operations puts doc.getBlob("operations")!!.toObject()
                lastModified = doc.getDate("lastModified")!!
                isPublished = true
            }
        }

        suspend fun search(title: String): List<CollaborativePlaylist> {
            val db = FirebaseFirestore.getInstance()
            return suspendCoroutine { cont ->
                db.collection("playlists").whereEqualTo("name", title).get().addOnCompleteListener {
                    val docs = it.result?.documents ?: listOf()
                    cont.resume(docs.map { fromDocument(it) })
                }
            }
        }

        suspend fun mostRecent(
            since: Date = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        ): List<CollaborativePlaylist> {
            val db = FirebaseFirestore.getInstance()
            return suspendCoroutine { cont ->
                db.collection("playlists").whereGreaterThanOrEqualTo("lastModified", since).get().addOnCompleteListener {
                    val docs = it.result?.documents ?: listOf()
                    cont.resume(docs.map { fromDocument(it) })
                }
            }
        }

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
                            else -> fromDocument(doc)
                        })
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }

//        fun fromSpotifyPlaylist(userId: String, id: String): CollaborativePlaylist {
//            val api = SpotifyApi()
//            val spotify = api.service
//            val playlist = spotify.getPlaylist(userId, id, mapOf(
//                "fields" to "tracks.items(added_at, track(name, track_number, disc_number, duration_ms, album(name), artists(name)))"
//            ))
//            val tracks = playlist.tracks.items
//            return CollaborativePlaylist(
//                playlist.owner.display_name,
//                playlist.name,
//                0
//            ).apply {
//                operations puts tracks.map {
//                    val track = it.track
//                    Operation.Add(Song(
//                        null, null,
//                        SongId(track.name, track.album.name, track.artists.first().name),
//                        track.track_number, track.disc_number, track.duration_ms.toInt()
//                    ))
//                }
//            }
//        }
    }

    val operations = ConflatedBroadcastChannel(listOf<Operation>())

    @Transient
    private val _tracks: ConflatedBroadcastChannel<List<Song>> = this.operations.openSubscription().map { ops ->
        val songs = mutableListOf<Song>()
        ops.forEach { op ->
            val idx = songs.indexOfFirst { it.id == op.songId }
            when (op) {
                is Operation.Add -> if (idx == -1) songs.add(op.song)
                is Operation.Remove -> if (idx != -1) songs.removeAt(idx)
                is Operation.Move -> if (idx != -1) songs.shift(idx, op.to)
            }
        }
        songs
    }.replayOne()

    override val tracks: ReceiveChannel<List<Song>>
        get() = _tracks.openSubscription()

    constructor(): this(SyncService.User(), "", null, UUID.randomUUID())

    override fun updateLastModified() {
        super.updateLastModified()
        if (isPublished) publish()
    }

    fun rename(newName: String) {
        name = newName
        updateLastModified()
    }

    fun add(song: Song): Boolean {
        // Check if we already have this song, and if so ask for confirmation
        val isNew = operations.value.find { it is Operation.Add && it.song.id == song.id } == null
        if (isNew) {
            operations appends Operation.Add(song)
            updateLastModified()
        }
        return isNew
    }

    fun remove(idx: Int) {
        val song = _tracks.value.getOrNull(idx)
        if (song != null) {
            operations appends Operation.Remove(song.id)
            updateLastModified()
        }
    }

    fun move(from: Int, to: Int) {
        val song = _tracks.value.getOrNull(from)
        if (song != null) {
            val op = Operation.Move(song.id, to)
            val lastOp = operations.value.last()
            if (lastOp is Operation.Move && lastOp.songId == song.id) {
                operations putsMapped { ops ->
                    ops.withReplaced(ops.lastIndex, op)
                }
            } else {
                operations appends op
            }
            updateLastModified()
        }
    }

    /// @return true if there were changes in our version since last server revision. (eg. Republish or not)
    override fun diffAndMerge(other: Playlist): Boolean {
        if (other is CollaborativePlaylist) {
            val ours = operations.value
            val theirs = other.operations.value
            val combined = (ours + theirs).sortedBy { it.timestamp }.dedupMergeSorted(
                // Removes shared history
                { a, b -> a == b },
                { a, b -> a }
            )
            // Totally remove anything that's been added and removed in the history!
            val toRemove = combined.filter { it is Operation.Remove }


            operations puts combined

            name = other.name
            color = other.color
            lastModified = maxOf(lastModified, other.lastModified)
            return combined != theirs
        }
        return false
    }

    override fun publish() {
        launch(BG_POOL) {
            val db = FirebaseFirestore.getInstance()
//        val batch = db.batch()
            val doc = db.collection("playlists").document(id.toString())
            doc.set(mapOf(
//                "type" to type.toString(),
                "format" to "playlist",
                "name" to name,
                "color" to color,
                "lastModified" to lastModified,
                "createdTime" to createdTime,
                "operations" to Blob.fromBytes(serialize(operations.value)),
                "owner" to Blob.fromBytes(serialize(owner))
            ))
        }
//        val ops = doc.collection("operations")
//        operations.value.forEach { batch.set(ops.document(), it) }
//        batch.commit()
        if (!isPublished) {
            updateLastModified()
            isPublished = true
        }
    }

    private var syncListener: ListenerRegistration? = null

    fun desync() {
        syncListener?.remove()
        syncListener = null
    }

    fun sync() = if (isPublished && syncListener == null) {
        val db = FirebaseFirestore.getInstance()
        syncListener?.remove()
        syncListener = db.collection("playlists").document(id.toString()).addSnapshotListener { doc, err ->
            if (err != null) {
                error { err.stackTrace }
            }
            if (doc != null && doc.exists()) {
                val localChange = doc.metadata.hasPendingWrites()
                if (!localChange) {
                    if (diffAndMerge(fromDocument(doc))) {
                        publish()
                    }
                }
            } else {
                isPublished = false
            }
        }
    } else null

}