package com.loafofpiecrust.turntable.repository.remote

import com.github.ajalt.timberkt.Timber
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.RemoteArtist
import com.loafofpiecrust.turntable.model.song.RemoteSongId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.awaitOr
import com.loafofpiecrust.turntable.util.http
import com.loafofpiecrust.turntable.util.parameters
import io.ktor.client.request.get
import io.ktor.client.request.url
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import org.jsoup.Jsoup

object MusicBrainz: Repository {
    override val displayName: Int
        get() = R.string.search_musicbrainz

    @Parcelize
    data class AlbumDetails(
        val id: String,
        val artistId: String? = null,
        override val thumbnailUrl: String? = null,
        val listeners: Int? = null
    ): Album.RemoteDetails {
        override val artworkUrl: String? get() = null
        override suspend fun resolveTracks(album: AlbumId): List<Song>
            = tracksOnAlbum(id) ?: listOf()
    }

    data class ArtistDetails(
        val id: String
    ): RemoteArtist.Details {
        override val thumbnailUrl: String? get() = null
        override val albums: List<Album> by lazy {
            runBlocking { resolveAlbums(id) } // TODO: implement
        }
        override val biography: String get() = ""
    }


    override suspend fun searchAlbums(query: String): List<Album> = coroutineScope {
        val res = http.get<JsonObject> {
            url("http://musicbrainz.org/ws/2/release-group")
            parameters(
                "fmt" to "json",
                "query" to query
            )
        }

        val entries = res["release-groups"].array
        entries.map { it.obj }.parMap {
            val name = it["name"].string
            val artistName = it["artist-credit"][0]["artist"]["name"].string
            val mbid = it["id"].string
            val task = async {
                // Check for potential release year here. Maybe grab tracks too
                val res = http.get<JsonObject> {
                    url("http://ws.audioscrobbler.com/2.0")
                    parameters(
                        "api_key" to BuildConfig.LASTFM_API_KEY,
                        "format" to "json",
                        "method" to "album.getinfo",
                        "album" to name,
                        "artist" to artistName
                    )
                }["album"]

                val images = res["image"].nullArray

                AlbumDetails(
                    mbid,
                    thumbnailUrl = images?.let { it[2]["#text"].nullString },
                    listeners = res["listeners"].string.toInt()
                ) to images?.let { it.last()["#text"].nullString }
            }


            val typeStr = it["primary-type"].nullString
            val secondaryTypes = it["secondary-types"].nullArray?.map { it.string }
            val type = when {
                secondaryTypes?.contains("Compilation") == true -> Album.Type.COMPILATION
                secondaryTypes?.contains("Live") == true -> Album.Type.LIVE
                else -> when (typeStr) {
                    "Album" -> Album.Type.LP
                    "EP" -> Album.Type.EP
                    "Single" -> Album.Type.SINGLE
                    "Other" -> Album.Type.OTHER
                    "Live" -> Album.Type.LIVE
                    else -> Album.Type.LP
                }
            }

            val details = task.awaitOr(null)
            RemoteAlbum(
                AlbumId(name, ArtistId(artistName)).also { id ->
                    Library.addAlbumExtras(
                        Library.AlbumMetadata(id, details?.second)
                    )
                },
                details?.first ?: AlbumDetails(mbid),
                type = type
            ) //to it["score"].string.toInt()
        }.awaitAllNotNull()
    }


