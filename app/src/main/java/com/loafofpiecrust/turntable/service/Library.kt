package com.loafofpiecrust.turntable.service

import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.github.ajalt.timberkt.Timber
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.LocalAlbum
import com.loafofpiecrust.turntable.model.album.MergedAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.LocalArtist
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.song.LocalSongId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.serialize.page
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.intValue
import com.mcxiaoke.koi.ext.longValue
import com.mcxiaoke.koi.ext.stringValue
import io.ktor.client.request.get
import io.paperdb.Paper
import kotlinx.collections.immutable.immutableListOf
import kotlinx.collections.immutable.immutableMapOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.map
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.io.File
import java.io.Serializable
import java.util.*
import kotlin.collections.HashMap

/**
 * Manages all our music and album covers, including loading from MediaStore
 */
object Library: CoroutineScope by GlobalScope {
    data class AlbumMetadata(
        val id: AlbumId,
        val artworkUri: String?,
        val lastUpdated: Long = System.currentTimeMillis(),
        val addedDate: Date = Date()
    ) : Serializable {
        constructor(): this(AlbumId("", ArtistId("")), null)
    }

    data class ArtistMetadata(
        val id: ArtistId,
        val artworkUri: String?,
        val lastUpdated: Long = System.currentTimeMillis()
    ) : Serializable {
        constructor(): this(ArtistId(""), null)
    }

    /// Map of SongId to local file path for that song.
    private val localSongSources = HashMap<SongId, String>()

    val localSongs = ConflatedBroadcastChannel<List<Song>>()
//    private val _albums = ConflatedBroadcastChannel<List<Album>>(listOf())

    private val albumCovers by Paper.page("albumMetadata") {
        immutableMapOf<AlbumId, Library.AlbumMetadata>()
    }

    private val artistMeta by Paper.page("artistMetadata") {
        immutableMapOf<ArtistId, Library.ArtistMetadata>()
    }

    val remoteAlbums by Paper.page("remoteAlbums") {
        immutableListOf<Album>()
    }

    private val cachedPlaylists = ConflatedBroadcastChannel(
        immutableListOf<Playlist>()
    )

    private var initialized = false

    private val localAlbums: ReceiveChannel<List<Album>> get() = localSongs.openSubscription().map {
        it.groupBy {
            (it.platformId as? LocalSongId)?.albumId
        }.values.map { songs ->
            val tracks = songs.sortedBy { it.discTrack }
            LocalAlbum(songs.first().id.album, tracks)
        }
    }

    /**
     * Artist sort key: uuid
     * Album sort key: name + artist
     * Song sort key: name + album + artist
     */
    val albumsMap: ConflatedBroadcastChannel<Map<AlbumId, Album>> =
        combineLatest(localAlbums, remoteAlbums.openSubscription()) { a, b ->
            (a.lazy + b.lazy).associateByTo(
                MergingHashMap { a, b -> MergedAlbum(a, b) }
            ) { it.id }
        }.replayOne()

    val songsMap: ConflatedBroadcastChannel<Map<SongId, Song>> =
        albumsMap.openSubscription().map {
            it.values.lazy.flatMap { runBlocking { it.resolveTracks().lazy } }
                .map { it.id to it }
                .toMap()
        }.replayOne()

    val artistsMap: ConflatedBroadcastChannel<Map<ArtistId, Artist>> =
        albumsMap.openSubscription().map { albums ->
            albums.values.groupBy { it.id.artist }.mapValues { (id, albums) ->
                // TODO: Use a dynamic sorting method: eg. sort by uuid, etc.
                LocalArtist(
                    id,
                    albums
                ) as Artist
            }
        }.replayOne()


    private var updateTask: Deferred<Unit>? = null


    /// TODO: Find nearest and do fuzzy compare
    fun sourceForSong(id: SongId): String? = localSongSources[id]

    fun songsOnAlbum(id: AlbumId)
        = findAlbum(id).map { it?.resolveTracks() }

