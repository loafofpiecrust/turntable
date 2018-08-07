package com.loafofpiecrust.turntable.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.esotericsoftware.minlog.Log
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.album.LocalAlbum
import com.loafofpiecrust.turntable.album.MergedAlbum
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.playlist.Playlist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.util.*
import com.mcxiaoke.koi.ext.intValue
import com.mcxiaoke.koi.ext.longValue
import com.mcxiaoke.koi.ext.stringValue
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.delay
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.AnkoLogger
import java.io.File
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.coroutineContext

/// Manages all our music and album covers, including loading from MediaStore
class Library : Service() {
    companion object: AnkoLogger {
        lateinit var instance: Library
            private set

        val ARTWORK_OPTIONS = RequestOptions()
            .fallback(R.drawable.ic_default_album)
            .error(R.drawable.ic_default_album)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .fitCenter()


        /// Bridges the connection between an Activity and the MusicService instance
        /// Starts the service if it somehow isn't started yet
        fun with(context: Context, cb: (Library) -> Unit) {
            var conn: ServiceConnection? = null
            class Conn: ServiceConnection {
                override fun onServiceConnected(comp: ComponentName, binder: IBinder?) {
                    cb.invoke((binder as Binder).music)
                    context.unbindService(conn)
                }
                override fun onServiceDisconnected(comp: ComponentName) {
                }
            }
            conn = Conn()
            val intent = Intent(App.instance, Library::class.java)
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }

        val ARTIST_COMPARATOR = compareByIgnoreCase<Artist>({ it.id.sortName })
        val ARTIST_META_COMPARATOR = compareByIgnoreCase<ArtistMetadata>({ it.id.sortName })

        /**
         * Sorting and searching comparator for albums.
         * TODO: Maybe re-order to allow finding albums by an artist by binary searching within albums, then going backwards and forwards
         */
        val ALBUM_COMPARATOR = compareByIgnoreCase<Album>(
            { it.id.sortTitle }, { it.id.artist.sortName }, { it.type.name }
        )
//        val ALBUM_ARTIST_COMPARATOR = compareBy<Album>({ it.artist })

        /**
         * Sorting and searching comparator for album metadata.
         */
        val ALBUM_META_COMPARATOR = compareByIgnoreCase<AlbumMetadata>(
            { it.id.sortTitle }, { it.id.artist.sortName }
        )

        val SONG_COMPARATOR = compareByIgnoreCase<Song>(
            { it.id.name }, { it.id.album.sortTitle }, { it.id.album.artist.sortName }
        )
//        val SONG_ALBUM_COMPARATOR = compareBy<Song>({ it.artist }, { it.album })
//        val SONG_ARTIST_COMPARATOR = compareBy<Song>({ it.artist })


        const val REMOTE_ALBUM_CACHE_LIMIT = 100
        const val REMOTE_ARTIST_CACHE_LIMIT = 20

        val METADATA_UPDATE_FREQ = TimeUnit.DAYS.toMillis(14)
    }

    class Binder(val music: Library) : android.os.Binder()
    override fun onBind(intent: Intent): IBinder? = Binder(this)

    init {
        instance = this
    }

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


    private val _songs = ConflatedBroadcastChannel<List<Song>>(listOf())

    private val albumCovers = UserPrefs.albumMeta.openSubscription().map {
        it.sortedWith(ALBUM_META_COMPARATOR).dedupMergeSorted({ a, b ->
            ALBUM_META_COMPARATOR.compare(a, b) == 0
        }, { a, b -> b })
    }.replayOne()

    private val artistMeta = UserPrefs.artistMeta.openSubscription().map {
        it.sortedWith(ARTIST_META_COMPARATOR).dedupMergeSorted({ a, b ->
            ARTIST_META_COMPARATOR.compare(a, b) == 0
        }, { a, b -> b })
    }.replayOne()

    private val remoteAlbums = UserPrefs.remoteAlbums

    private val _cachedAlbums = ConflatedBroadcastChannel(listOf<Album>())
    private val cachedAlbums = _cachedAlbums.openSubscription().map {
        it.sortedWith(ALBUM_COMPARATOR)
    }.replayOne()

    private val _cachedArtists = ConflatedBroadcastChannel(listOf<Artist>())
    private val cachedArtists = _cachedArtists.openSubscription().map {
        it.sortedWith(ARTIST_COMPARATOR)
    }.replayOne()