    private suspend fun tracksOnAlbum(rgid: String): List<Song>? {

        var albumRes: JsonObject
        var attempts = 0
        do {
            if (attempts > 0) {
                delay(600)
            }
            albumRes = http.get("http://musicbrainz.org/ws/2/release") {
                parameters(
                    "fmt" to "json",
                    "release-group" to rgid
//                "status" to "official"
                )
            }

            attempts += 1
        } while (albumRes["releases"] == null && attempts < 5)

//            async(UI) { println("album: songs req got ${}") }

        val releases = albumRes["releases"].array
        // Just use the first release for now

        val firstRelease = (if (releases.size() <= 1) {
            releases.first()
        } else {
            releases.minBy {
                try {
                    val disamb = it["disambiguation"].nullString
                    val name = it["title"].nullString
                    val isDeluxe = name?.contains(AlbumId.SIMPLE_EDITION) == true || disamb?.contains(AlbumId.SIMPLE_EDITION) == true
                    val datePat = Regex("(\\d+)-?(\\d+)?-?(\\d+)?$")
                    var score = it["date"].nullString?.let { s ->
                        val mat = datePat.find(s)!!
                        val groups = mat.groupValues
                        val year = groups[1].toInt() * 10000
                        if (groups.size > 2) {
                            val month = groups[2].toInt() * 100
                            val day = groups[3].toInt()
                            year + month + day
                        } else year + (12_00) + 32
                    } ?: 9999_99_99

                    if (isDeluxe) {
                        score -= 1_00_00
                    }
                    score
                } catch (e: Exception) {
                    9999_99_99
                }
            }
        })!!.obj

        val reid = firstRelease["id"].string

//            task(UI) { println("album: picked release $reid") }

        // We don't have this albums' song info, so retrieve it from Last.FM
        val res = http.get<JsonObject> {
            url("http://musicbrainz.org/ws/2/release/$reid")
            parameters(
                "fmt" to "json",
                "inc" to "recordings+artist-credits"
            )
        }
//        async(UI) { println("album release '${reid}'") }
        val albumId = AlbumId(
            res["title"].string,
            ArtistId(res["artist-credit"][0]["name"].string)
        )
        val year = res["date"].nullString?.take(4)?.toInt()

        val discs = res["media"].array

        return discs.map { it.obj }.mapIndexed { discIdx, disc ->
            val recordings = disc["tracks"].array
            recordings.map { it.obj }.mapIndexed { idx, recording ->
                try {
                    val title = recording["title"].string
                    //                                if (tracks.find { it.name == name } != null) {
                    //                                    // Skip duplicate songs, keep only the first one.
                    //                                    return@forEach
                    //                                }
//                async(UI) { println("album track '${name}'") }

//                    RemoteSong(

                    val artistName = recording["artist-credit"]?.get(0)?.get("name")?.string
                    Song(
                        SongId(
                            title,
                            albumId,
                            artistName?.let { ArtistId(it) } ?: albumId.artist
                        ),
                        duration = recording["length"]?.int ?: 0,
                        track = recording["position"]?.int ?: idx+1,
                        disc = discIdx + 1,
                        year = year ?: 0,
                        platformId = RemoteSongId(
                            recording["id"]?.string,
                            rgid,
                            null
                        )
                    )

//                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    null
                }
            }.filterNotNull()
        }.flatten().sortedBy { it.discTrack }
    }

    override suspend fun searchArtists(query: String): List<Artist> {
        val entries = http.get<JsonObject> {
            url("http://musicbrainz.org/ws/2/artist")
            parameters(
                "fmt" to "json",
                "query" to query
            )
        }["artists"].array

        return entries.map { it.obj }.mapNotNull {
            val name = it["name"].string
            val sortName = it["sort-name"].string.split(',', limit=2)
                .map { it.trim() }
                .asReversed()
                .joinToString(" ")
            val mbid = it["id"].string

            val lfmRes = http.get<JsonObject> {
                url("http://ws.audioscrobbler.com/2.0")
                parameters(
                    "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                    "method" to "artist.getInfo",
                    "mbid" to mbid
                )
            }
            val thumbnail = try {
                lfmRes["artist"]["image"][2]["#text"].string
            } catch (e: Exception) {
                null
            }

            val disamb = it["disambiguation"].nullString
            val lifeSpan = it["life-span"].obj
            val score = it["score"].string.toInt()

            if (score > 0) {
                RemoteArtist(
                    ArtistId(name, sortName.provided { !it.equals(name, true) }),
                    ArtistDetails(mbid),
//                    thumbnail,
                    startYear = lifeSpan["begin"].nullString?.take(4)?.toInt(),
                    endYear = lifeSpan["end"].nullString?.take(4)?.toInt()
                ) //to score
            } else null
        }
    }