    fun findAlbum(key: AlbumId): ReceiveChannel<Album?> =
        albumsMap.openSubscription().map { libAlbums ->
            libAlbums[key]
//            libAlbums.binarySearchElem(key) { it.uuid }
        }

    fun findCachedAlbum(album: AlbumId): ReceiveChannel<Album?>
        = findAlbum(album)

    fun findAlbumFuzzy(id: AlbumId): ReceiveChannel<Album?> = artistsMap.openSubscription().map {
        it.values.find {
            FuzzySearch.ratio(it.id.name, id.artist.name) >= 88
        }?.albums?.find {
            FuzzySearch.ratio(it.id.displayName, id.displayName) >= 88
        }
    }

    fun findSong(id: SongId): ReceiveChannel<Song?> = songsMap.openSubscription().map {
//        it.getOrNull(it.binarySearchBy(uuid) { s: Song -> s.uuid })
        it[id]
    }

    fun findSongFuzzy(song: SongId): ReceiveChannel<Song?> = songsMap.openSubscription().map {
        it.values.find { it.id.fuzzyEquals(song) }
//        it.binarySearchNearestElem(song) { it.uuid }?.takeIf {
//            it.uuid.fuzzyEquals(song)
//        }
    }

    fun findArtist(id: ArtistId): ReceiveChannel<Artist?> = artistsMap.openSubscription().map {
//        val key = Artist(uuid, null, listOf())
//        it.binarySearchElem(uuid) { it.uuid }
        it[id]
    }


    fun findAlbumExtras(id: AlbumId) =
        albumCovers.openSubscription().map { it[id] }

    private fun findArtistExtras(id: ArtistId) =
        artistMeta.openSubscription().map { it[id] }

    fun loadAlbumCover(req: RequestManager, id: AlbumId): ReceiveChannel<RequestBuilder<Drawable>?> =
        findAlbumExtras(id).distinctSeq().map {
            it?.artworkUri?.let {
                req.load(it)
            }
        }

    fun loadArtistImage(req: RequestManager, id: ArtistId): ReceiveChannel<RequestBuilder<Drawable>?> =
        findArtistExtras(id).distinctSeq().map {
            it?.artworkUri?.let {
                req.load(it)
            }
        }


    suspend fun addAlbumExtras(meta: AlbumMetadata) {
        albumCovers putsMapped { it.put(meta.id, meta) }
    }

    suspend fun addArtistExtras(meta: ArtistMetadata) {
        artistMeta putsMapped { it.put(meta.id, meta) }
    }


    private fun fillMissingArtwork() = launch {
        val albums = albumsMap.openSubscription().first().values
        val cache = albumCovers.openSubscription().first()
//        val addedCache = listOf<AlbumMetadata>()
        for (album in albums) {
            val key = AlbumMetadata(album.id, null)
            val cached = cache[album.id]

            val now = System.currentTimeMillis()
            // Don't check online for an album cover if all of these conditions pass:
            // 1. There is already an entry for this album in the metadata cache
            // 2. That entry has an artwork url OR it's been recently updated (and couldn't find an image online)
            if (cached != null && (cached.artworkUri != null || (now - cached.lastUpdated) <= METADATA_UPDATE_FREQ)) {
                continue
            }
            
            val artwork = Repositories.fullArtwork(album, true)
            if (artwork != null) {
                addAlbumExtras((AlbumMetadata(album.id, artwork)))
            } else {
                // This album definitely has no cover in cache
                // since it would've been grabbed in the album grouping process and assigned to the album instance.
                // So, we can assume here that we need to look online for this album artwork.
                albumCovers putsMapped { it.put(key.id, key) }
            }

            // Last.FM has an API limit of 1 request per second
            // But the response itself takes a few hundred ms, so let's just wait a few hundred more.
            // The limit isn't super strict
            delay(100)
        }
    }

