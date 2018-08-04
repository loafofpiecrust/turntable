package com.loafofpiecrust.turntable.playlist

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.song.Song
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import java.util.*


@Parcelize
class AlbumCollection(
    override val owner: String,
    override var name: String,
    override var color: Int?,
    override val id: UUID
) : Playlist(owner, name, color) {
    /// For serialization
    private constructor(): this("", "", null, UUID.randomUUID())

    private val _albums = ConflatedBroadcastChannel(listOf<Album>())

    val albums: ReceiveChannel<List<Album>> get() = _albums.openSubscription()

    override val typeName: String
        get() = "Album Collection"

    override val tracks: ReceiveChannel<List<Song>>
        get() = albums.map { it.flatMap { it.tracks } }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): AlbumCollection {
            return AlbumCollection(
                doc.getString("owner")!!,
                doc.getString("name")!!,
                doc.getLong("color")?.toInt(),
                UUID.fromString(doc.id)
            ).apply {
                isPublished = true
                _albums puts App.kryo.objectFromBytes(
                    doc.getString("albums")!!.toByteArray(Charsets.ISO_8859_1),
                    decompress = true
                )
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
            _albums puts _albums.value + album.minimize()
            updateLastModified()
        }
        return isNew
    }

    fun addAll(albums: List<Album>) {
        updateLastModified()
        _albums puts _albums.value + albums.map { it.minimize() }
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
        val db = FirebaseFirestore.getInstance()
        db.collection("playlists")
            .document(id.toString())
            .set(mapOf(
                "format" to "albums",
                "owner" to owner,
                "name" to name,
                "color" to color,
                "lastModified" to lastModified,
                "albums" to App.kryo.objectToBytes(_albums.value, compress=true).toString(Charsets.ISO_8859_1)
            ))
        isPublished = true
    }
}