package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.view.Menu
import com.google.firebase.firestore.FirebaseFirestore
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.reiterate
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.suspendedTask
import com.loafofpiecrust.turntable.util.task
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import java.io.Serializable
import java.util.*

abstract class Playlist(
    open val owner: SyncService.User,
    open var name: String,
    open var color: Int? = null,
    open var lastModified: Date = Date(),
    /**
     * Used to store the playlist "locally" in Google Drive
     * TODO: Get rid of this in favor of centralizing _all_ playlists to Firestore.
     */
    open var remoteFileId: String? = null,
    open val id: UUID = UUID.randomUUID(),
    var isPublished: Boolean = false,
    var isCompletable: Boolean = false
): Music, Serializable {
    var createdTime: Date = Date()
        private set
    abstract val tracks: ReceiveChannel<List<Song>>
    abstract val typeName: String

    override val simpleName: String get() = name
    override fun optionsMenu(ctx: Context, menu: Menu) {

    }

    /// The first time, publishes this playlist to the database.
    /// After that, this pushes updates to the database entry.
    abstract fun publish()

    /// Removes this playlist from the database
    fun unpublish() {
        if (isPublished) {
            val db = FirebaseFirestore.getInstance()
            db.collection("playlists")
                .document(id.toString())
                .delete()
            isPublished = false
        }
    }

    /// @return true if there were changes in our version since last server revision.
    open fun diffAndMerge(newer: Playlist): Boolean {
        this.name = newer.name
        this.color = newer.color
        this.lastModified = newer.lastModified
        this.remoteFileId = newer.remoteFileId
        return false
    }

    open fun updateLastModified() {
        lastModified = Date()
        task { UserPrefs.playlists.reiterate() }
    }


    open fun sendTo(user: SyncService.User) {
        publish()
        SyncService.send(SyncService.Message.Playlist(id), user)
    }

    companion object {
        suspend fun allByUser(owner: SyncService.User): List<Playlist> {
            val db = FirebaseFirestore.getInstance()
            return suspendedTask<List<Playlist>> { cont ->
                db.collection("playlists")
                    .whereEqualTo("owner", owner.username)
                    .get()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            cont.resume(it.result.mapNotNull {
                                when (it.getString("format")) {
                                    "mixtape" -> MixTape.fromDocument(it)

                                    "playlist" -> CollaborativePlaylist.fromDocument(it)

                                    "albums" -> AlbumCollection.fromDocument(it)

                                    else -> throw IllegalStateException("Unrecognized playlist format")
                                }.apply {
                                    createdTime = it.getDate("createdTime") ?: createdTime
                                }
                            })
                        } else {
                            cont.resume(listOf())
                        }
                    }
            }.await()
        }
    }
}


//sealed class SongCollection(
//    owner: SyncService.User,
//    name: String,
//    color: Int? = null
//    /// For saving the current tracklist to JSON
////    var currentTracks: List<Song> = listOf()
//) : Playlist(owner, name, color) {
//    private val _tracks = ConflatedBroadcastChannel(listOf<Song>())
//    override val tracks get() = _tracks.openSubscription()
//
//    override fun diffAndMerge(newer: Playlist): Boolean {
//        super.diffAndMerge(newer)
//        _tracks puts runBlocking { newer.tracks.first() }
//        return false
//    }
//
//    /**
//     * @return true if the collection has reached some implementation defined capacity (if any)
//     */
//    open fun add(song: Song): Boolean {
//        _tracks puts _tracks.value + song.minimize()
//        updateLastModified()
//        return false
//    }
//
//    open fun addAll(songs: List<Song>): Boolean {
//        _tracks puts _tracks.value + songs.map { it.minimize() }
//        updateLastModified()
//        return false
//    }
//
//    open fun remove(index: Int) {
//        if (_tracks.value.isNotEmpty()) {
//            _tracks puts _tracks.value.without(index)
//            updateLastModified()
//        }
//    }
//
//    open fun move(from: Int, to: Int) {
//        _tracks puts _tracks.value.shifted(from, to)
//        updateLastModified()
//    }
//}