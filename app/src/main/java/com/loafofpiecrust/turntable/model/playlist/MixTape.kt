package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Playlist with a time limit and (possibly) multiple sides (eg. A Side, B Side, C Side)
 * Question: How should users browse mixtapes? By tag? popularity? recent?
 */
open class MixTape(
    override val owner: User,
    var type: Type,
    override var name: String,
    override var color: Int?,
    override val uuid: UUID = UUID.randomUUID()
) : AbstractPlaylist() {
    override val icon: Int
        get() = R.drawable.ic_cassette

    /// For serialization
//    private constructor(): this(Sync.User(), Type.C60, "", null, UUID.randomUUID())

    enum class Type(
        val sideCount: Int,
        val sideLength: Int
    ) {
        C46(2, 23),
        C60(2, 30),
        C90(2, 45),
        C120(2, 60);

        val totalLength get() = sideCount * sideLength
        private fun sideName(index: Int): String = ('A' + index).toString()
        val sideNames: List<String> get() = (0 until sideCount).map(::sideName)
    }

    companion object: AnkoLogger {
        fun fromDocument(doc: DocumentSnapshot): MixTape = runBlocking {
            MixTape(
                doc.getBlob("owner")!!.toObject(),
                Type.valueOf(doc.getString("type")!!),
                doc.getString("name")!!,
                doc.getLong("color")?.toInt(),
                UUID.fromString(doc.id)
            ).apply {
                sides puts doc.getBlob("tracks")!!.toObject()
                lastModified = doc.getDate("lastModified")!!
                isPublished = true
            }
        }

        suspend fun queryMostRecent(daysOld: Long, limit: Int = 100): List<MixTape> {
            return GlobalScope.suspendAsync<List<MixTape>> { cont ->
                val db = FirebaseFirestore.getInstance()
                val now = System.currentTimeMillis()
                db.collection("playlists")
                    .whereEqualTo("format", "mixtape")
                    .whereGreaterThan("lastModified", now - TimeUnit.DAYS.toMillis(daysOld))
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            cont.resume(it.result.map(Companion::fromDocument))
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }.await()
        }

        suspend fun allFromUser(owner: User): List<MixTape> {
            return GlobalScope.suspendAsync<List<MixTape>> { cont ->
                val db = FirebaseFirestore.getInstance()
                val now = System.currentTimeMillis()
                db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .whereEqualTo("format", "mixtape")
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                           cont.resume(it.result.map(Companion::fromDocument))
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }.await()
        }
    }

    val sides = ConflatedBroadcastChannel(
        (0 until type.sideCount).map { emptyList<Song>() }
    )

    override val tracksChannel: ReceiveChannel<List<Song>>
        get() = sides.openSubscription().map { it.flatten() }


    /**
     * Is this mixtape complete by lasting within 5 minutes of the alloted tape length?
     */
    val isPublishable get() = Math.abs(totalDuration - type.totalLength) <= 5

    private val totalDuration get() = TimeUnit.MILLISECONDS.toMinutes(
        tracks.sumBy { trackDuration(it) }.toLong()
    )

    /**
     * Duration of the given song in milliseconds.
     * If the song has no listed duration, then an average
     * 4 minutes is assumed as a common song length.
     * (SUBJECT TO CHANGE)
     */
    private fun trackDuration(song: Song): Int {
        return if (song.duration == 0) {
            TimeUnit.MINUTES.toMillis(4).toInt()
        } else song.duration
    }

    fun tracksOnSide(sideIdx: Int): ReceiveChannel<List<Song>> =
//        sides.value[clamp(0, sideNum, type.sideCount - 1)]
        sides.openSubscription().map { it[sideIdx] }

    /**
     * Total length of one side of the mixtape, in minutes.
     */
    private fun durationOfSide(sideIdx: Int) = TimeUnit.MILLISECONDS.toMinutes(
        runBlocking { tracksOnSide(sideIdx).first() }.sumBy { trackDuration(it) }.toLong()
    )

    fun sideIsFull(sideIdx: Int) =
        durationOfSide(sideIdx) >= type.sideLength

    fun updates(): ReceiveChannel<MixTape> {
        val db = FirebaseFirestore.getInstance()
        var listener: ListenerRegistration? = null
        return GlobalScope.produce(onCompletion = { listener?.remove() }) {
            listener = db.playlists().document(uuid.toString()).addSnapshotListener { snapshot, err ->
                if (snapshot?.exists() == true) {
                    val localChange = snapshot.metadata.hasPendingWrites()
                    if (!localChange) {
                        offer(fromDocument(snapshot))
                    }
                }
            }
        }
    }
}

