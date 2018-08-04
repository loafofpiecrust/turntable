package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewManager
import com.github.salomonbrys.kotson.*
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.browse.MusicBrainz
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.provided
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.success
import com.loafofpiecrust.turntable.util.task
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

class RelatedArtistsFragment: BaseFragment() {
    @Arg lateinit var artist: Artist
    lateinit var gridAdapter: ArtistsAdapter

    override fun makeView(ui: ViewManager): View {
//        ActivityStarter.fill(this)
        return ui.frameLayout {
            recyclerView {
                // TODO: dynamic grid size
                layoutManager = GridLayoutManager(context, 3)
                adapter = ArtistsAdapter { view, artists, pos ->
                    // smoothly transition the cover image!
                    val artist = artists[pos]
                    ctx.replaceMainContent(
                        ArtistDetailsFragmentStarter.newInstance(artist, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE) ,
                        true,
                        view.transitionViews
                    )
                }.apply {
                    // TODO: Have this only load when this tab is entered.
//                    val nameQuery = URLEncoder.encode(artist.id, "UTF-8")
//                    println("related artists for '$nameQuery'")
                    task {
//                        Thread.sleep(1000)
                        tryOr(0) {
                            Spotify.similarTo(artist.id).also {
                                if (it.isNotEmpty()) {
                                    return@task it
                                }
                            }
                        }

                        // Load the related artists from last.fm

                        val remote = artist.remote
                        val query = if (remote is MusicBrainz.ArtistDetails && artist.id.displayName.length <= 5) {
                            "mbid" to remote.id
                        } else {
                            "artist" to artist.id.name
                        }
                        val res = Http.get(
                            "https://ws.audioscrobbler.com/2.0/",
                            params = mapOf(
                                "api_key" to BuildConfig.LASTFM_API_KEY,
                                "method" to "artist.getsimilar",
                                "format" to "json",
                                "autocorrect" to "1",
                                query
                            )
                        ).gson.obj

                        res["similarartists"]["artist"].array.map { it.obj }.map {
                            val img = it["image"][2]["#text"].string
                            val mbid = it["mbid"].nullString
                            val name = it["name"].string
                            // Use Last.FM data directly if the id is real short,
                            // because the mapping to MusicBrainz is quite likely wrong
                            Artist(
                                ArtistId(name),
                                given(mbid.provided(name.length > 3)) {
                                    MusicBrainz.ArtistDetails(it)
                                },
                                listOf(),
                                img
                            )
                        }
                    }.success(UI) {
                        updateData(it)
                    }
                    Unit
                }
            }
        }
    }
}