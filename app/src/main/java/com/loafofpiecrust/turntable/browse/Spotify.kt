package com.loafofpiecrust.turntable.browse

import android.content.Context
import android.util.Base64
import com.github.salomonbrys.kotson.*
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.RemoteArtist
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.playlist.PlaylistDetailsFragment
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.lazy
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


object Spotify: SearchApi, AnkoLogger {
    @Parcelize
    data class AlbumDetails(
        val id: String,
        override val thumbnailUrl: String? = null,
        override val artworkUrl: String? = null
    ): Album.RemoteDetails {
        /// TODO: Pagination
        override suspend fun resolveTracks(album: AlbumId): List<Song> {
            return apiRequest(
                "albums/$id/tracks",
                mapOf("limit" to "50")
            ).gson["items"].array.map {
//                RemoteSong(
                Song(
                    SongId(
                        it["name"].string,
                        album,
                        ArtistId(it["artists"][0]["name"].string)
                    ),
                    track = it["track_number"].int,
                    disc = it["disc_number"].int,
                    duration = it["duration_ms"].int,
                    year = null
                )
//                )
            }
        }

        suspend fun fullArtwork(): String? {
            if (artworkUrl != null) return artworkUrl

            val res = Http.get("https://api.spotify.com/v1/albums/$id", headers = mapOf(
                "Authorization" to "Bearer $accessToken"
            )).gson

            return res["images"][0]["url"].nullString
        }
    }

    @Parcelize
    data class ArtistDetails(
        val id: String,
        val thumbnailUrl: String? = null,
        val artworkUrl: String? = null
    ): RemoteArtist.Details {
        /// TODO: Pagination
        override val albums: List<Album> by lazy { runBlocking {
            apiRequest(
                "artists/$id/albums",
                mapOf("limit" to "50")
            ).gson["items"].array.map {
                val imgs = it["images"].array
                val artists = it["artists"].array
                RemoteAlbum(
                    AlbumId(
                        it["name"].string,
                        ArtistId(artists[0]["name"].string)
                    ),
                    AlbumDetails(
                        it["id"].string,
                        tryOr(null) { imgs.last()["url"].string },
                        tryOr(null) { imgs.first()["url"].string }
                    ),
                    year = it["release_date"].nullString?.take(4)?.toInt(),
                    type = when (it["album_type"].string) {
                        "single" -> Album.Type.SINGLE
                        "compilation" -> Album.Type.COMPILATION
                        else -> Album.Type.LP
                    }
                )
            }
        } }
        override val biography: String
            get() = ""

        suspend fun fullArtwork(search: Boolean): String? {
            if (artworkUrl != null) return artworkUrl

            val res = Http.get("https://api.spotify.com/v1/artists/$id", headers = mapOf(
                "Authorization" to "Bearer $accessToken"
            )).gson

            return tryOr(null) { res["images"][0]["url"].string }
        }
    }

    private const val clientId = BuildConfig.SPOTIFY_ID
    private const val clientSecret = BuildConfig.SPOTIFY_SECRET

    private var accessToken: String? = null
        get() {
            synchronized(this) {
                val now = System.currentTimeMillis()
                if (field == null || (now - tokenTimestamp) > tokenLifespan) {
                    runBlocking { login() }
                }
            }
            return field
        }
    private var tokenTimestamp: Long = 0
    private var tokenLifespan: Long = 3600000

    private suspend fun apiRequest(url: String, params: Map<String, String> = mapOf()) =
        Http.get(
            "https://api.spotify.com/v1/$url",
            params = params,
            headers = mapOf("Authorization" to "Bearer $accessToken")
        )

    private suspend fun login() {
        val authKey = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        debug { "recs: key = $authKey" }
        try {
            val res = Http.post("https://accounts.spotify.com/api/token",
                body = mapOf("grant_type" to "client_credentials"),
                headers = mapOf("Authorization" to "Basic $authKey")
            ).gson
//            task(UI) { println("recs: res = ${t.text}") }
//            val res = JsonParser().parse(t.text)
            accessToken = res["access_token"].nullString
            debug { "recs: token = $accessToken" }
            tokenTimestamp = System.currentTimeMillis()
            tokenLifespan = res["expires_in"].long * 1000
        } catch (e: Throwable) {
            error("Spotify failed to login", e)
        }
    }

    private suspend fun searchFor(artist: ArtistId): List<String> {
        val res = apiRequest("search", params = mapOf(
            "q" to "artist:\"${artist.displayName}\"",
            "type" to "artist",
            "limit" to "3"
        )).gson

        return res["artists"]["items"].nullArray?.map { it["id"].string } ?: listOf()
    }