    private val cachedPlaylists = ConflatedBroadcastChannel(listOf<Playlist>())

//    val remoteAlbums: BehaviorSubject<List<Album>> = BehaviorSubject.createDefault(listOf())
    private var initialized = false

//    val songs: Observable<List<Song>> = Observables.combineLatest(_songs, remoteAlbums)
//        .map { (songs, remotes) ->
//            (songs + remotes.flatMap { it.tracks }).sortedBy { it.searchKey }
//        }

    private val localAlbums: ReceiveChannel<List<Album>> = _songs.openSubscription().map {
        it.groupBy {
            val local = it.local as Song.LocalDetails.Downloaded
//            if (local is Song.LocalDetails.Downloaded) {
//                "${local.albumId}\$id"
//            } else {
//                (it.album + Artist.sortKey(it.artist)).toLowerCase()
//            }
            local.albumId
        }.map {
            val tracks = it.value.sortedBy { (it.disc * 1000) + it.track }
            val firstTrack = tracks.first()
            val id = firstTrack.id.album
            val discNum = id.discNumber
            val album = LocalAlbum(
                id = id,
                tracks = tracks
            )
            album.tracks.forEach {
                it.disc = discNum
            }
            album
        }
    } //.replay(1).autoConnect()

    /**
     * Artist sort key: id
     * Album sort key: name + artist
     * Song sort key: name + album + artist
     */
    val albums: ConflatedBroadcastChannel<List<Album>> = localAlbums.combineLatest(remoteAlbums.openSubscription()).map { (a, b) ->
        (a + b).sortedWith(ALBUM_COMPARATOR).dedupMergeSorted(
            { a, b -> a.id == b.id },
            { a, b -> a.mergeWith(b) }
        )
    }.replayOne()


//    val songs: Observable<List<Song>> = Observables.combineLatest(
//        remoteAlbums.map { it.flatMap { it.tracks } },
//        _songs, { a, b ->
//            (b + a).sortedWith(SONG_COMPARATOR).filter {
//                it.duration == 0 || (Song.MIN_DURATION <= it.duration && it.duration <= Song.MAX_DURATION)
//            }.dedupMergeSorted(
//                { a, b -> SONG_COMPARATOR.compare(a, b) == 0 },
//                { a, b -> if (a.local != null) a else b }
//            )
//        }).replay(1).autoConnect()

    val songs: ConflatedBroadcastChannel<List<Song>>
        = albums.openSubscription().map {
            it.flatMap { it.tracks }.sortedWith(SONG_COMPARATOR)
        }.replayOne()

    val artists: ConflatedBroadcastChannel<List<Artist>> = albums.openSubscription().map { albums ->
        albums.groupBy { it.id.artist.displayName.toLowerCase() }.values.map { it: List<Album> ->
            val firstAlbum = it.find { it is LocalAlbum } ?: it.first()
//            val artistId = when (firstAlbum.local) {
//                is Album.LocalDetails.Downloaded -> firstAlbum.local.artistId
//                else -> 0
//            }
            // TODO: Use a dynamic sorting method: eg. sort by id, etc.
            Artist(
                firstAlbum.id.artist,
                null,
                it.sortedByDescending { it.year }
            )
        }.sortedWith(ARTIST_COMPARATOR)
    }.replayOne()


    private var updateTask: Deferred<Unit>? = null

//    val playlists: Observable<List<Playlist>> = FileSyncService.instance.playlists.map {
//        it.sortedBy { it.id }
//    }
//    val playlists = BehaviorSubject.createDefault(listOf<Playlist>())

    fun albumsByArtist(id: ArtistId): ReceiveChannel<List<Album>> = artists.openSubscription().map {
        val key = Artist.justForSearch(id.name)
        val artist = it.binarySearchElem(key, ARTIST_COMPARATOR)
//        val artist = it.binarySearchElem(Artist.sortKey(artistName).toLowerCase()) { it.searchKey }
        artist?.albums ?: listOf()
    }

