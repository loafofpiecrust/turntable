package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import com.github.daemontus.Result
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.typedToJson
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.util.replayOne
import com.loafofpiecrust.turntable.util.withReplaced
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.toast
import java.io.Serializable
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Optionally collaborative playlist that syncs to the user's Google Drive storage.
 * It can be shared with other users and edited by them as well.
 */
class CollaborativePlaylist (
    override val id: PlaylistId,
    override var color: Int?
) : AbstractPlaylist(), MutablePlaylist {
    override val icon: Int
        get() = R.drawable.ic_boombox_color

    sealed class Operation(
        open val timestamp: Long
    ): Serializable {
        abstract val songId: SongId

        class Add(
            val song: Song,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp) {
            override val songId get() = song.id
        }

        class Remove(
            override val songId: SongId,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp)

        class Move(
            override val songId: SongId,
            val replacing: SongId,
            override val timestamp: Long = System.currentTimeMillis()
        ): Operation(timestamp)
    }

    val operations = ConflatedBroadcastChannel(listOf<Operation>())

    @delegate:Transient
    private val _tracks: ConflatedBroadcastChannel<List<Song>> by lazy {
        tracksChannel.replayOne()
    }

    override val tracksChannel: ReceiveChannel<List<Song>>
        get() = operations.openSubscription().map { ops ->
            val songs = mutableListOf<Song>()
            ops.forEach { op ->
                val idx = songs.indexOfFirst { it.id == op.songId }
                when (op) {
                    is Operation.Add -> if (idx == -1) songs.add(op.song)
                    is Operation.Remove -> if (idx != -1) songs.removeAt(idx)
                    is Operation.Move -> if (idx != -1) {
                        val dest = songs.indexOfFirst { it.id == op.replacing }
                        songs.shift(idx, dest)
                    }
                }
            }
            songs
        }

    constructor(): this(PlaylistId("", User()), null)

    override fun updateLastModified() {
        super.updateLastModified()
        if (isPublished) publish()
    }

    /// FIXME
    fun rename(newName: String) {
//        id.name = newName
        updateLastModified()
    }

    /// Returns true if any of the tracks in the given container are non-duplicates
    fun addAll(songs: Iterable<Song>): Boolean {
        val allNew = songs.map { add(it) }
        return allNew.any { it }
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

    fun move(from: SongId, to: SongId) {
        val op = Operation.Move(from, to)
        val lastOp = operations.value.last()
        if (lastOp is Operation.Move && lastOp.songId == from) {
            runBlocking {
                operations putsMapped { ops ->
                    ops.withReplaced(ops.lastIndex, op)
                }
            }
        } else {
            operations appends op
        }
        updateLastModified()
    }

    fun move(from: Int, to: Int) {
        val song = _tracks.value.getOrNull(from)
        val dest = _tracks.value.getOrNull(to)
        if (song != null && dest != null) {
            println("playlist: moving ${song.id} to where ${dest.id} was")
            move(song.id, dest.id)
        }
    }

    /// @return true if there were changes in our version since last server revision. (eg. Republish or not)
    override fun diffAndMerge(other: AbstractPlaylist): Boolean {
        (other as? CollaborativePlaylist)?.let { other ->
            val ours = operations.value
            val theirs = other.operations.value
            val combined = (ours + theirs).sortedBy { it.timestamp }.dedupMergeSorted(
                // Removes shared history
                { a, b -> a == b },
                { a, b -> a }
            )

            operations puts combined

            // FIXME
//            name = other.name
            color = other.color
            lastModified = maxOf(lastModified, other.lastModified)
            return combined != theirs
        }
        return false
    }

    // TODO: Pass in Fragment scope
    override fun publish() {
        GlobalScope.launch {
            val db = FirebaseFirestore.getInstance()
//        val batch = db.batch()
            val doc = db.collection("playlists").document(id.uuid.toString())
            doc.set(mapOf(
//                "type" to type.toString(),
                "format" to "playlist",
                "name" to id.name,
                "color" to color,
                "lastModified" to lastModified,
                "createdTime" to createdTime,
                "operations" to App.gson.typedToJson(operations.value),
                "owner" to id.owner?.let { App.gson.typedToJson(it) }
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
        syncListener = db.collection("playlists").document(id.uuid.toString()).addSnapshotListener { doc, err ->
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


    companion object {
        fun fromDocument(doc: DocumentSnapshot): CollaborativePlaylist = runBlocking {
            CollaborativePlaylist(
                PlaylistId(
                    doc.getString("name")!!,
                    App.gson.fromJson(doc.getString("owner")!!),
                    UUID.fromString(doc.id)
                ),
                doc.getLong("color")?.toInt()
            ).apply {
                operations puts App.gson.fromJson(doc.getString("operations")!!)
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

//        suspend fun mostRecent(
//            since: Date = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
//        ): List<CollaborativePlaylist> {
//            val db = FirebaseFirestore.getInstance()
//            return suspendCoroutine { cont ->
//                db.collection("playlists").whereGreaterThanOrEqualTo("lastModified", since).get().addOnCompleteListener {
//                    val docs = it.result?.documents ?: listOf()
//                    cont.resume(docs.map { fromDocument(it) })
//                }
//            }
//        }


        suspend fun fromSpotifyPlaylist(userId: String, id: String): Result<SongPlaylist, Exception> {
            return try {
                val playlist = Spotify.getPlaylist(userId, id)
                Result.Ok(SongPlaylist(
                    PlaylistId(
                        playlist.name,
                        Sync.selfUser,
                        UUID.nameUUIDFromBytes(id.toByteArray())
                    )
                ).apply {
                    for (item in playlist.items) {
                        add(item.track, 0)
                    }
                })
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}

fun CollaborativePlaylist.add(context: Context, song: Song) = addAll(context, listOf(song))
fun CollaborativePlaylist.addAll(context: Context, songs: Iterable<Song>) = context.run {
    toast(when {
        addAll(songs) -> getString(R.string.playlist_added_track, id.name)
        else -> getString(R.string.playlist_duplicate, id.name)
    })
}