    override suspend fun searchArtists(query: String): List<Artist> {
        val res = apiRequest("search", mapOf(
            "q" to query,
            "type" to "artist"
        )).gson

        return res["artists"]["items"].array.map {
            val imgs = it["images"].array
            RemoteArtist(
                ArtistId(it["name"].string),
                ArtistDetails(
                    it["id"].string,
                    imgs.last()["url"].string,
                    imgs.first()["url"].string
                )
            )
        }
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return given(searchFor(artist).firstOrNull()) {
            RemoteArtist(artist, ArtistDetails(it))
        }
    }

    private suspend fun searchFor(song: SongId): List<String> {
        val res = Http.get("search", headers = mapOf(
            "Authorization" to "Bearer $accessToken"
        ), params = mapOf(
            "q" to "track:\"${song.name}\" artist:\"${song.artist}\" album:\"${song.album.name}\"",
            "type" to "track",
            "limit" to "3"
        )).gson

        return res["tracks"]["items"].array.map { it["id"].string }
    }

    private suspend fun searchFor(album: AlbumId): List<RemoteAlbum> {
        val res = Http.get("search", headers = mapOf(
            "Authorization" to "Bearer $accessToken"
        ), params = mapOf(
            "q" to "album:\"${album.name}\" artist:\"${album.artist.name}\"",
            "type" to "album",
            "limit" to "3"
        )).gson

        return res["albums"]["items"].nullArray?.map {
            val artists = it["artists"].array
            val primaryArtist = artists.first()["name"].string
            RemoteAlbum(
                AlbumId(it["name"].string, ArtistId(primaryArtist)),
                AlbumDetails(it["id"].string)
            )
        } ?: listOf()
    }

    override suspend fun find(album: AlbumId): Album? {
        return given(searchFor(album).firstOrNull()) {
            it
        }
    }

    override suspend fun searchAlbums(query: String): List<Album> {
        val res = apiRequest("search", mapOf(
            "q" to query,
            "type" to "album",
            "limit" to "3"
        )).gson

        return res["albums"]["items"].array.map {
            val imgs = it["images"].array
            RemoteAlbum(
                AlbumId(
                    it["name"].string,
                    ArtistId(it["artists"][0]["name"].string)
                ),
                AlbumDetails(
                    it["id"].string,
                    imgs.last()["url"].string,
                    imgs.first()["url"].string
                ),
                type = when (it["album_type"].string) {
                    "single" -> Album.Type.SINGLE
                    "compilation" -> Album.Type.COMPILATION
                    else -> Album.Type.LP
                }
            )
        }
    }

    override suspend fun searchSongs(query: String): List<Song> {
        return apiRequest(
            "search",
            mapOf(
                "q" to query,
                "type" to "track"
            )
        ).gson["tracks"]["items"].array.map {
//            RemoteSong(
            Song(
                SongId(
                    it["name"].string,
                    it["album"]["name"].string,
                    it["album"]["artists"][0]["name"].string,
                    it["artists"][0]["name"].string
                ),
                track = it["track_number"].int,
                disc = it["disc_number"].int,
                duration = it["duration_ms"].int,
                year = null
            )
//            )
        }
    }

    private suspend fun recommendationsFor(params: Map<String, String>): List<Song> {

//        task(UI) { println("recs: artists = $artists") }
        val res = apiRequest("recommendations", params).gson
//        task(UI) { println("recs: $res") }

        val tracks = res["tracks"].array
        return tracks.map { it.obj }.map {
//            RemoteSong(
            Song(
                SongId(
                    it["name"].string,
                    tryOr("") { it["album"]["name"].string },
                    tryOr("") { it["album"]["artists"][0]["name"].string }
                ),
                track = it["track_number"].int,
                disc = it["disc_number"].nullInt ?: 1,
                duration = it["duration_ms"].int,
                year = null
            )
//            )

        }
    }

    private suspend fun recommendationsFor(
        artists: List<ArtistId> = listOf(),
        albums: List<AlbumId> = listOf(),
        songs: List<SongId> = listOf()
    ): List<Song> {
        login()

        val params = mutableMapOf<String, String>()
        artists.take(10).map { searchFor(it) }
            .mapNotNull { it.firstOrNull() }
            .take(5)
            .also { if (it.isNotEmpty()) {
                params["seed_artists"] = it.joinToString(",")
            } }

        songs.take(10).map { searchFor(it) }
            .mapNotNull { it.firstOrNull() }
            .take(5)
            .also { if (it.isNotEmpty()) {
                params["seed_tracks"] = it.joinToString(",")
            } }

        albums.take(10).map { searchFor(it) }
            .mapNotNull { it.firstOrNull() }
            .take(5)
            .also { if (it.isNotEmpty()) {
                params["seed_albums"] = it.joinToString(",")
            } }

        return if (params.isNotEmpty()) {
            recommendationsFor(params)
        } else listOf()
    }