    suspend fun songsOnAlbum(id: AlbumId)
        = findCachedAlbum(id).map { it?.tracks }

//    fun songsOnAlbum(unresolved: Album): Observable<List<Song>> = albums.map {
////        val album = it.getOrNull(it.binarySearch(unresolved, ALBUM_COMPARATOR))
////        album?.tracks ?: run {
////            val cache = cachedAlbums.blockingFirst()
////            cache.getOrNull(cache.binarySearch(unresolved, ALBUM_COMPARATOR))?.tracks
////        } ?: listOf()
//    }
    suspend fun songsOnAlbum(unresolved: Album): ReceiveChannel<List<Song>?>
        = findCachedAlbum(unresolved.id).map { it?.tracks }

//    fun songsOnAlbum(unresolved: Album): Observable<List<Song>> = songs.map { songs ->
//        val key = Song.justForSearch("", unresolved.name, unresolved.artist)
//        val someSongIdx = songs.binarySearch(key, SONG_ALBUM_COMPARATOR)
//        val someSong = songs.getOrNull(someSongIdx) ?: return@map listOf<Song>()
//        val firstIdx = ((someSongIdx..0).find {
//            val song = songs[it]
//            song.album != someSong.album && song.artist != someSong.artist
//        } ?: return@map listOf<Song>()) + 1
//        val endIdx = (someSongIdx until songs.size).find {
//            val song = songs[it]
//            song.album != someSong.album && song.artist != someSong.artist
//        } ?: return@map listOf<Song>()
//
//        songs.subList(firstIdx, endIdx)
//    }

    fun songsByArtist(id: ArtistId): ReceiveChannel<List<Song>>
        = albumsByArtist(id).map { it.flatMap { it.tracks } }

    fun findAlbum(key: AlbumId): ReceiveChannel<Album?> =
        albums.openSubscription().map { libAlbums ->
            libAlbums.binarySearchElem(key) { it.id }
        }

    suspend fun findCachedAlbumNow(album: AlbumId): ReceiveChannel<Album?> = findAlbum(album).switchMap {
        if (it == null) {
            cachedAlbums.openSubscription().map { it.binarySearchElem(album) { it.id } }
        } else produceTask { it }
    }

    suspend fun findCachedAlbum(album: AlbumId): ReceiveChannel<Album?>
        = findAlbum(album).switchMap {
            if (it != null) {
                produce(coroutineContext) { send(it) }
            } else {
                cachedAlbums.openSubscription().map { it.binarySearchElem(album) { it.id } }
            }
        }


    suspend fun findAlbumOfSong(song: Song): ReceiveChannel<Album?> = run {
        // First, look for one with matching artist id, that'll catch the majority of cases
        // So, most searches are as fast as binary search and Various Artists albums only do a few more comparisons
//        val maybeAlbum = findCachedAlbum(song.album, song.artist)
//        maybeAlbum ?:
        findCachedAlbum(song.id.album)
//            ?: albums.blockingFirst().find {
//            // If we don't find that, look for the album that contains the given song
//            it.name.equals(song.album, true) && it.tracks.contains(song)
//        }
    }
    fun findAlbumFuzzy(id: AlbumId): ReceiveChannel<Album?> = artists.openSubscription().map {
        it.find {
            FuzzySearch.ratio(it.id.name, id.artist.name) >= 88
        }?.albums?.find {
            FuzzySearch.ratio(it.id.displayName, id.displayName) >= 88
        }
    }

    fun findSong(id: SongId): ReceiveChannel<Song?> = songs.openSubscription().map {
        val key = Song(null, null, id, 1, 1, 0)
        it.binarySearchElem(key, SONG_COMPARATOR)
    }

    fun findSongFuzzy(song: Song): ReceiveChannel<Song?> = songs.openSubscription().map {
//        if (song.id.album.displayName.isNotBlank()) {
            given(it.binarySearchNearestElem(song, SONG_COMPARATOR)) {
                it provided {
                    Math.abs(it.duration - song.duration) <= 10_000
                        && it.id.fuzzyEquals(song.id)
                }
            }
//        } else {
//            // sigh, do a search without album...
//            // this is always from outside queries, so not super duper common.
//
//        }
    }

    fun findArtist(id: ArtistId): ReceiveChannel<Artist?> = artists.openSubscription().map {
        val key = Artist(id, null, listOf())
        it.binarySearchElem(key, ARTIST_COMPARATOR)
//        it.binarySearchElem(Artist.sortKey(id).toLowerCase()) { it.searchKey }
    }

//    fun findArtistFuzzy(name: String): Artist? = artists.blockingFirst().let {
//        it.find { FuzzySearch.ratio(it.id.name, name) >= 88 }
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
            given(it?.artworkUri) {
                req.load(it).apply(ARTWORK_OPTIONS)
            }
        }

    fun loadArtistImage(req: RequestManager, id: ArtistId): ReceiveChannel<RequestBuilder<Drawable>?> =
        findArtistExtras(id).map {
            given(it?.artworkUri) {
                req.load(it).apply(ARTWORK_OPTIONS)
            }
        }



    fun addAlbumExtras(meta: AlbumMetadata) {
        UserPrefs.albumMeta appends meta
    }

