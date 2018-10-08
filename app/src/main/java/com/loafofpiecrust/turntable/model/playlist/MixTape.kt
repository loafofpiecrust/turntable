package com.loafofpiecrust.turntable.model.playlist

import android.content.Context
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.sync.User
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Playlist with a time limit and (possibly) multiple sides (eg. A Side, B Side, C Side)
 * Question: How should users browse mixtapes? By tag? popularity? recent?
 */
data class MixTape(
    override val owner: User,
    var type: Type,
    override var name: String,
    override var color: Int?,
    override val id: UUID = UUID.randomUUID()
) : Playlist() {
    override val icon: Int
        get() = R.drawable.ic_cassette

    /// For serialization
//    private constructor(): this(SyncService.User(), Type.C60, "", null, UUID.randomUUID())

    enum class Type(
        val sideCount: Int,
        val sideLength: Int
    ) {
        C46(2, 23),
        C60(2, 30),
        C90(2, 45),
        C120(2, 60);

        val totalLength get() = sideCount * sideLength
        private fun sideName(index: Int): String = ('A' + index) + " Side"
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
                _tracks puts doc.getBlob("tracks")!!.toObject()
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

    private val _tracks = ConflatedBroadcastChannel(
        (0 until type.sideCount).map { emptyList<Song>() }
    )

    override val tracks: ReceiveChannel<List<Song>> get() = _tracks.openSubscription().map { it.flatten() }


    /**
     * Is this mixtape complete by lasting within 5 minutes of the alloted tape length?
     */
    val isPublishable get() = Math.abs(totalDuration - type.totalLength) <= 5

    private val totalDuration get() = TimeUnit.MILLISECONDS.toMinutes(
        runBlocking { tracks.first() }.sumBy { it.duration }.toLong()
    )

    fun tracksOnSide(sideIdx: Int): ReceiveChannel<List<Song>> =
//        _tracks.value[clamp(0, sideNum, type.sideCount - 1)]
        _tracks.openSubscription().map { it[sideIdx] }

    /**
     * Total length of one side of the mixtape, in minutes.
     */
    private fun durationOfSide(sideIdx: Int) = TimeUnit.MILLISECONDS.toMinutes(
        runBlocking { tracksOnSide(sideIdx).first() }.sumBy { it.duration }.toLong()
    )

    fun sideIsFull(sideIdx: Int) =
        durationOfSide(sideIdx) >= type.sideLength

    /**
     * @return true if the track was added
     */
    fun add(sideIdx: Int, song: Song): Boolean {
//        val sideIdx = clamp(0, sideIdx, type.sideCount - 1)
        return if (sideIsFull(sideIdx)) {
            false
        } else {
            val side = _tracks.value[sideIdx]
            _tracks puts _tracks.value.withReplaced(sideIdx, side + song)
            updateLastModified()
//            !sideIsFull(sideIdx)
            true
        }
    }

    /**
     * @return true if all the songs were added
     */
    fun addAll(sideIdx: Int, newSongs: List<Song>): Boolean {
        // Adds songs from the given list until that side is full (add returns false)
        // or the list is spent (all are added).
        return newSongs.all { add(sideIdx, it) }
    }

//    fun replaceSides(newTracks: List<List<Song>>) {
//        _tracks puts newTracks
//    }

    fun remove(sideIdx: Int, index: Int) {
//        val sideIdx = clamp(0, sideIdx, type.sideCount - 1)
        val side = _tracks.value[sideIdx]
        if (side.isNotEmpty()) {
            _tracks puts _tracks.value.withReplaced(sideIdx, side.without(index))
            updateLastModified()
        }
    }

    fun move(sideIdx: Int, from: Int, to: Int) {
//        val sideIdx = clamp(0, sideIdx, type.sideCount - 1)
        val side = _tracks.value[sideIdx]
        _tracks puts _tracks.value.withReplaced(sideIdx, side.shifted(from, to))
        updateLastModified()
    }

    /**
     * Publish this mixtape to the global database for public access.
     * A mixtape's published version can only be modified by the original author republishing it.
     */
    override fun publish() {
        GlobalScope.launch {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists").document(id.toString())
                .set(mapOf(
                    "type" to type.toString(),
                    "format" to "mixtape",
                    "name" to name,
                    "color" to color,
                    "lastModified" to lastModified,
                    "createdTime" to createdTime,
                    "tracks" to Blob.fromBytes(serialize(_tracks.value)),
                    "owner" to Blob.fromBytes(serialize(owner))
                ))
        }
        isPublished = true
//        databaseUser.continueWith {
//            database.updateOne(
//                Document(mapOf("_id" to id)),
//                Document(mapOf(
//                    "_id" to id,
//                    "type" to Binary(App.kryo.concreteToBytes(type, 2)),
//                    "id" to id,
//                    "color" to color,
//                    "lastModified" to lastModified,
//                    "tracks" to Binary(App.kryo.objectToBytes(_tracks.value, compress=true)),
//                    "userId" to stitchClient.auth.userId
//                )),
//                true
//            ).addOnCompleteListener {
//                debug { "success? ${it.isSuccessful}. result: ${it.result}" }
//            }
//        }
    }
}

// Extensions!!
fun MixTape.add(ctx: Context, song: Song) {
    ctx.selector(ctx.getString(R.string.mixtape_pick_side), type.sideNames) { dialog, idx ->
        val wasAdded = add(idx, song)
        ctx.toast(
            if (wasAdded) ctx.getString(R.string.playlist_added_track, name)
            else ctx.getString(R.string.playlist_is_full, name)
        )
    }
}