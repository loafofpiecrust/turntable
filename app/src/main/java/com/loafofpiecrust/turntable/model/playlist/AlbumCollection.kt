package com.loafofpiecrust.turntable.model.playlist

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.shifted
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.util.lazy
import com.loafofpiecrust.turntable.util.serialize
import com.loafofpiecrust.turntable.util.toObject
import com.loafofpiecrust.turntable.util.without
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*


class AlbumCollection(
    override val owner: User,
    override var name: String,
    override var color: Int?,
    override val uuid: UUID
) : AbstractPlaylist(), MutablePlaylist {
    /// For serialization
    private constructor(): this(User(), "", null, UUID.randomUUID())

    private val _albums = ConflatedBroadcastChannel(listOf<AlbumId>())

    val albums: ReceiveChannel<List<AlbumId>> get() = _albums.openSubscription()

    override val icon: Int
        get() = R.drawable.ic_album

    /**
     * NOTE: Is this even valuable or usable?
     * Maybe just return empty for a non-playable collection.
     */
    override val tracksChannel: ReceiveChannel<List<Song>>
        get() = albums.map {
            it.mapNotNull { Repository.find(it) }.lazy
                .flatMap { it.tracks.lazy }
                .toList()
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


    override fun diffAndMerge(newer: AbstractPlaylist): Boolean {
        if (newer is AlbumCollection) {
            _albums puts newer._albums.value
        }
        return false
    }

    fun add(album: AlbumId): Boolean {
        val isNew = _albums.value.find { it == album } == null
        if (isNew) {
            _albums puts _albums.value + album
            updateLastModified()
        }
        return isNew
    }

    fun addAll(albums: List<AlbumId>) {
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
        GlobalScope.launch {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(uuid.toString())
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