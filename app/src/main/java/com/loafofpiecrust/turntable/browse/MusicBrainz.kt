package com.loafofpiecrust.turntable.browse

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.album.RemoteAlbum
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.artist.RemoteArtist
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.song.RemoteSong
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.song.SongInfo
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.task
import com.loafofpiecrust.turntable.util.text
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import org.jsoup.Jsoup
import java.util.regex.Pattern

object MusicBrainz: SearchApi, AnkoLogger {
    @Parcelize
    data class AlbumDetails(
        val id: String,
        val artistId: String? = null,
        override val thumbnailUrl: String? = null,
        val listeners: Int? = null
    ): Album.RemoteDetails() {
        override suspend fun resolveTracks(album: AlbumId): List<Song>
            = tracksOnAlbum(id) ?: listOf()
    }

    @Parcelize
    data class ArtistDetails(
        val id: String
    ): RemoteArtist.Details {
        override val albums: List<Album> by lazy {
            runBlocking { resolveAlbums(id) } // TODO: implement
        }
        override val biography: String get() = ""
    }


    override suspend fun searchAlbums(query: String): List<Album> {
        val res = Http.get("http://musicbrainz.org/ws/2/release-group", params = mapOf(
            "fmt" to "json",
            "query" to query
        )).gson

        val entries = res["release-groups"].array
        return entries.map { it.obj }.parMap {
            val name = it["name"].string
            val artistName = it["artist-credit"][0]["artist"]["name"].string
            val mbid = it["id"].string
            val task = task {
                tryOr(null) {
                    // Check for potential release year here. Maybe grab tracks too
                    val res = Http.get("http://ws.audioscrobbler.com/2.0", params = mapOf(
                        "api_key" to BuildConfig.LASTFM_API_KEY,
                        "format" to "json",
                        "method" to "album.getinfo",
                        "album" to name,
                        "artist" to artistName
                    )).gson["album"]
                    val images = res["image"].nullArray
                    AlbumDetails(
                        mbid,
                        thumbnailUrl = images?.let { it[2]["#text"].nullString },
                        listeners = res["listeners"].string.toInt()
                    ) to images?.let { it.last()["#text"].nullString }
                }
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

            val details = task.await()
            RemoteAlbum(
                ArtistId(artistName).forAlbum(name).also { id ->
                    Library.instance.addAlbumExtras(
                        Library.AlbumMetadata(id, details?.second)
                    )
                },
                details?.first ?: AlbumDetails(mbid),
                type = type,
                year = null
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
            albumRes = Http.get("http://musicbrainz.org/ws/2/release", params = mapOf(
                "fmt" to "json",
                "release-group" to rgid
//                "status" to "official"
            )).gson.obj

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
                    val isDeluxe = name?.contains(AlbumId.SIMPLE_EDITION_PAT) == true || disamb?.contains(AlbumId.SIMPLE_EDITION_PAT) == true
                    val datePat = Pattern.compile("(\\d+)-?(\\d+)?-?(\\d+)?$")
                    var score = given(it["date"].nullString) { s ->
                        val mat = datePat.matcher(s)
                        mat.find()
                        val year = mat.group(1).toInt() * 10000
                        if (mat.groupCount() > 1) {
                            val month = mat.group(2).toInt() * 100
                            val day = mat.group(3).toInt()
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
        val res = Http.get("http://musicbrainz.org/ws/2/release/$reid", params = mapOf(
            "fmt" to "json",
            "inc" to "recordings+artist-credits"
        )).gson
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

                    RemoteSong(
                        SongInfo(
                            SongId(
                                title,
                                albumId,
                                tryOr(albumId.artist) { ArtistId(recording["artist-credit"][0]["name"].string) }
                            ),
                            duration = recording["length"].nullInt ?: 0,
                            track = recording["position"].nullInt ?: idx + 1,
                            disc = discIdx + 1,
                            year = year
                        ),
                        RemoteSong.Details(
                            tryOr(null) { recording["id"].string },
                            rgid,
                            null
                        )
                    )
                } catch (e: Exception) {
                    error { e.message }
                    null
                }
            }.filterNotNull()
        }.flatMap { it }.sortedBy { it.info.disc * 1000 + it.info.track }
    }

    override suspend fun searchArtists(query: String): List<Artist> {
        val entries = Http.get("http://musicbrainz.org/ws/2/artist", params = mapOf(
            "fmt" to "json",
            "query" to query
        )).gson["artists"].array

        return entries.map { it.obj }.mapNotNull {
            val name = it["name"].string
            val sortName = it["sort-name"].string.split(',', limit=2)
                .map { it.trim() }
                .asReversed()
                .joinToString(" ")
            val mbid = it["id"].string

            val lfmRes = Http.get("http://ws.audioscrobbler.com/2.0", params = mapOf(
                "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                "method" to "artist.getInfo",
                "mbid" to mbid
            )).gson
            val thumbnail = try {
                lfmRes["artist"]["image"][2]["#text"].string
            } catch (e: Throwable) {
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
        return Http.get("http://musicbrainz.org/ws/2/recording", params = mapOf(
            "fmt" to "json",
            "query" to query
        )).gson["recordings"].array.map {
            val artistName = it["artist-credit"][0]["artist"]["name"].string
            RemoteSong(
                SongInfo(
                    SongId(
                        it["title"].string,
                        it["releases"][0]["title"].string,
                        artistName,
                        artistName
                    ),
                    track = it["releases"][0]["media"][0]["track"][0]["number"].nullString?.toInt() ?: 0,
                    disc = 1,
                    duration = it["length"].int,
                    year = null
                )
            )
        }
    }

    suspend fun resolveAlbums(artistId: String): List<Album> = tryOr(listOf()) {
//        val mbid = /*this.mbid ?:*/ findMbid() ?: return@then

        val res = Jsoup.parse(
            Http.get("https://musicbrainz.org/artist/$artistId").text
        )
        info { "album: loading $artistId" }

        val artistName = res.getElementsByClass("artistheader")[0]
            .child(0).child(0).text()

        val discography = res.getElementById("content").getElementsByAttributeValue("method", "post")[0]
        val sections = discography.children()
//        val remoteAlbums = mutableListOf<Album>()

//        Library.instance.cacheRemoteArtist(
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

            info { "album: release type $type" }
            val items = sections[idx + 1].child(1)
            items.children().map {
                val titleLink = it.child(1).child(0)
                val title = titleLink.text()


                val year = it.child(0).text()?.toIntOrNull() ?: 0
                val albumId = titleLink.attr("href").split("/").last()


                val (remote, cover) = try {
                    val res = Http.get("http://ws.audioscrobbler.com/2.0", params = mapOf(
                        "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
                        "method" to "album.getinfo",
                        "album" to title,
                        "artist" to artistName//this.id.name
                    )).gson["album"]
                    val images = res["image"].nullArray
                    AlbumDetails(
                        albumId,
                        artistId,
                        thumbnailUrl = images?.get(2)?.get("#text").nullString,
                        listeners = res["listeners"].string.toInt()
                    ) to images?.last()?.get("#text").nullString
                } catch (e: Throwable) {
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
                        Library.instance.addAlbumExtras(
                            Library.AlbumMetadata(id, cover)
                        )
                    },
                    remote,
                    year = year,
                    type = type
                )

                info { "album: added '$title'!!" }

                album
            }
        }.awaitAll().flatMap { it!! }
    }


    override suspend fun find(album: AlbumId): Album? = try {
        val res = Http.get("https://musicbrainz.org/ws/2/release-group", params = mapOf(
            "fmt" to "json",
            "query" to "releasegroup:\"${album.displayName}\" AND artist:\"${album.artist.name}\"",
            "limit" to "2"
        )).gson.obj

        if (res["count"].int > 0) {
            val rg = res["release-groups"][0]
            val score = rg["score"].string.toInt()
            if (score >= 95) {
                RemoteAlbum(album, MusicBrainz.AlbumDetails(rg["id"].string))
            } else null
        } else null
    } catch (e: Exception) {
        error { e.stackTrace }
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