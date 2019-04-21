package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import android.support.annotation.VisibleForTesting
import com.github.salomonbrys.kotson.fromJson
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.BaseActivity
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.lazy
import com.loafofpiecrust.turntable.util.minutes
import com.loafofpiecrust.turntable.util.withReplaced
import kotlinx.collections.immutable.immutableListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast
import java.util.*

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
//@Serializable
class SongPlaylist(
    override var id: PlaylistId
) : Playlist {
    override val icon: Int
        get() = R.drawable.ic_queue

    override var color: Int? = null

    @VisibleForTesting
    internal var lastSyncTime: Long = 0

    var lastModifiedTime: Long = 0
        private set

    /**
     * Maximum length of one side in milliseconds
     */
    var maxSideDuration: Long = SIDE_UNLIMITED
        private set

    var isPublic: Boolean = false
        private set

    val sides = ConflatedBroadcastChannel(
        immutableListOf(immutableListOf<Track>())
    )

    val tracksChannel: ReceiveChannel<List<Song>>
        get() = sides.openSubscription().map {
            it.lazy.flatten()
                .map { it.song }
                .toList()
        }

    override suspend fun resolveTracks(): List<Song> =
        sides.value.lazy.flatten().map { it.song }.toList()

    val sideCount: Int get() = sides.value.size

    fun durationOfSide(side: Int): Int =
        sides.value.getOrNull(side)?.sumBy {
            it.song.duration.takeIf { it > 0 }
                ?: DEFAULT_TRACK_DURATION.intValue
        } ?: 0

    fun sideIsFull(sideIdx: Int): Boolean =
        durationOfSide(sideIdx) >= maxSideDuration

    fun sideName(sideIdx: Int): String =
        ('A' + sideIdx).toString()

    val totalDuration: Int get() =
        (0 until sideCount).sumBy { durationOfSide(it) }


    enum class AddResult {
        SIDE_FULL,
        DUPLICATE_SONG,
        ADDED
    }
    fun add(song: Song, sideIdx: Int = 0): AddResult {
        val side = sides.value[sideIdx]
        return when {
            sideIsFull(sideIdx) -> AddResult.SIDE_FULL
            side.any { it.song.id == song.id } -> AddResult.DUPLICATE_SONG
            else -> runBlocking {
                modify()
                val track = Track(song)
                if (sideCount <= sideIdx) {
                    sides putsMapped { it.add(immutableListOf(track)) }
                } else {
                    sides putsMapped { it.set(sideIdx, it[sideIdx].add(track)) }
                }
                AddResult.ADDED
            }
        }
    }

    fun addSide(startingTrack: Song? = null) {
        runBlocking {
            sides putsMapped {
                it.add(startingTrack?.let {
                    immutableListOf(Track(it))
                } ?: immutableListOf())
            }
        }
    }

    fun rename(newName: String) {
        modify()
        id = id.copy(name = newName)
    }

    fun recolor(newColor: Int) {
        modify()
        color = newColor
    }

    fun remove(songId: SongId) {
        modify()
        runBlocking {
            sides.putsMapped { sides ->
                for ((sideIdx, side) in sides.withIndex()) {
                    val index = side.indexOfFirst { it.song.id == songId }
                    if (index != -1) {
                        return@putsMapped sides.set(sideIdx, side.removeAt(index))
                    }
                }
                sides
            }
        }
    }

    fun move(songId: SongId, replacing: SongId) {
        modify()
        runBlocking {
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
                destSide.add(destIdx, src.copy(lastMovedTime = lastModifiedTime))

                result.lazy.map { it.toImmutableList() }.asIterable().toImmutableList()
            }
        }
    }

    fun canModify(user: User): Boolean =
        id.owner == null || user.username == id.owner?.username

    private fun modify() {
        if (!canModify(Sync.selfUser)) {
            error("The current user cannot modify this playlist.")
        }

        lastModifiedTime = System.currentTimeMillis()
    }

    fun publish() {
        // TODO: Check that we're logged in here.
        if (id.owner == null) {
            if (Sync.isLoggedIn) {
                id = id.copy(owner = Sync.selfUser)
            } else {
                Sync.requestLogin(BaseActivity.current!!, soft = false)
                return
            }
        }
        if (!isPublic) {
            upload()
        } else {
            GlobalScope.launch { syncWithRemote() }
        }
    }

    fun unpublish() {
        isPublic = false
        val db = FirebaseFirestore.getInstance()
        db.playlists()
            .document(id.uuid.toString())
            .delete()
    }

    private suspend fun syncWithRemote() {
        val remote = fromDocument(try {
            download()
        } catch (e: Exception) {
            // failed to find it in the database.
            e.printStackTrace()
            return
        })

        val mergeNeeded = lastSyncTime >= remote.lastModifiedTime
        if (mergeNeeded) {
            mergeWith(remote)
            delay(5)
        }
        if (mergeNeeded || lastModifiedTime > lastSyncTime) {
            lastSyncTime = System.currentTimeMillis()
            upload()
        }
    }

    private fun upload() {
        val db = FirebaseFirestore.getInstance()
        db.playlists()
            .document(id.uuid.toString())
            .set(toDocument())
            .addOnCompleteListener {
                if (!it.isSuccessful) {
                    it.exception?.printStackTrace()
                } else {
                    isPublic = true
                }
            }
    }

    private suspend fun download(): DocumentSnapshot =
        FirebaseFirestore.getInstance()
            .playlists()
            .document(id.uuid.toString())
            .get().await()

    private fun toDocument(): Map<String, Any> = runBlocking {
        mapOf(
            "name" to id.name,
            "owner" to (id.owner?.username ?: ""),
            "lastModifiedTime" to lastModifiedTime,
            "maxSideDuration" to maxSideDuration,
            "sides" to App.gson.toJson(sides.value)
        )
    }


    /**
     * @return true if merging was necessary
     */
    internal suspend fun mergeWith(remote: SongPlaylist): Boolean {
        // If we've synced since the remote was last modified,
        // we don't need to do anything
//            return false
//        }
        // For simplicity, let's assume the same # of sides first
        sides putsMapped { sides ->
            sides.lazy.zip(remote.sides.value.lazy).map { (localSide, remoteSide) ->
                // Compile the shared set of songs with indices based on the remote.
                val compiled = remoteSide.toMutableList()
                compiled.retainAll(localSide)

                println("shared = $compiled")

                val missingRemotes = remoteSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { (_, track) ->
                        track.addedTime > lastSyncTime && !localSide.contains(track)
                    }

                val missingLocals = localSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { (_, track) ->
                        track.addedTime > lastSyncTime && !remoteSide.contains(track)
                    }

                for ((index, missingTrack) in missingRemotes + missingLocals) {
                    // keep the track if it was added *after* last sync
                    // otherwise, the other side removed it from the playlist.
                    compiled.add(index, missingTrack)
                }

                println("before moves = $compiled")

                // all that's left are moved tracks
                val movedRemotes = remoteSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { (_, track) ->
                        track.addedTime < lastSyncTime && track.lastMovedTime >= lastSyncTime
                    }

                println("moved remotes = ${movedRemotes.toList()}")

                val movedLocals = localSide.lazy
                    .mapIndexed { index, track -> index to track }
                    .filter { (_, track) ->
                        track.addedTime < lastSyncTime && track.lastMovedTime >= lastSyncTime
                    }

                println("moved locals = ${movedLocals.toList()}")

                val movedTracks = (movedRemotes + movedLocals).toListSortedBy {
                    // descending
                    -it.second.lastMovedTime
                }.dedupMerge(
                    { (_, a), (_, b) -> a.song.id == b.song.id },
                    { a, b ->
                        if (a.second.lastMovedTime >= b.second.lastMovedTime) {
                            a
                        } else b
                    }
                )

                println("moved tracks = $movedTracks")

                for ((index, track) in movedTracks) {
                    compiled.remove(track)
                    compiled.add(index, track)
                }

                compiled.toImmutableList()
            }.asIterable().toImmutableList()
        }
        return true
    }

    data class Track(
        val song: Song,
        val addedTime: Long = System.currentTimeMillis(),
        val lastMovedTime: Long = System.currentTimeMillis()
    ) {
        override fun hashCode() = song.id.hashCode()
        override fun equals(other: Any?) = other === this ||
            (other is Track && song.id == other.song.id)
    }

    companion object {
        const val SIDE_UNLIMITED: Long = Long.MAX_VALUE
        private val DEFAULT_TRACK_DURATION = 4.minutes

        private fun fromDocument(doc: DocumentSnapshot) = SongPlaylist(
            PlaylistId(
                doc.getString("name")!!,
                User.resolve(doc.getString("owner")!!)!!,
                UUID.fromString(doc.id)
            )
        ).apply {
            lastSyncTime = System.currentTimeMillis()
            lastModifiedTime = doc.getLong("lastModifiedTime")!!
            maxSideDuration = doc.getLong("maxSideDuration")!!
            isPublic = true
            sides.offer(App.gson.fromJson(doc.getString("sides")!!))
        }
    }
}

