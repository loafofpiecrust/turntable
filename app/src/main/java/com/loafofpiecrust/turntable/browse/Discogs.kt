package com.loafofpiecrust.turntable.browse

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.RemoteArtist
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.text
import com.mcxiaoke.koi.ext.closeQuietly
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.Response
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import org.jsoup.Jsoup

/**
 * Created by snead on 2/1/18.
 */
object Discogs: SearchApi, AnkoLogger {
    override val displayName: Int
        get() = R.string.search_discogs

    @Parcelize
    data class AlbumDetails(
        val id: String,
        override val thumbnailUrl: String? = null,
        override val artworkUrl: String? = null
    ): Album.RemoteDetails {
        override suspend fun resolveTracks(album: AlbumId) =
            tracksOnAlbum(id)
    }

    @Parcelize
    data class ArtistDetails(
        val id: Int,
        override val biography: String = "",
        val artworkUrl: String? = null//,
//        val members:
    ): RemoteArtist.Details {
        override val albums: List<Album> by lazy {
            runBlocking { discographyHtml(id) }
//            runBlocking { discography(id) }
        }
    }


    private val ARTIST_OF_MANY = Regex("\\(\\d+\\)$")
    private val ARTIST_ALT_NAME = Regex("\\*$")
    private fun cleanArtistName(artist: String) = ArtistId(
        artist.replace(ARTIST_OF_MANY, "")
            .replace(ARTIST_ALT_NAME, "")
    )


    private const val key = BuildConfig.DISCOGS_KEY
    private const val secret = BuildConfig.DISCOGS_SECRET

    private suspend fun apiRequest(url: String, params: Map<String, String> = mapOf()): JsonObject =
        try {
            var reqCount = 3
            var res: Response
            do {
                reqCount--
                res = Http.get(url, params = params + mapOf(
                    "key" to key,
                    "secret" to secret
                ), headers = mapOf(
                    "User-Agent" to "com.loafofpiecrust.turntable/0.1alpha"
                ))
                if (res.code() == 429) {
                    res.closeQuietly()
                    delay(4000)
                }
            } while (res.code() > 400 && reqCount > 0)
            val rem = res.header("X-Discogs-Ratelimit-Remaining")!!.toInt()
            info { "$rem remaining" }
            if (rem <= 5) {
                delay(500)
            }

            res.gson.obj
        } catch (e: Exception) {
            error(e.message, e)
            throw e
//            error(e)
        }

    private suspend fun searchFor(artist: ArtistId): List<Int> {
        return doSearch(artist.displayName, mapOf(
            "type" to "artist"
        )).map {
            it["id"].int
        }
    }

    private suspend fun searchFor(album: AlbumId): List<RemoteAlbum> {
        return doSearch(album.displayName, mapOf(
            "type" to "release",
            "artist" to album.artist.name,
//            "year" to album.year.toString(),
            "per_page" to "5"
        )).mapNotNull {
            tryOr(null) {
                val artist = cleanArtistName(it["artists"][0]["name"].string)
                RemoteAlbum(
                    AlbumId(it["name"].string, artist),
                    AlbumDetails("${it["type"].string}s/${it["id"].string}")
                )
            }
        }
    }

    private suspend fun doSearch(query: String, params: Map<String, String>): List<JsonObject> {
        // TODO: Heed rate limits!
        return apiRequest("https://api.discogs.com/database/search", params = params + mapOf(
            "q" to query
        ))["results"].array.map { it.obj }
    }

    override suspend fun searchArtists(query: String): List<Artist> {
        return doSearch(query, mapOf(
            "type" to "artist"
        )).mapNotNull {
            tryOr(null) {
                RemoteArtist(
                    cleanArtistName(it["title"].string),
                    ArtistDetails(it["id"].int, "", it["thumb"].nullString)
                )
            }
        }
    }

    override suspend fun searchAlbums(query: String): List<Album> {
        return doSearch(query, mapOf(
            "type" to "master"
        )).map {
            val titleParts = it["title"].string.split(" - ", limit=2)
            RemoteAlbum(
                AlbumId(
                    titleParts[1],
                    cleanArtistName(titleParts[0])
                ),
                AlbumDetails(
                    it["id"].int.toString(),
                    thumbnailUrl = it["thumb"].nullString
                ),
                year = it["year"].nullString?.toIntOrNull()
            )
        }
    }

