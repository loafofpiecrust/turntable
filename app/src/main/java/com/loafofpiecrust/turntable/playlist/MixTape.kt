package com.loafofpiecrust.turntable.playlist

//import com.mongodb.stitch.android.StitchClient
//import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProvider
//import com.mongodb.stitch.android.services.mongodb.MongoClient
//import org.bson.Document
//import org.bson.types.Binary
//import org.bson.types.ObjectId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.suspendedTask
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.AnkoLogger
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Playlist with a time limit and (possibly) multiple sides (eg. A Side, B Side, C Side)
 * Question: How should users browse mixtapes? By tag? popularity? recent?
 */
@Parcelize
class MixTape(
    override val owner: String,
    var type: Type,
    override var name: String,
    override var color: Int?,
    override val id: UUID
) : Playlist(owner, name, color) {
    override val typeName: String
        get() = "Mix Tape"

    /// For serialization
    constructor(): this("", Type.C60, "", null, UUID.randomUUID())

    enum class Type(
        val sideCount: Int,
        val sideLength: Int
    ) {
        C46(2, 23),
        C60(2, 30),
        C90(2, 45),
        C120(2, 60);

        val totalLength get() = sideCount * sideLength
    }

    companion object: AnkoLogger {
        fun fromDocument(doc: DocumentSnapshot): MixTape = run {
            MixTape(
                doc.getString("owner")!!,
                Type.valueOf(doc.getString("type")!!),
                doc.getString("name")!!,
                doc.getLong("color")?.toInt(),
                UUID.fromString(doc.id)
            ).apply {
                _tracks puts App.kryo.objectFromBytes(
                    doc.getString("tracks")!!.toByteArray(Charsets.ISO_8859_1)
                )
                lastModified = doc.getDate("lastModified")!!
                isPublished = true
            }
        }

//        private val stitchClient by lazy {
//            StitchClient(App.instance, "turntable-akvca")
//        }
//        private val databaseClient by lazy {
//            MongoClient(stitchClient, "mongodb-atlas")
//        }
//        private val database by lazy {
//            databaseClient
//                .getDatabase("TurntableMixTapes")
//                .getCollection("mixtapes")
////                .apply {
////                    // 1 = ascending, -1 = descending
////                    createIndex(Indexes.text("id"))
////                    createIndex(Document(mapOf("type" to 1)))
////                    createIndex(Document(mapOf("lastModified" to -1)))
////                }
//        }
//        private val databaseUser by lazy {
//            stitchClient.logInWithProvider(AnonymousAuthProvider())
//        }
//
//        private fun fromBson(doc: Document): MixTape {
//            val mix = MixTape(
//                App.kryo.concreteFromBytes((doc["type"] as Binary).data),
//                doc.getString("id"),
//                tryOrNull { doc.getInteger("color") },
//                doc.getObjectId("_id"),
//                tryOrNull { doc.getString("userId") }
//            )
//            mix.lastModified = doc.getLong("lastModified")
//            mix._tracks puts App.kryo.objectFromBytes((doc["tracks"] as Binary).data, decompress=true)
//            return mix
//        }

//        fun queryMostRecent(asOldAs: Long, limit: Int = 100): Deferred<List<MixTape>> {
//            return promise { cont ->
//                databaseUser.continueWith {
//                    val now = System.currentTimeMillis()
//                    database.find(Document(mapOf(
//                        "lastModified" to mapOf("\$gt" to (now - asOldAs))
//                    )), limit).addOnCompleteListener {
//                        cont.resume(
//                            try {
//                                it.result.mapNotNull { tryOrNull { fromBson(it) } }
//                            } catch (e: Throwable) {
//                                warn("Failed to load recent mixtapes from MongoDB", e)
//                                listOf<MixTape>()
//                            }
//                        )
//                    }
//                }
//            }
//        }

        suspend fun queryMostRecent(daysOld: Long, limit: Int = 100): List<MixTape> {
            return suspendedTask<List<MixTape>> { cont ->
                val db = FirebaseFirestore.getInstance()
                val now = System.currentTimeMillis()
                db.collection("playlists")
                    .whereEqualTo("format", "mixtape")
                    .whereGreaterThan("lastModified", now - TimeUnit.DAYS.toMillis(daysOld))
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                           cont.resume(it.result.mapNotNull {
                               val mt = MixTape(
                                   it.getString("owner")!!,
                                   Type.valueOf(it.getString("type")!!),
                                   it.getString("name")!!,
                                   it.getLong("color")?.toInt(),
                                   UUID.fromString(it.id)
                               )
                               mt._tracks puts App.kryo.objectFromBytes(
                                   it.getString("tracks")!!.toByteArray(Charsets.ISO_8859_1)
                               )
                               mt.lastModified = it.getDate("lastModified")!!
                               mt
                           })
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }.await()
        }

        suspend fun allFromUser(owner: SyncService.User): List<MixTape> {
            return suspendedTask<List<MixTape>> { cont ->
                val db = FirebaseFirestore.getInstance()
                val now = System.currentTimeMillis()
                db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .whereEqualTo("format", "mixtape")
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                           cont.resume(it.result.mapNotNull {
                               val mt = MixTape(
                                   it.getString("owner")!!,
                                   Type.valueOf(it.getString("type")!!),
                                   it.getString("name")!!,
                                   it.getLong("color")?.toInt(),
                                   UUID.fromString(it.id)
                               )
                               mt._tracks puts App.kryo.objectFromBytes(
                                   it.getString("tracks")!!.toByteArray(Charsets.ISO_8859_1)
                               )
                               mt.lastModified = it.getDate("lastModified")!!
                               mt
                           })
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }.await()
        }
    }

    private val _tracks = ConflatedBroadcastChannel(
        (0 until type.sideCount).map { listOf<Song>() }
    )

    override val tracks get() = _tracks.openSubscription().map { it.flatten() }


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

    private fun sideIsFull(sideIdx: Int) =
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
            _tracks puts _tracks.value.withReplaced(sideIdx, side + song.minimize())
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
        return newSongs.all { add(sideIdx, it.minimize()) }
    }

    fun replaceSides(newTracks: List<List<Song>>) {
        _tracks puts newTracks
    }

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
        val db = FirebaseFirestore.getInstance()
        db.collection("playlists").document(id.toString())
            .set(mapOf(
                "type" to type.toString(),
                "format" to "mixtape",
                "name" to name,
                "color" to color,
                "lastModified" to lastModified,
                "createdTime" to createdTime,
                "tracks" to App.kryo.objectToBytes(_tracks.value).toString(Charsets.ISO_8859_1),
                "owner" to owner
            ))
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