    private fun fillArtistArtwork(context: Context) = launch {
        val cache = artistMeta.openSubscription().first()
        artistsMap.openSubscription().first().values.forEach { artist ->
            val key = ArtistMetadata(artist.id, null)
            val cached = cache[artist.id]

            val now = System.currentTimeMillis()
            // Don't check online for an album cover if all of these conditions pass:
            // 1. There is already an entry for this album in the metadata cache
            // 2. That entry has an artwork url OR it's been recently updated (and couldn't find an image on Last.FM)
            if (cached != null && (cached.artworkUri != null || (now - cached.lastUpdated) <= METADATA_UPDATE_FREQ)) {
                return@forEach
            }

            val res = try {
                http.get<JsonObject>("https://ws.audioscrobbler.com/2.0/") {
                    parameters(
                        "format" to "json",
                        "api_key" to BuildConfig.LASTFM_API_KEY,
                        "method" to "artist.getinfo",
                        "artist" to artist.id.displayName
                    )
                }
            } catch (e: Exception) {
                return@forEach
            }

            res["artist"]?.get("image")?.get(3)?.get("#text")?.string?.let { uri ->
                artistMeta putsMapped {
                    it.put(artist.id, key.copy(artworkUri = uri))
                }
//                launch(Dispatchers.Main) {
//                    Glide.with(context.applicationContext)
//                        .load(uri)
////                      .apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(key)))
//                        .preload()
//                }
            } ?: run {
                artistMeta putsMapped { it.put(artist.id, key) }
            }

            delay(50)
        }
    }

    fun addRemoteAlbum(album: Album) = launch {
        // Ensure the tracks are loaded before saving.
        if (!album.resolveTracks().isEmpty()) {
            remoteAlbums putsMapped { it.add(album) }
            val artwork = Repositories.fullArtwork(album, search = true)
            if (artwork != null) {
                addAlbumExtras(AlbumMetadata(album.id, artwork))
            }
        }
    }

    fun removeRemoteAlbum(album: Album) = launch {
        val all = remoteAlbums.value
        val idx = all.indexOfFirst { it.id == album.id }
        if (idx != -1) {
            remoteAlbums puts all.removeAt(idx)
            // remove extra metadata for this album
            albumCovers putsMapped { allMeta ->
                allMeta.remove(album.id)
            }
        }
    }

    fun findPlaylist(id: UUID): ReceiveChannel<Playlist?> =
        UserPrefs.playlists.openSubscription().switchMap {
            val r = it.find { it.id.uuid == id }
                ?: UserPrefs.recommendations.value.lazy
                    .mapNotNull { it as? Playlist }
                    .find { it.id.uuid == id }

            if (r != null) {
                produceSingle(r)
            } else findCachedPlaylist(id)
        }

    private fun findCachedPlaylist(id: UUID): ReceiveChannel<Playlist?> =
        cachedPlaylists.openSubscription().map { playlists ->
            playlists.find { it.id.uuid == id }
        }

    fun addPlaylist(pl: Playlist) {
        launch { UserPrefs.playlists putsMapped { it.add(pl) } }
    }

    fun cachePlaylist(pl: Playlist) {
        launch { cachedPlaylists putsMapped { it.add(pl) } }
    }


    fun initData(context: Context) {
        Timber.d { "maybe loading local data" }
        if (!initialized) {
            initialized = true

            Timber.d { "loading local data" }

            // Load all the songs from the MediaStore
            launch {
                updateSongs(context)
                updateLocalArtwork()
                if (UserPrefs.downloadArtworkAuto.value) {
                    fillMissingArtwork()
                    fillArtistArtwork(context)
                }
            }


            // Change events for mediaStore
            // On change, empty _all and refill with songs
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : ContentObserver(Handler()) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        println("songs: External Media has been added at $uri")
                        // Update the song library, but to accomodate rapid changes, wait 2 seconds
                        // before re-querying the MediaStore.
                        val prevTask = updateTask
                        updateTask = async {
                            prevTask?.cancel()
                            delay(1500)
                            prevTask?.join()
                            updateSongs(context)
                        }
                    }
                }
            )