//    fun addPlaylist(pl: Playlist) {
//        UserPrefs.playlists appends pl
////        async(CommonPool) {
////            FileSyncService.instance.uploadPlaylist(pl)
////        }
//    }

//    fun deletePlaylist(pl: Playlist) {
//        val all = UserPrefs.playlists.value.toList()
//        UserPrefs.playlists puts all.without(all.indexOf(pl)).toTypedArray()
////        async(CommonPool) {
////            FileSyncService.instance.deletePlaylist(pl)
////        }
//    }

//    fun findPlaylist(name: String): Playlist? {
//        async(UI) { println("playlist: looking for $name") }
//        return playlists.blockingFirst().binarySearchElem(name) { it.id }
////        return UserPrefs.playlists.value.find { it.id == name }
//    }

//    fun addToPlaylist(name: String, tracks: List<Song>) {
//        val playlists = UserPrefs.playlists.value
//        val idx = playlists.indexOfFirst { it.id == name }
//        val pl = playlists.getOrNull(idx) ?: return
//        playlists.withReplaced(idx, pl.copy(songs = pl.songs + tracks))
//    }

    private fun fillMissingArtwork() = task {
        val albums = albums.value
        val cache = albumCovers.value
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
//                    UserPrefs.albumMeta appends AlbumMetadata(album.id, art)
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

    private fun fillArtistArtwork() = task {
        val cache = artistMeta.value
        artists.value.forEach { artist ->
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
//                "artist" to artist.id.name
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

            if (res.has("artist")) {
                val uri = tryOr(null) { res["artist"]["image"][3]["#text"].string }
                if (uri != null && uri.isNotEmpty()) {
                    task {
                        synchronized(UserPrefs.artistMeta) {
                            UserPrefs.artistMeta appends key.copy(artworkUri = uri)
                        }
                    }.success(UI) {
                        Glide.with(App.instance)
                            .load(uri)
//                                .apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(key)))
                            .preload()
                    }
                } else {
                    UserPrefs.artistMeta appends key
                }
            } else {
                UserPrefs.artistMeta appends key
            }

            delay(50)
        }
    }

    fun addRemoteAlbum(album: Album) = task {
        val existing = findAlbum(album.id).first()
        if (existing != null) {
            return@task
        }

        if (album.tracks.isEmpty()) {
            given(findCachedRemoteAlbum(album).first()) {
                UserPrefs.remoteAlbums appends it
            }
        } else {
            UserPrefs.remoteAlbums appends album
        }
//        UserPrefs.remoteAlbumsFile.save()
    }

    fun removeRemoteAlbum(album: Album) = task {
        val all = UserPrefs.remoteAlbums.value
        val idx = all.indexOfFirst { it.id == album.id }
        if (idx != -1) {
            UserPrefs.remoteAlbums puts all.without(idx)
//            UserPrefs.remoteAlbumsFile.save()
        }
    }

    /**
     * Caches the given remote album for quick future viewing and playback,
     * but does _not_ add it to the user's library.
     * The cache holds a limited history of albums that's cleared when the app is restarted.
     */
    fun cacheRemoteAlbum(album: Album) = task {
        val cache = _cachedAlbums.value.toMutableList()
        if (cache.size >= REMOTE_ALBUM_CACHE_LIMIT) {
            cache.removeAt(0)
        }
        cache.add(album)
        _cachedAlbums puts cache
    }

    fun findCachedRemoteAlbum(album: Album): ReceiveChannel<Album?>
        = cachedAlbums.openSubscription().map {
            it.binarySearchElem(album, ALBUM_COMPARATOR)
        }

    fun cacheRemoteArtist(artist: Artist) = task {
        val cache = _cachedArtists.value.toMutableList()
        if (cache.size >= REMOTE_ARTIST_CACHE_LIMIT) {
            cache.removeAt(0)
        }
        cache.add(artist)
        _cachedArtists puts cache
    }

    fun findCachedRemoteArtist(artist: Artist): ReceiveChannel<Artist?>
        = cachedArtists.openSubscription().map {
            it.binarySearchElem(artist, ARTIST_COMPARATOR)
        }

    fun findPlaylist(id: UUID): ReceiveChannel<Playlist?>
        = UserPrefs.playlists.openSubscription().flatMap {
            val r = it.find { it.id == id }
                ?: UserPrefs.recommendations.value.mapNotNull { it as? Playlist }.find { it.id == id }

            if (r != null) {
                produceTask { r }
            } else findCachedPlaylist(id)
        }

    fun findCachedPlaylist(id: UUID): ReceiveChannel<Playlist?>
        = cachedPlaylists.openSubscription().map {
            it.find { it.id == id }
        }

    fun addPlaylist(pl: Playlist) {
        task { UserPrefs.playlists appends pl }
    }
    fun cachePlaylist(pl: Playlist) {
        task { cachedPlaylists appends pl }
    }

//    fun loadArtistImage(req: RequestManager, id: Long) =

    fun initData() {
        if (!initialized) {
            initialized = true

            // Load all the songs from the MediaStore
            task {
                updateSongs()
            }.then {
                updateLocalArtwork()
            }.then {
                fillMissingArtwork()
                fillArtistArtwork()
            }


            // Change events for mediaStore
            // On change, empty _all and refill with songs
            contentResolver.registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
                object : ContentObserver(Handler()) {
                    override fun onChange(selfChange: Boolean) {
                        println("songs: External Media has been added")
                        // Update the song library, but to accomodate rapid changes, wait 2 seconds
                        // before re-querying the MediaStore.
                        given(updateTask) {
                            if (it.isActive) it.cancel()
                        }
                        updateTask = task {
                            delay(1500)
                            updateSongs()
                        }
                        super.onChange(selfChange)
                    }
                }
            )
            contentResolver.registerContentObserver(android.provider.MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true,
                object : ContentObserver(Handler()) {
                    override fun onChange(selfChange: Boolean) {
                        println("songs: Internal Media has been added")
                        super.onChange(selfChange)
                    }
                }
            )
        }

    }

    private fun compileSongsFrom(uri: Uri): List<Song> {
        val songs = ArrayList<Song>()
        val cur = App.instance.contentResolver.query(
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
            error("lopc: Cursor failed to load.")
        } else if (!cur.moveToFirst()) {
            error("lopc: No music")
        } else {
            do {
                try {
                    val duration = cur.intValue(MediaStore.Audio.Media.DURATION)

                    if (duration == 0 || (Song.MIN_DURATION <= duration && duration <= Song.MAX_DURATION)) {
                        var artist = cur.stringValue(MediaStore.Audio.Media.ARTIST)
                        val albumArtist = tryOr(artist) {
                            cur.stringValue("album_artist")
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
                        val year = cur.intValue(MediaStore.Audio.Media.YEAR).provided { it > 0 }

                        val (disc, track) = if (index >= 1000) {
                            // There may be multiple discs
                            index / 1000 to index % 1000
                        } else {
                            1 to index
                        }

                        songs.add(Song(
                            Song.LocalDetails.Downloaded(data, id, albumId, artistId),
                            null,
                            SongId(title, album, albumArtist, artist),
                            track, disc, duration,
                            year
                        ))
                    }
                } catch (e: Exception) {
                    Log.trace("Issues", e)
                }
            } while (cur.moveToNext())
        }
        cur.close()
        return songs
    }

    private suspend fun updateSongs() {
        // Load the song library here
        val external = compileSongsFrom(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
//        val internal = task { compileSongsFrom(MediaStore.Audio.Media.INTERNAL_CONTENT_URI) }
//        val songs = external.await() //+ internal.await()
        synchronized(this) {
            this@Library._songs.send(external)
        }
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
//                    val id = cur.longValue(MediaStore.Audio.Albums._ID)
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
        val albums = albums.value

        val imageExts = arrayOf("jpg", "jpeg", "gif", "png")
        val frontReg = Regex("\\b(front|cover|folder|album|booklet)\\b", RegexOption.IGNORE_CASE)
        val existingCovers = albumCovers.value
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
            val local = firstTrack.local as Song.LocalDetails.Downloaded
            val folder = File(local.path).parentFile
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
}

val Context.library get() = Library.instance