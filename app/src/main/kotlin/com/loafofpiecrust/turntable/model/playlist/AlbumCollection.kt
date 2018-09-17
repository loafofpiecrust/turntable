package com.loafofpiecrust.turntable.model.playlist

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.serialize
import com.loafofpiecrust.turntable.util.toObject
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.util.*


class AlbumCollection(
    override val owner: SyncService.User,
    override var name: String,
    override var color: Int?,
    override val id: UUID
) : Playlist() {
    /// For serialization
    private constructor(): this(SyncService.User(), "", null, UUID.randomUUID())

    private val _albums = ConflatedBroadcastChannel(listOf<Album>())

    val albums: ReceiveChannel<List<Album>> get() = _albums.openSubscription()

    override val typeName: String
        get() = "Album Collection"

    override val tracks: ReceiveChannel<List<Song>>
        get() = albums.map {
            it.asSequence().flatMap {
                it.tracks.asSequence()
            }.toList()
        }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): AlbumCollection = runBlocking {
            AlbumCollection(
                doc.getBlob("owner")!!.toObject(),
                doc.getString("name")!!,
                doc.getLong("color")?.toInt(),
                UUID.fromString(doc.id)
            ).apply {
                isPublished = true
                _albums puts doc.getBlob("albums")!!.toObject()
            }
        }
    }


    override fun diffAndMerge(newer: Playlist): Boolean {
        if (newer is AlbumCollection) {
            _albums puts newer._albums.value
        }
        return false
    }

    fun add(album: Album): Boolean {
        val isNew = _albums.value.find { it.id == album.id } == null
        if (isNew) {
            _albums puts _albums.value + album
            updateLastModified()
        }
        return isNew
    }

    fun addAll(albums: List<Album>) {
        updateLastModified()
        _albums puts _albums.value + albums
    }

    fun remove(index: Int) {
        if (_albums.value.isNotEmpty()) {
            updateLastModified()
            _albums puts _albums.value.without(index)
        }
    }

    fun move(from: Int, to: Int) {
        updateLastModified()
        _albums puts _albums.value.shifted(from, to)
    }

    override fun publish() {
        launch(BG_POOL) {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(id.toString())
                .set(mapOf(
                    "format" to "albums",
                    "owner" to Blob.fromBytes(serialize(owner)),
                    "name" to name,
                    "color" to color,
                    "lastModified" to lastModified,
                    "albums" to Blob.fromBytes(serialize(_albums.value))
                ))
            isPublished = true
        }
    }
}