    /// Song search is not supported by Discogs.
    override suspend fun searchSongs(query: String): List<Song> = listOf()

    override suspend fun find(album: AlbumId): Album? {
        // TODO: ensure that the result is identical or fuzzy match of >=98
        return given(searchFor(album).firstOrNull()) {
            it
        }
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return given(searchFor(artist).firstOrNull()) {
            val res = apiRequest("https://api.discogs.com/artists/$it").obj
            RemoteArtist(
                artist,
                ArtistDetails(
                    it,
                    res["profile"].nullString ?: "",
                    res["images"]?.get(0)?.get("uri")?.nullString
                )
            )
        }
    }

//    override suspend fun find(song: Song): Song.Details? {
//        return given(searchFor(song.id).firstOrNull()) {
//            val res = apiRequest("https://api.discogs.com/artists/$it").obj
//            ArtistDetails(
//                it,
//                res["profile"].nullString,
//                res["images"]?.get(0)?.get("uri")?.nullString
//            )
//        }
//    }


    private fun releasesToAlbums(artist: ArtistId?, rels: List<JsonObject>): List<Pair<RemoteAlbum, String>> {
        return rels.mapNotNull {
            val role = it["role"].nullString
            val title = if (role == null) {
                it["title"].string
            } else {
                it["title"].string.substringAfter(" - ")
            }
            val relTy = it["type"].nullString
            val format = it["format"].nullString?.split(", ")
            val type = format?.let {
                when {
                    relTy == "LP" -> Album.Type.LP
                    relTy == "Single" -> Album.Type.SINGLE
                    relTy == "EP" -> Album.Type.EP
                    it.any { it.contains("DVD") || it.contains("Test Pressing") }
                        || it.contains("PAL")
                        || it.contains("NTSC") -> Album.Type.OTHER
                    title.contains("Live at ", true)
                        || title.contains("Live in ", true) -> Album.Type.LIVE
                    it.contains("Comp") -> Album.Type.COMPILATION
                    it.any { it.startsWith("Album") }
                        || it.any { it.endsWith("LP") } -> Album.Type.LP
                    it.contains("EP")
                        || it.contains("MiniAlbum")
                        || it.any { it[0].isDigit() && it.endsWith("File") && it[0].toString().toInt() > 4 } -> Album.Type.EP
                    it.contains("Single")
                        || it.any { it.endsWith("File") }
                        || it.contains("7\"")
                        || it.contains("Maxi")
                        || it.contains("Flexi")
                        || it.contains("12\"") -> Album.Type.SINGLE
                    it.any { it.startsWith("RE") } -> Album.Type.COMPILATION
                    else -> Album.Type.LP
                }
            } ?: Album.Type.OTHER
//            task(UI) { println("discogs: type for '$name': $format = $type, $relTy") }
            val (remoteId, relType) = when (relTy) {
                "master" -> "masters/${it["id"].int}" to "master"
                else -> "releases/${it["id"].int}" to "release"
            }

            val artistName = given(it["artist"].nullString) {
                cleanArtistName(it)
            }

            // TODO: Include featured albums as well, somewhere
            if (role == null || role == "Main") {
                RemoteAlbum(
                    AlbumId(title, artistName ?: artist
                    ?: ArtistId("")),
                    AlbumDetails(
                        remoteId,
                        thumbnailUrl = it["thumb"].nullString,
                        artworkUrl = it["images"].nullArray
                            ?.firstOrNull { it["type"].nullString == "primary" }
                            ?.get("uri")?.string
                    ),
//                    artworkUrl = null,
                    year = it["year"].nullInt,
                    type = type
                ) to relType
            } else null
        }
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? {
        // FIXME: Generalize this!!!
        val id = when {
            album is RemoteAlbum -> {
                val remote = album.remoteId
                if (remote is Discogs.AlbumDetails) {
                    remote.id
                } else searchFor(album.id).firstOrNull()
            }
            search -> searchFor(album.id).firstOrNull() ?: return null
            else -> return null
        }
//        if (album.remote !is Discogs.AlbumDetails) {
//            return null
//        }
        val res = apiRequest("https://api.discogs.com/$id").obj
        val imgs = res["images"].nullArray
        val url = (imgs?.firstOrNull { it["type"].nullString == "primary" } ?: imgs?.first())?.get("uri")?.string

        if (url != null) {
            Library.instance.addAlbumExtras(
                Library.AlbumMetadata(album.id, url)
            )
        }
        return url
    }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? {
        val id = when {
            artist is RemoteArtist -> {
                if (artist.details is ArtistDetails) {
                    artist.details.id
                } else searchFor(artist.id).firstOrNull()
            }
            search -> searchFor(artist.id).firstOrNull() ?: return null
            else -> return null
        }

        val res = apiRequest("https://api.discogs.com/artists/$id").obj
        val imgs = res["images"].nullArray
        val url = (imgs?.firstOrNull { it["type"].nullString == "primary" } ?: imgs?.first())?.get("uri")?.string

        if (url != null) {
            Library.instance.addArtistExtras(
                Library.ArtistMetadata(artist.id, url)
            )
        }
        return url
    }

    private suspend fun discographyPages(id: Int, page: Int = 1, perPage: Int = 100): List<Pair<RemoteAlbum, String>> {
        val res = apiRequest("https://api.discogs.com/artists/$id/releases", params = mapOf(
            "sort" to "year",
            "sort_order" to "desc",
            "per_page" to perPage.toString(),
            "page" to page.toString()
        ))

        val pageCt = res["pagination"]["pages"].int
        val curr = releasesToAlbums(null, res["releases"].array.map { it.obj })

        return if (pageCt > page) {
            curr + discographyPages(id, page + 1)
        } else curr
    }

    suspend fun discography(id: ArtistId): List<RemoteAlbum> =
        given(searchFor(id).firstOrNull()) {
            discography(it)
        } ?: listOf()

    private suspend fun discography(id: Int): List<RemoteAlbum> {
        val releases = discographyPages(id).toMutableList()
        var masters = releases.filter { it.second == "master" }
            .also { releases.removeAll(it) }
            .map { it.first }
        var totalMasterReqs = 0
        masters = masters.parMap { master ->
            val subRels = run {
//            val subRels = releases.filter {
//                it.first.id.displayName == master.id.displayName
//            }.let {
//                if (it.isNotEmpty()) {
//                    synchronized(releases) {
//                        releases.removeAll(it)
//                    }
//                    it
//                } else {
                    synchronized(releases) {
                        totalMasterReqs += 1
                    }
                val det = master.remoteId as Discogs.AlbumDetails
                    val res = apiRequest(
                        "https://api.discogs.com/${det.id}/versions",
                        mapOf("per_page" to "5")
                    )
                    releasesToAlbums(null,
                        res["versions"].array.map { it.obj }
                    )
//                    it
//                }
//            }
            }.map { it.first }

            val common = subRels.groupingBy { it.type }
                .eachCount().also {
                    debug { "discogs: for ${master.id.displayName}, $it" }
                }
                .maxBy {
                    if (it.key != Album.Type.OTHER) {
                        it.value
                    } else 0
                }

            RemoteAlbum(
                master.id,
                master.remoteId,
                year = master.year,
                type = common?.key ?: master.type
            )
        }.awaitAllNotNull()

        debug { "discogs: requested $totalMasterReqs masters" }

        return (masters + releases.map { it.first })//.dedupMerge(
//            { a, b -> a.id == b.id },
//            { a, b -> if (b.year ?: 0 > a.year ?: 0) b else a }
//        )

//        return .dedupMerge(
//            { a, b -> a.first.id.displayName == b.first.id.displayName },
//            { a, b ->
//                val type = if (b.first.type == Album.Type.OTHER
//                    || b.first.type == Album.Type.LP
//                    && a.first.type != Album.Type.OTHER) {
//                    a.first.type
//                } else {
//                    b.first.type
//                }
//                val remote = if (a.first.remote!!.id!!.startsWith("m")) {
//                    b.first.remote
//                } else a.first.remote
//                val s = if (a.second == "master" && b.second == "master") {
//                    "master"
//                } else "release"
//
//                if (a.second == "master") {
//                    a.first.copy(type = type, remote = remote) to s
//                } else if (b.second == "master") {
//                    b.first.copy(type = type, remote = remote) to s
//                } else {
//
//                }
//            }
//        ).map { it.first }
    }

    suspend fun discographyHtml(id: ArtistId) =
        given(searchFor(id).firstOrNull()) {
            discographyHtml(it)
        } ?: listOf()

    suspend fun discographyHtml(id: Int): List<Album> {
        var start = System.nanoTime()
        val txt = Http.get("https://www.discogs.com/artist/$id", mapOf(
            "limit" to "100"
        )).use { it.text }
        info { "discography request took ${System.nanoTime() - start}ns" }
        start = System.nanoTime()
        val res = Jsoup.parse(txt).body()
        info { "discography parse took ${System.nanoTime() - start}ns" }
        start = System.nanoTime()
        val profile = res.getElementsByClass("profile").first()
        val artist = ArtistId(profile.child(0).text())
        val table = res.getElementById("artist").child(0)

        var currType: Album.Type? = Album.Type.LP
        return table.children().mapNotNull { row -> when {
            row.hasClass("credit_header") -> {
                currType = when (row.text()) {
                    "Albums" -> Album.Type.LP
                    "Singles & EPs" -> Album.Type.EP
                    "Compilations" -> Album.Type.COMPILATION
                    "Videos" -> null
                    else -> Album.Type.OTHER
                }
                null
            }
            currType != null -> {
                val cols = row.children()
                val titleLink = cols.first { it.hasClass("title") }
                    .children().first { it.tagName() == "a" }
                val title = titleLink.text()
                val linkParts = titleLink.attr("href").split('/')
                val id = linkParts[linkParts.size - 2] + "s/" + linkParts.last()
                val ty = tryOr(currType) {
                    val format = cols.first { it.hasClass("format") }.text()
                    val types = format.substring(1, format.length - 1).split(", ")

                    when {
                        types.contains("Single") -> Album.Type.SINGLE
                        types.contains("EP") -> Album.Type.EP
                        types.contains("Comp") -> Album.Type.COMPILATION
                        else -> currType
                    }
                }
                val year = cols.first { it.hasClass("year") }.text().toIntOrNull()

                val thumbLink = cols.firstOrNull { it.hasClass("image") }
                    ?.getElementsByClass("thumbnail_center")?.firstOrNull()
                    ?.child(0)?.attr("data-src")

                val artist = ArtistId(
                    cols.first { it.hasClass("artist") }.text()
                )

                RemoteAlbum(
                    AlbumId(title, artist),
                    AlbumDetails(id, thumbLink),
                    year = year,
                    type = ty!!
                )
            }
            else -> null
        } }.also {
            info { "discography process took ${System.nanoTime() - start}ns" }
        }
    }

    suspend fun tracksOnAlbum(id: String): List<Song> {
//        val id = if (id.startsWith("m")) { // this is a master, we need a release.
//            val res = Http.get("https://api.discogs.com/$id/versions", params = mapOf(
//                "key" to key,
//                "secret" to secret
//            )).gson
//            "releases/"+ res["versions"][0]["id"].string
//        } else id
        if (id.isEmpty()) return listOf()
        info { "getting tracks of album '$id'" }
        val res = apiRequest("https://api.discogs.com/$id")
        val albumTitle = res["title"].string
        val artistName = cleanArtistName(res["artists"][0]["name"].string)
        return res["tracklist"].array.map { it.obj }.mapIndexed { idx, it ->
            val title = it["title"].string
            val durationParts = it["duration"].nullString?.split(':')
            val duration = durationParts?.let {
                if (it.size > 1) {
                    (it[0].toInt() * 60 + it[1].toInt()) * 1000
                } else 0
            } ?: 0
//            RemoteSong(
            Song(
                SongId(
                    title,
                    AlbumId(albumTitle, artistName),
                    features = if (it.has("extraartists")) {
                        it["extraartists"].array.map {
                            ArtistId(it["name"].string, it["anv"].nullString)
                        }
                    } else listOf()
                ),
                track = idx + 1,
                disc = 1,
                duration = duration,
                year = null
            )
//            )
        }
    }

}