// Extensions!!
fun MutableMixtape.add(ctx: Context, song: Song) = ctx.run {
    selector(getString(R.string.mixtape_pick_side), type.sideNames) { dialog, idx ->
        val result = add(idx, song)
        toast(when (result) {
            MutableMixtape.AddResult.ADDED -> getString(R.string.playlist_added_track, name)
            MutableMixtape.AddResult.SIDE_FULL -> getString(R.string.playlist_is_full, getString(R.string.mixtape_side_named, type.sideNames[idx], name))
            MutableMixtape.AddResult.DUPLICATE_SONG -> getString(R.string.playlist_duplicate, name)
        })
    }
}

class MutableMixtape(
    owner: User,
    type: MixTape.Type,
    name: String,
    color: Int?,
    uuid: UUID = UUID.randomUUID()
): MixTape(owner, type, name, color, uuid), MutablePlaylist {
    enum class AddResult {
        SIDE_FULL,
        DUPLICATE_SONG,
        ADDED
    }

    /**
     * Adds the given song to the given side of the mixtape,
     * unless the side is full or already contains that song.
     */
    fun add(sideIdx: Int, song: Song): AddResult {
        val side = sides.value[sideIdx]
        return when {
            sideIsFull(sideIdx) -> AddResult.SIDE_FULL
            side.contains(song) -> AddResult.DUPLICATE_SONG
            else -> {
                sides puts sides.value.withReplaced(sideIdx, side + song)
                updateLastModified()
                AddResult.ADDED
            }
        }
    }

    /**
     * @return true if all the songs were added
     */
    fun addAll(sideIdx: Int, newSongs: List<Song>): AddResult {
        // Adds songs from the given list until that side is full (add returns false)
        // or the list is spent (all are added).
        val results = newSongs.map { add(sideIdx, it) }
        return when {
            results.any { it == AddResult.SIDE_FULL } -> AddResult.SIDE_FULL
            results.all { it == AddResult.DUPLICATE_SONG } -> AddResult.DUPLICATE_SONG
            else -> AddResult.ADDED
        }
    }

//    fun replaceSides(newTracks: List<List<Song>>) {
//        sides puts newTracks
//    }

    fun remove(sideIdx: Int, index: Int) {
//        val sideIdx = clamp(0, sideIdx, type.sideCount - 1)
        val side = sides.value[sideIdx]
        if (side.isNotEmpty()) {
            sides puts sides.value.withReplaced(sideIdx, side.without(index))
            updateLastModified()
        }
    }

    fun move(sideIdx: Int, from: Int, to: Int) {
//        val sideIdx = clamp(0, sideIdx, type.sideCount - 1)
        val side = sides.value[sideIdx]
        sides puts sides.value.withReplaced(sideIdx, side.shifted(from, to))
        updateLastModified()
    }

    /**
     * Publish this mixtape to the global database for public access.
     * A mixtape's published version can only be modified by the original author republishing it.
     */
    override fun publish() {
        GlobalScope.launch {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists").document(uuid.toString())
                .set(mapOf(
                    "type" to type.toString(),
                    "format" to "mixtape",
                    "name" to name,
                    "color" to color,
                    "lastModified" to lastModified,
                    "createdTime" to createdTime,
                    "tracks" to Blob.fromBytes(serialize(sides.value)),
                    "owner" to Blob.fromBytes(serialize(owner))
                ))
        }
        isPublished = true
//        databaseUser.continueWith {
//            database.updateOne(
//                Document(mapOf("_id" to uuid)),
//                Document(mapOf(
//                    "_id" to uuid,
//                    "type" to Binary(App.kryo.concreteToBytes(type, 2)),
//                    "id" to uuid,
//                    "color" to color,
//                    "lastModified" to lastModified,
//                    "tracks" to Binary(App.kryo.objectToBytes(sides.value, compress=true)),
//                    "userId" to stitchClient.auth.userId
//                )),
//                true
//            ).addOnCompleteListener {
//                debug { "success? ${it.isSuccessful}. result: ${it.result}" }
//            }
//        }
    }
}