    override suspend fun searchSongs(query: String): List<Song> {
        return http.get<JsonObject> {
            url("http://musicbrainz.org/ws/2/recording")
            parameters(
                "fmt" to "json",
                "query" to query
            )
        }["recordings"].array.map {
            val albumObj = it["releases"][0]
            val artistName = it["artist-credit"][0]["artist"]["name"].string
            val artistId = ArtistId(artistName)
            Song(
                SongId(
                    it["title"].string,
                    AlbumId(albumObj["title"].string, artistId)
                ),
                track = albumObj["media"][0]["track"][0]["number"].string.toInt(),
                disc = 1,
                duration = it["length"].int,
                year = 0
            )
        }
    }

    suspend fun resolveAlbums(artistId: String): List<Album> = tryOr(listOf()) {
//        val mbid = /*this.mbid ?:*/ findMbid() ?: return@then

        val res = Jsoup.parse(
            http.get<String>("https://musicbrainz.org/artist/$artistId")
        )
        Timber.d { "album: loading $artistId" }

        val artistName = res.getElementsByClass("artistheader")[0]
            .child(0).child(0).text()

        val discography = res.getElementById("content").getElementsByAttributeValue("method", "post")[0]
        val sections = discography.children()
//        val remoteAlbums = mutableListOf<Album>()

//        Library.cacheRemoteArtist(
//            RemoteArtist(
//                ArtistId(artistName),
//                ArtistDetails(artistId),
//                remoteAlbums
//            )
//        )

        return (0 until sections.size step 2).parMap { idx ->
            //                task {
            val typeStr = sections[idx].text()
            val type = when {
                typeStr.contains("Live") -> Album.Type.LIVE
                typeStr.contains("Compilation") -> Album.Type.COMPILATION
                typeStr.startsWith("Album") -> Album.Type.LP
                typeStr.startsWith("Single") -> Album.Type.SINGLE
                typeStr.startsWith("EP") -> Album.Type.EP
                typeStr.startsWith("Other") -> Album.Type.OTHER
                else -> Album.Type.LP
            }

            Timber.d { "album: release type $type" }
            val items = sections[idx + 1].child(1)
            items.children().map {
                val titleLink = it.child(1).child(0)
                val title = titleLink.text()


                val year = it.child(0).text()?.toIntOrNull() ?: 0
                val albumId = titleLink.attr("href").split("/").last()


                val (remote, cover) = try {
                    val res = http.get<JsonObject> {
                        url("http://ws.audioscrobbler.com/2.0")
                        parameters(
                            "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                            "method" to "album.getinfo",
                            "album" to title,
                            "artist" to artistName//this.uuid.name
                        )
                    }["album"]
                    val images = res["image"].nullArray
                    AlbumDetails(
                        albumId,
                        artistId,
                        thumbnailUrl = images?.get(2)?.get("#text").nullString,
                        listeners = res["listeners"].string.toInt()
                    ) to images?.last()?.get("#text").nullString
                } catch (e: Exception) {
                    AlbumDetails(albumId, artistId) to null
                }

//                        val (remote, cover) = Album.coverArtFor(albumId).let { imgUrls ->
//                            Album.Details(
//                                albumId,
//                                this@Artist.mbid,
//                                imgUrls?.get(0),
//                                type = Album.Details.Type.MUSICBRAINZ
//                            ) to imgUrls?.get(1)
//                        }

                val album = RemoteAlbum(
                    AlbumId(title, ArtistId(artistName)).also { id ->
                        Library.addAlbumExtras(
                            Library.AlbumMetadata(id, cover)
                        )
                    },
                    remote,
                    year = year,
                    type = type
                )

                Timber.d { "album: added '$title'!!" }

                album
            }
        }.awaitAll().flatten()
    }


    override suspend fun find(album: AlbumId): Album? = try {
        val res = http.get<JsonObject> {
            url("https://musicbrainz.org/ws/2/release-group")
            parameters(
                "fmt" to "json",
                "query" to "releasegroup:\"${album.displayName}\" AND artist:\"${album.artist.name}\"",
                "limit" to "2"
            )
        }

        if (res["count"].int > 0) {
            val rg = res["release-groups"][0]
            val score = rg["score"].string.toInt()
            if (score >= 95) {
                RemoteAlbum(album, AlbumDetails(rg["id"].string))
            } else null
        } else null
    } catch (e: Exception) {
        Timber.e(e)
        null
    }


    override suspend fun find(artist: ArtistId): Artist? {
        return null
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? {
        return null
    }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? = null
}