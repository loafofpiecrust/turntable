package com.loafofpiecrust.turntable.service

import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.chibatching.kotpref.KotprefModel
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.*
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.LocalArtist
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.song.LocalSongId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseService
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.intValue
import com.mcxiaoke.koi.ext.longValue
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.map
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import java.io.File
import java.io.Serializable
import java.util.*
import kotlin.collections.HashMap

/// Manages all our music and album covers, including loading from MediaStore
object Library: AnkoLogger, CoroutineScope by GlobalScope {
    data class AlbumMetadata(
        val id: AlbumId,
//        val name: String,
//        val artist: String,
        val artworkUri: String?,
        val lastUpdated: Long = System.currentTimeMillis()
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

    data class PlaylistData(
        val name: String,
        val color: Int?,
        val songs: List<Song>
    ): Serializable {
        constructor(): this("", null, listOf())
    }

    /// Map of SongId to local file path for that song.
    private val localSongSources = HashMap<SongId, String>()

    val localSongs = ConflatedBroadcastChannel<List<Song>>()
//    private val _albums = ConflatedBroadcastChannel<List<Album>>(listOf())

    private val albumCovers by lazy {
        UserPrefs.albumMeta.openSubscription().map {
            it.sortedWith(ALBUM_META_COMPARATOR).dedupMergeSorted({ a, b ->
                ALBUM_META_COMPARATOR.compare(a, b) == 0
            }, { a, b -> b })
        }.replayOne()
    }

    private val artistMeta by lazy {
        UserPrefs.artistMeta.openSubscription().map {
            it.sortedWith(ARTIST_META_COMPARATOR).dedupMergeSorted(
                { a, b -> ARTIST_META_COMPARATOR.compare(a, b) == 0 },
                { a, b -> b }
            )
        }.replayOne()
    }

    val remoteAlbums get() = UserPrefs.remoteAlbums

    private val cachedPlaylists = ConflatedBroadcastChannel(listOf<Playlist>())

//    val remoteAlbums: BehaviorSubject<List<Album>> = BehaviorSubject.createDefault(listOf())
    private var initialized = false

//    val songs: Observable<List<Song>> = Observables.combineLatest(localSongs, remoteAlbums)
//        .map { (songs, remotes) ->
//            (songs + remotes.flatMap { it.tracks }).sortedBy { it.searchKey }
//        }

    private val localAlbums: ReceiveChannel<List<Album>> get() = localSongs.openSubscription().map {
        it.groupBy {
            (it.platformId as? LocalSongId)?.albumId
        }.values.map {
            val tracks = it.sortedBy { it.discTrack }
            LocalAlbum(it.first().id.album, tracks)
        }
    }

    /**
     * Artist sort key: uuid
     * Album sort key: name + artist
     * Song sort key: name + album + artist
     */
//    val albums: ConflatedBroadcastChannel<List<Album>> = run {
//        combineLatest(localAlbums, remoteAlbums.openSubscription()) { a, b ->
//            measureTime("albumsLazy(${a.size + b.size})") {
//                (a.lazy + b.lazy).toListSortedBy { it.uuid }.dedupMergeSorted(
//                    { a, b -> a.uuid == b.uuid },
//                    { a, b -> MergedAlbum(a, b) }
//                )
//            }
//        }.replayOne()
//    }
    val albumsMap: ConflatedBroadcastChannel<Map<AlbumId, Album>> = run {
        combineLatest(localAlbums, remoteAlbums.openSubscription()) { a, b ->
            (a.lazy + b.lazy).associateByTo(
                MergingHashMap { a, b -> MergedAlbum(a, b) }
            ) { it.id }
        }.replayOne()
    }

//    val songs: ConflatedBroadcastChannel<List<Song>> = run {
////        combineLatest(localSongs.openSubscription(), remoteAlbums.openSubscription()) { songs, albums ->
////            measureTime("songsLazy(${songs.size} songs, ${albums.size} remote albums)") {
////                (songs.lazy + albums.lazy.flatMap { it.tracks.lazy }).toListSortedBy { it.uuid }
////            }
//        albums.openSubscription().map {
//            measureTime("songsLazy(${it.size} albums)") {
//                it.lazy.flatMap { it.tracks.lazy }.toListSortedBy { it.uuid }
//            }
//        }.replayOne()
//    }
    val songsMap: ConflatedBroadcastChannel<Map<SongId, Song>> = run {
        albumsMap.openSubscription().map {
            it.values.lazy.flatMap { it.tracks.lazy }
                .map { it.id to it }
                .toMap()
        }.replayOne()
    }

//    val artists: ConflatedBroadcastChannel<List<Artist>> = run {
//        albums.openSubscription().map { albums ->
//            measureTime("artistsLazy(${albums.size} albums)") {
//                albums.groupBy { it.uuid.artist }.values.lazy.map { it: List<Album> ->
//                    val firstAlbum = it.find { it is LocalAlbum } ?: it.first()
//                    // TODO: Use a dynamic sorting method: eg. sort by uuid, etc.
//                    LocalArtist(
//                        firstAlbum.uuid.artist,
//                        it
//                    ) as Artist
//                }.toListSortedBy { it.uuid }
//            }
//        }.replayOne()
//    }
    val artistsMap: ConflatedBroadcastChannel<Map<ArtistId, Artist>> = run {
        albumsMap.openSubscription().map { albums ->
            albums.values.groupBy { it.id.artist }.mapValues { (id, albums) ->
                // TODO: Use a dynamic sorting method: eg. sort by uuid, etc.
                LocalArtist(
                    id,
                    albums
                ) as Artist
            }
        }.replayOne()
    }


    private var updateTask: Deferred<Unit>? = null



    class Binder(val music: Library) : android.os.Binder()

    /// TODO: Find nearest and do fuzzy compare
    fun sourceForSong(id: SongId): String? = localSongSources[id]

    fun songsOnAlbum(id: AlbumId)
        = findAlbum(id).map { it?.tracks }

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

//    fun findArtistFuzzy(name: String): Artist? = artists.blockingFirst().let {
//        it.find { FuzzySearch.ratio(it.uuid.name, name) >= 88 }
//    }

    fun findAlbumExtras(id: AlbumId) = albumCovers.openSubscription().map {
        val key = AlbumMetadata(id, "")
        it.binarySearchElem(key, ALBUM_META_COMPARATOR)
    }

    private fun findArtistExtras(id: ArtistId) = artistMeta.openSubscription().map {
        val key = ArtistMetadata(id, "")
        it.binarySearchElem(key, ARTIST_META_COMPARATOR)
    }

    fun loadAlbumCover(req: RequestManager, id: AlbumId): ReceiveChannel<RequestBuilder<Drawable>?> =
        findAlbumExtras(id).map {
            it?.artworkUri?.let {
                req.load(it)
            }
        }

    fun loadArtistImage(req: RequestManager, id: ArtistId): ReceiveChannel<RequestBuilder<Drawable>?> =
        findArtistExtras(id).map {
            it?.artworkUri?.let {
                req.load(it)
            }
        }



    fun addAlbumExtras(meta: AlbumMetadata) {
        UserPrefs.albumMeta appends meta
    }

    fun addArtistExtras(meta: ArtistMetadata) {
        UserPrefs.artistMeta appends meta
    }


    private fun fillMissingArtwork() = launch {
        val albums = albumsMap.openSubscription().first().values
        val cache = albumCovers.openSubscription().first()
//        val addedCache = listOf<AlbumMetadata>()
        albums/*.filter {
            val cover = it.artworkUrl
            cover == null || cover.isEmpty() || cover == "null"
        }*/.forEach { album ->
            val key = AlbumMetadata(album.id, null)
            val cachedIdx = cache.binarySearch(key, ALBUM_META_COMPARATOR)
            val cached = cache.getOrNull(cachedIdx)

            val now = System.currentTimeMillis()
            // Don't check online for an album cover if all of these conditions pass:
            // 1. There is already an entry for this album in the metadata cache
            // 2. That entry has an artwork url OR it's been recently updated (and couldn't find an image on Last.FM)
            if (cached != null && (cached.artworkUri != null || (now - cached.lastUpdated) <= METADATA_UPDATE_FREQ)) {
                return@forEach
            }

            // This album definitely has no cover in cache
            // since it would've been grabbed in the album grouping process and assigned to the album instance.
            // So, we can assume here that we need to look online for this album artwork.
//            async(CommonPool) {
            // look on Last.FM for artwork
//            val art = Discogs.getFullArtwork(album, true)
//            if (art != null && art.isNotEmpty()) {
//                synchronized(UserPrefs.albumMeta) {
//                    // Add this artwork url to the cache
//                    UserPrefs.albumMeta appends AlbumMetadata(album.uuid, art)
//                }
//            } else {
//                // If Last.FM doesn't have this album, then don't check every single start-up.
//                UserPrefs.albumMeta appends key
//            }
            val updateKey = {
                UserPrefs.albumMeta putsMapped {
                    if (cachedIdx < 0) {
                        it + key
                    } else {
                        it.withReplaced(cachedIdx, key)
                    }
                }
            }

            val res = try {
                Http.get("https://ws.audioscrobbler.com/2.0/", params = mapOf(
                    "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                    "method" to "album.getinfo",
                    "artist" to album.id.artist.displayName,
                    "album" to album.id.displayName
                )).gson.obj
            } catch (e: Exception) {
                return@forEach
            }

            if (res.has("album")) {
                val albumObj = res["album"].obj
                if (albumObj.has("image")) {
                    val uri = albumObj["image"][3]["#text"].nullString
                    if (uri != null && uri.isNotEmpty()) {
//                        task {
//                        synchronized(UserPrefs.albumMeta) {
                        // Add this artwork url to the cache
                        addAlbumExtras(AlbumMetadata(album.id, uri))
//                        }
//                        }.success(UI) {
//                            Glide.with(App.instance)
//                                .load(uri)
////                                .apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(key)))
//                                .preload()
//                        }
                    } else updateKey()
                } else updateKey()
            } else updateKey()

            // Last.FM has an API limit of 1 request per second
            // But the response itself takes a few hundred ms, so let's just wait a few hundred more.
            // The limit isn't overly strict
            delay(100)
//            }
        }
    }

    private fun fillArtistArtwork(context: Context) = launch {
        val cache = artistMeta.openSubscription().first()
        artistsMap.openSubscription().first().values.forEach { artist ->
            val key = ArtistMetadata(artist.id, null)
            val cached = cache.binarySearchElem(key, ARTIST_META_COMPARATOR)

            val now = System.currentTimeMillis()
            // Don't check online for an album cover if all of these conditions pass:
            // 1. There is already an entry for this album in the metadata cache
            // 2. That entry has an artwork url OR it's been recently updated (and couldn't find an image on Last.FM)
            if (cached != null && (cached.artworkUri != null || (now - cached.lastUpdated) <= METADATA_UPDATE_FREQ)) {
                return@forEach
            }
//            val res = http.get<JsonElement>("https://ws.audioscrobbler.com/2.0/", params = mapOf(
//                "api_key" to LASTFM_KEY, "format" to "json",
//                "method" to "artist.getinfo",
//                "artist" to artist.uuid.name
//            )).obj
            val json = try {
                Http.get("https://ws.audioscrobbler.com/2.0/", params = mapOf(
                    "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                    "method" to "artist.getinfo",
                    "artist" to artist.id.displayName
                )).gson
            } catch (e: Exception) {
                return@forEach
            }
            val res = json.obj

            res["artist"]?.get("image")?.get(3)?.get("#text")?.string?.let { uri ->
                UserPrefs.artistMeta appends key.copy(artworkUri = uri)
                launch(Dispatchers.Main) {
                    Glide.with(context.applicationContext)
                        .load(uri)
//                      .apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(key)))
                        .preload()
                }
            } ?: run {
                UserPrefs.artistMeta appends key
            }

            delay(50)
        }
    }

    fun addRemoteAlbum(album: Album) = launch {
//        val existing = findAlbum(album.uuid).first()
//        if (existing == null) {
        // Ensure the tracks are loaded before saving.
            if (!album.tracks.isEmpty()) {
                UserPrefs.remoteAlbums appends album
                KotprefModel.saveFiles()
            }
//        }
    }

    fun removeRemoteAlbum(album: Album) = launch {
        val all = UserPrefs.remoteAlbums.value
        val idx = all.indexOfFirst { it.id == album.id }
        if (idx != -1) {
            UserPrefs.remoteAlbums puts all.without(idx)
            KotprefModel.saveFiles()
        }
    }

    fun findPlaylist(id: UUID): ReceiveChannel<Playlist?>
        = UserPrefs.playlists.openSubscription().switchMap {
            val r = it.find { it.id.uuid == id }
                ?: UserPrefs.recommendations.value.mapNotNull { it as? Playlist }.find { it.id.uuid == id }

            if (r != null) {
                produceSingle(r)
            } else findCachedPlaylist(id)
        }

    private fun findCachedPlaylist(id: UUID): ReceiveChannel<Playlist?>
        = cachedPlaylists.openSubscription().map {
            it.find { it.id.uuid == id }
        }

    fun addPlaylist(pl: Playlist) {
        launch { UserPrefs.playlists appends pl }
    }
    fun cachePlaylist(pl: Playlist) {
        launch { cachedPlaylists appends pl }
    }

//    fun loadArtistImage(req: RequestManager, uuid: Long) =

    fun initData(context: Context) {
        info { "maybe loading local data" }
        if (!initialized) {
            initialized = true

            info { "loading local data" }

            // Load all the songs from the MediaStore
            launch {
                updateSongs(context)
                updateLocalArtwork()
                fillMissingArtwork()
                fillArtistArtwork(context)
            }


            // Change events for mediaStore
            // On change, empty _all and refill with songs
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
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
            error { "lopc: Cursor failed to load." }
            return emptyList()
        } else if (!cur.moveToFirst()) {
            error { "lopc: No music" }
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
                    error("Failed to compile song", e)
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
        info { "compiling songs on sd card" }
        val external = try {
            compileSongsFrom(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        } catch (e: Exception) {
            error(e.message, e)
            emptyList<Song>()
        }

        info { "songs: $external" }

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
        val existingCovers = albumCovers.valueOrNull ?: listOf()
        albums.parMap {
            if (it !is LocalAlbum && it !is MergedAlbum) return@parMap

            if (existingCovers.isNotEmpty()) {
                val existingKey = AlbumMetadata(it.id, null)
                val existing = existingCovers.binarySearchElem(existingKey, ALBUM_META_COMPARATOR)
                if (existing != null) {
                    return@parMap
                }
            }

            val firstTrack = it.tracks.firstOrNull() ?: return@parMap
            val local = sourceForSong(firstTrack.id)
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
        }.awaitAll()

//        UserPrefs.albumMeta puts metaList
    }


    val ARTWORK_OPTIONS by lazy {
        RequestOptions()
            .fallback(R.drawable.ic_default_album)
            .error(R.drawable.ic_default_album)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .fitCenter()
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