    suspend fun recommendationsFor(seed: List<Music>): List<Song> {
        return recommendationsFor(
            artists = seed.mapNotNull { (it as? Artist)?.id },
            albums = seed.mapNotNull { (it as? Album)?.id },
            songs = seed.mapNotNull { (it as? Song)?.id }
        )
    }

    suspend fun openRecommendationsPlaylist(
        ctx: Context,
        artists: List<ArtistId> = listOf(),
        albums: List<AlbumId> = listOf(),
        songs: List<SongId> = listOf()
    ) {
        val newPl = CollaborativePlaylist()
        val recs = Spotify.recommendationsFor(
            artists, albums, songs
        )
        recs.forEach { newPl.add(it) }
        ctx.library.cachePlaylist(newPl)

        App.launch {
            ctx.replaceMainContent(
                PlaylistDetailsFragment.newInstance(newPl.id, newPl.name),
                true
            )
        }
    }

    suspend fun similarTo(artist: ArtistId): List<Artist> {
        val a = searchFor(artist).firstOrNull() ?: return listOf()
        val res = apiRequest("artists/$a/related-artists").gson

        return res["artists"].array.map { it.obj }.map {
            RemoteArtist(
                ArtistId(it["name"].string),
                ArtistDetails(it["id"].string, tryOr(null) { it["images"][1]["url"].string })
            )
        }
    }

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    data class Playlist(
        val name: String,
        val ownerName: String,
        val items: List<Item>
    ) {
        data class Item(
            val addedAt: Date,
            val track: Song
        )
    }
    suspend fun getPlaylist(userId: String, playlistId: String, page: Int = 0): Spotify.Playlist {
        val res = apiRequest(
            "users/$userId/playlists/$playlistId",
            mapOf(
                "limit" to "100",
                "offset" to (page*100).toString(),
                "fields" to "name,owner.display_name,tracks.items(added_at,track(track_number,disc_number,duration_ms,name,album(name,type,release_date),artists(name)))"
            )
        ).gson

        val items = res["tracks"]["items"].array.map {
            val track = it["track"].obj
            Playlist.Item(
                addedAt = DATE_FORMAT.parse(it["added_at"].string),
                track = Song(
                    SongId(
                        track["name"].string,
                        track["album"]["name"].string,
                        track["artists"][0]["name"].string
                    ),
                    track = track["track_number"].int,
                    disc = track["disc_number"].int,
                    duration = track["duration_ms"].nullInt ?: 0,
                    year = track["album"]["release_date"].nullString?.take(4)?.toInt()
                )
            )
        }
        val finalTracks = if (items.size == 100) {
            items + getPlaylist(userId, playlistId, page + 1).items
        } else items

        return Spotify.Playlist(
            name = res["name"].string,
            ownerName = res["owner"]["display_name"].string,
            items = finalTracks
        )
    }

    data class PartialPlaylist(
        val id: String,
        val name: String,
        val ownerName: String,
        val trackCount: Int
    )
    suspend fun playlistsByUser(userId: String, page: Int = 0): List<PartialPlaylist> {
        val res = apiRequest(
            "users/$userId/playlists",
            mapOf(
                "limit" to "50",
                "offset" to (page*50).toString()
            )
        ).use { it.gson }

        return res["items"].array.map {
            val obj = it.obj
            PartialPlaylist(
                id = obj["id"].string,
                name = obj["name"].string,
                ownerName = obj["owner"]["display_name"].string,
                trackCount = obj["tracks"]["total"].int
            )
        }
    }


    override suspend fun fullArtwork(album: Album, search: Boolean): String? {
        if (album is RemoteAlbum) {
            val remote = album.remoteId
            if (remote is AlbumDetails) {
                return remote.fullArtwork()
            }
        }

        val res = Http.get("search", headers = mapOf(
            "Authorization" to "Bearer $accessToken"
        ), params = mapOf(
            "q" to "album:\"${album.id.name}\" artist:\"${album.id.artist.name}\"",
            "type" to "album",
            "limit" to "3"
        )).gson

        return res["albums"]["items"].nullArray?.lazy?.map { it["images"][0]["url"].nullString }?.first()
    }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? {
        return ((artist as? RemoteArtist)?.details as? ArtistDetails)?.fullArtwork(search)
    }
}