fun SongPlaylist.add(ctx: Context, song: Song) {
    if (sideCount <= 1) {
        addToSide(ctx, 0, song)
    } else {
        pickSideForAdd(ctx, song)
    }
}

private fun SongPlaylist.pickSideForAdd(
    context: Context, song: Song
) = with(context) {
    val sideNames = (0 until sideCount).map { i ->
        getString(R.string.mixtape_side, sideName(i))
    }
    selector(
        getString(R.string.mixtape_pick_side),
        sideNames
    ) { dialog, idx ->
        addToSide(context, idx, song)
    }
}

private fun SongPlaylist.addToSide(
    context: Context, idx: Int, song: Song
): Unit = with(context) {
    val result = add(song, idx)
    when (result) {
        SongPlaylist.AddResult.ADDED ->
            toast(getString(R.string.playlist_added_track, id.name))
        SongPlaylist.AddResult.DUPLICATE_SONG ->
            toast(getString(R.string.playlist_duplicate, id.name))
        SongPlaylist.AddResult.SIDE_FULL ->
            maybeNewSide(context, idx, song)
    }
}

private fun SongPlaylist.maybeNewSide(
    context: Context, idx: Int, song: Song
) = with(context) {
    alert {
        title = getString(
            R.string.playlist_is_full,
            getString(R.string.mixtape_side_named, sideName(idx), id.name)
        )

        positiveButton("New Side") {
            addSide(song)
            toast(getString(R.string.playlist_added_track, id.name))
        }
        neutralPressed("Other Side") {
            pickSideForAdd(context, song)
        }
        cancelButton {}
    }.show()
}