//            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true,
//                object : ContentObserver(Handler()) {
//                    override fun onChange(selfChange: Boolean) {
//                        println("songs: Internal Media has been added")
//                        super.onChange(selfChange)
//                    }
//                }
//            )
        }

    }

//    private fun compileAlbumsFrom(uri: Uri): List<Album> {
//        val albums = mutableListOf<Album>()
//        val cur = App.instance.contentResolver.query(
//            uri,
//            arrayOf(
//                MediaStore.Audio.Albums._ID,
//                MediaStore.Audio.Albums.ALBUM,
//                MediaStore.Audio.Albums.ARTIST,
//                MediaStore.Audio.Albums.FIRST_YEAR
//            ),
//            null,
//            null,
//            null,
//            null
//        )
//        cur.use { cur ->
//            cur.moveToFirst()
//            do {
//
//            } while (cur.moveToNext())
//        }
//    }

    private fun compileSongsFrom(context: Context, uri: Uri): List<Song> {
        val cur = context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.YEAR,
                "album_artist"
            ),
            null,
            null,
            null,
            null
        )
        if (cur == null) {
            Timber.e { "lopc: Cursor failed to load." }
            return emptyList()
        } else if (!cur.moveToFirst()) {
            Timber.i { "User has no local music" }
            return emptyList()
        } else cur.use {
            val songs = ArrayList<Song>(cur.count)
            do {
                try {
                    val durationMillis = cur.intValue(MediaStore.Audio.Media.DURATION)
                    val duration = durationMillis.milliseconds

                    if (durationMillis == 0 || (Song.MIN_DURATION <= duration && duration <= Song.MAX_DURATION)) {
                        var artist = cur.stringValue(MediaStore.Audio.Media.ARTIST)
                        val albumArtist = tryOr(artist) {
                            cur.getString(cur.getColumnIndex("album_artist")) ?: artist
                        }
                        if (artist == "" || artist == "<unknown>") {
                            artist = albumArtist
                        }
                        val id = cur.longValue(MediaStore.Audio.Media._ID)
                        val artistId = cur.longValue(MediaStore.Audio.Media.ARTIST_ID)
                        val albumId = cur.longValue(MediaStore.Audio.Media.ALBUM_ID)
                        val title = cur.stringValue(MediaStore.Audio.Media.TITLE)
                        val album = cur.stringValue(MediaStore.Audio.Media.ALBUM)
//                        val album = cur.stringValue(MediaStore.Audio.Media.ALBUM)
                        val data = cur.stringValue(MediaStore.Audio.Media.DATA)
                        val index = cur.intValue(MediaStore.Audio.Media.TRACK)
                        val year = cur.intValue(MediaStore.Audio.Media.YEAR)

                        val (disc, track) = if (index >= 1000) {
                            // There may be multiple discs
                            index / 1000 to index % 1000
                        } else {
                            1 to index
                        }

                        val songId = SongId(title, album, albumArtist, artist)
                        songs.add(/*LocalSong(
                            data,
                            albumId,*/
                            Song(
                                songId,
                                track,
                                disc,
                                durationMillis,
                                year,
                                platformId = LocalSongId(id, albumId, artistId)
                            )/*,
                            artworkUrl = null
                        )*/)
                        localSongSources[songId] = data
                    }
                } catch (e: Exception) {
                    Timber.e(e) { "Failed to compile song" }
                    break
                }
            } while (cur.moveToNext())
            return songs
        }
    }

    private fun updateSongs(context: Context) {
        // TODO: Add preference for including internal content (default = false)
        // Load the song library here
//        val internal = async(BG_POOL) { compileSongsFrom(MediaStore.Audio.Media.INTERNAL_CONTENT_URI) }
        val external = try {
            compileSongsFrom(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        } catch (e: Exception) {
            Timber.e(e)
            emptyList<Song>()
        }

        localSongs.offer(/*internal.await() +*/ external)
    }

//    private fun updateLocalArtwork() = async(CommonPool) {
//        println("library: trying to push local album covers")
//        val artMap = mutableListOf<AlbumMetadata>()
//        val cur = App.instance.contentResolver.query(
//            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
//            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM_ART),
//            null,
//            null,
//            null,
//            null
//        )
//        if (cur == null) {
//            error("lopc: Album cursor failed to load.")
//        } else if (!cur.moveToFirst()) {
//            error("lopc: No album covers")
//        } else {
//            do {
//                try {
//                    val uuid = cur.longValue(MediaStore.Audio.Albums._ID)
//                    val art = cur.stringValue(MediaStore.Audio.Albums.ALBUM_ART)
//                    val name = cur.stringValue(MediaStore.Audio.Albums.ALBUM)
//                    val artist = cur.stringValue(MediaStore.Audio.Albums.ARTIST)
//                    artMap.add(AlbumMetadata((name + artist).toLowerCase(), art))
//                } catch(e: Exception) {
//                    // No cover for this album
////                    async(UI) { e.printStackTrace() }
//                }
//            } while (cur.moveToNext())
//        }
//        cur.close()
//        println("library: pushing ${artMap.size} album covers")
//        UserPrefs.albumMeta puts artMap
//    }

    /// MediaStore sucks at storing artwork.
    /// It caches for speed at ~300x300, so the quality is ass.
    /// Let's go find the files ourselves!
    private suspend fun updateLocalArtwork() {
        val albums = albumsMap.openSubscription().first().values

        val imageExts = arrayOf("jpg", "jpeg", "gif", "png")
        val frontReg = Regex("\\b(front|cover|folder|album|booklet)\\b", RegexOption.IGNORE_CASE)
        val existingCovers = albumCovers.valueOrNull ?: immutableMapOf()
        for (it in albums) {
            if (it !is LocalAlbum && it !is MergedAlbum) continue

            if (existingCovers.isNotEmpty()) {
                val existing = existingCovers[it.id]
                if (existing != null) {
                    continue
                }
            }

            val firstTrack = it.resolveTracks().firstOrNull() ?: continue
            val local = sourceForSong(firstTrack.id) ?: continue
            val folder = File(local).parentFile
            val imagePaths = folder.listFiles { path ->
                val ext = path.extension
                imageExts.any { ext.equals(it, true) }
            }

            val count = imagePaths.size
            val artPath = when (count) {
                0 -> null
                1 -> imagePaths.first().absolutePath
                else -> (imagePaths.firstOrNull {
                    it.name.contains(frontReg)
                } ?: imagePaths.first()).absolutePath
            }

            if (artPath != null) {
                addAlbumExtras(AlbumMetadata(it.id, artPath))
            }
        }

//        UserPrefs.albumMeta puts metaList
    }


    val ARTIST_META_COMPARATOR = compareBy<ArtistMetadata> { it.id }

    /**
     * Sorting and searching comparator for albums.
     * TODO: Maybe re-order to allow finding albums by an artist by binary searching within albums, then going backwards and forwards
     */
//        val ALBUM_ARTIST_COMPARATOR = compareBy<Album>({ it.artist })

    /**
     * Sorting and searching comparator for album metadata.
     */
    val ALBUM_META_COMPARATOR = compareBy<AlbumMetadata> { it.id }
//        val SONG_ALBUM_COMPARATOR = compareBy<Song>({ it.artist }, { it.album })
//        val SONG_ARTIST_COMPARATOR = compareBy<Song>({ it.artist })


    const val REMOTE_ALBUM_CACHE_LIMIT = 100
    const val REMOTE_ARTIST_CACHE_LIMIT = 20

    val METADATA_UPDATE_FREQ = 14.days.toMillis().toLong()
}
