package com.loafofpiecrust.turntable.model.playlist

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.appends
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.util.lazy
import com.loafofpiecrust.turntable.util.serialize
import com.loafofpiecrust.turntable.util.toObject
import com.loafofpiecrust.turntable.util.withReplaced
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Generalizes various playlist forms:
 * - Collaborative playlist where multiple users have write access.
 * - Mixtape with restrictions placed on length, composed of sides.
 *
 * Approach:
 * - A playlist can have 0 or more Side Boundaries, which mark where each side starts.
 * - A standard double-sided mixtape simply has 2 Side Boundaries
 * - Every playlist instance is mutable, but we provide a method to check permissions.
 * - It is the duty of the UI to enforce permissions.
 * - Represented as a list of Operations that compose into the actual track list
 */
class GeneralPlaylist(
    override val id: PlaylistId,
    val owner: User = Sync.selfUser
): Music {
    private var lastSyncTime: Long = 0
    private var lastModifiedTime: Long = 0

    var maxSideDuration: Long = SIDE_UNLIMITED
        private set

    var isPublic: Boolean = false
        private set

    val sides = ConflatedBroadcastChannel(emptyList<List<Track>>())

//    private val operations = ConflatedBroadcastChannel(emptyList<Operation>())
//
//    @Transient
//    val sides = operations.openSubscription().map { ops ->
//        val sides = mutableListOf<MutableList<Song>>()
//        for (op in ops) {
//            op.apply(sides)
//        }
//        sides
//    }.replayOne()

    val sideCount: Int get() = sides.value.size

    fun durationOfSide(side: Int): Int =
        sides.value.getOrNull(side)?.size ?: 0

    val totalDuration: Int get() =
        (0 until sideCount).sumBy { durationOfSide(it) }


    fun add(song: Song, side: Int = 0) {
        modify()
        val track = Track(song)
        if (sideCount <= side) {
            sides appends listOf(track)
        } else {
            sides putsMapped { it.withReplaced(side, it[side] + track) }
        }
    }

    fun addSide() {
        sides appends emptyList()
    }

    fun remove(songId: SongId) {
        modify()
        sides.putsMapped { sides ->
            val result = sides.map { it.toMutableList() }
            for (side in result) {
                val index = side.indexOfFirst { it.song.id == songId }
                if (index != -1) {
                    side.removeAt(index)
                    break
                }
            }
            result
        }
    }

    fun move(songId: SongId, replacing: SongId) {
        modify()
        sides.putsMapped { sides ->
            val result = sides.map { it.toMutableList() }

            // find the list and index of the destination.
            val (destSide, destIdx) = result.lazy.map {
                it to it.indexOfFirst { replacing == it.song.id }
            }.first { it.second != -1 }
            val (srcSide, srcIdx) = result.lazy.map {
                it to it.indexOfFirst { songId == it.song.id }
            }.first { it.second != -1 }

            val src = srcSide.removeAt(srcIdx)
            destSide.add(destIdx, src)

            result
        }
    }

    private fun canModify(user: User): Boolean =
        user == owner

    private fun modify() {
        if (!canModify(Sync.selfUser)) {
            error("The current user cannot modify this playlist.")
        }

        lastModifiedTime = System.currentTimeMillis()
    }

    /**
     * @return true if merging was necessary
     */
    private fun mergeWith(remote: GeneralPlaylist): Boolean {
        // If we've synced since the remote was last modified,
        // we don't need to do anything
        if (lastSyncTime >= remote.lastModifiedTime) {
            return false
        }
        // For simplicity, let's assume the same # of sides first
        sides putsMapped {
            it.lazy.zip(remote.sides.value.lazy).map { (localSide, remoteSide) ->
                // Compile the shared set of songs with indices based on the remote.
                val compiled = remoteSide.toMutableList()
                compiled.retainAll(localSide)

                val missingRemotes = remoteSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { !localSide.contains(it.second) }

                val missingLocals = localSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { !remoteSide.contains(it.second) }

                for ((index, missingTrack) in missingRemotes + missingLocals) {
                    // keep the track if it was added *after* last sync
                    // otherwise, the other side removed it from the playlist.
                    if (missingTrack.addedTime >= lastSyncTime) {
                        compiled.add(index, missingTrack)
                    }
                }
                compiled
            }.toList()
        }
        return true
    }

    private suspend fun syncWithRemote() {
        val remote = try {
            download().await()
        } catch (e: Exception) {
            // failed to find it in the database.
            e.printStackTrace()
            return
        }

        val mergeNeeded = mergeWith(fromDocument(remote))
        if (mergeNeeded) {
            upload()
        }
    }

    private fun upload() {
        val db = FirebaseFirestore.getInstance()
        db.playlists()
            .document(id.uuid.toString())
            .set(toDocument())
    }

    private fun download(): Task<DocumentSnapshot> = run {
        val db = FirebaseFirestore.getInstance()
        db.playlists()
            .document(id.uuid.toString())
            .get()
    }


    data class Track(
        val song: Song
    ) {
        val addedTime: Long = System.currentTimeMillis()

        override fun hashCode() = song.id.hashCode()
        override fun equals(other: Any?) =
            other is Track && song.id == other.song.id
    }

    private sealed class Operation {
        val timestamp: Long = System.currentTimeMillis()

        abstract fun apply(sides: MutableList<MutableList<Song>>)

        data class Add(
            val song: Song,
            val side: Int
        ): Operation() {
            override fun apply(sides: MutableList<MutableList<Song>>) {
                while (sides.size <= side) {
                    sides.add(mutableListOf())
                }
                sides[side].add(song)
            }
        }

        data class Remove(
            val id: SongId
        ): Operation() {
            override fun apply(sides: MutableList<MutableList<Song>>) {
                sides.forEach { it.removeAll { it.id == this.id } }
            }
        }

        data class Move(
            val id: SongId,
            val replacing: SongId
        ): Operation() {
            override fun apply(sides: MutableList<MutableList<Song>>) {
                // find the list and index of the destination.
                val (destSide, destIdx) = sides.lazy.map {
                    it to it.indexOfFirst { replacing == it.id }
                }.first { it.second != -1 }
                val (srcSide, srcIdx) = sides.lazy.map {
                    it to it.indexOfFirst { id == it.id }
                }.first { it.second != -1 }

                val src = srcSide.removeAt(srcIdx)
                destSide.add(destIdx, src)
            }
        }
    }

    private fun toDocument(): Map<String, Any> = runBlocking {
        mapOf(
            "name" to id.name,
            "owner" to Blob.fromBytes(serialize(owner)),
            "lastModifiedTime" to lastModifiedTime,
            "maxSideDuration" to maxSideDuration,
            "sides" to Blob.fromBytes(serialize(sides.value))
        )
    }

    companion object {
        const val SIDE_UNLIMITED: Long = -1
        private val DEFAULT_TRACK_DURATION = TimeUnit.MINUTES.toMillis(4)

        private fun fromDocument(doc: DocumentSnapshot) = runBlocking {
            GeneralPlaylist(
                PlaylistId(doc.getString("name")!!, UUID.fromString(doc.id)),
                doc.getBlob("owner")!!.toObject()
            ).apply {
                lastSyncTime = System.currentTimeMillis()
                lastModifiedTime = doc.getLong("lastModifiedTime")!!
                maxSideDuration = doc.getLong("maxSideDuration")!!
                isPublic = true
                sides.offer(doc.getBlob("sides")!!.toObject())
            }
        }
    }
}

data class Duration(val duration: Long, val unit: TimeUnit) {
    fun to(destUnit: TimeUnit) = unit.convert(duration, destUnit)
}

inline operator fun TimeUnit.invoke(duration: Long) = Duration(duration, this)


suspend fun <T> Task<T>.await(): T = suspendCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            task.exception?.let { e ->
                cont.resumeWithException(e)
            }
        }
    }
}