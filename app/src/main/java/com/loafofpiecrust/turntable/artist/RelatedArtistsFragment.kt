package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceTask
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

class RelatedArtistsFragment: BaseFragment() {
    @Arg lateinit var artistId: ArtistId
    lateinit var gridAdapter: ArtistsAdapter

    override fun ViewManager.createView(): View = recyclerView {
        // TODO: dynamic grid size
        layoutManager = GridLayoutManager(context, 3)
        adapter = ArtistsAdapter { view, artists, pos ->
            // smoothly transition the cover image!
            val artist = artists[pos]
            ctx.replaceMainContent(
                ArtistDetailsFragment.fromArtist(artist, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE),
                true,
                view.transitionViews
            )
        }.apply {
            subscribeData(produceTask(BG_POOL + jobs) { Spotify.similarTo(artistId) })
//            task {
//                Spotify.similarTo(artistId)

                // Load the related artists from last.fm

//                        val remote = artist.remote
//                        val query = if (remote is MusicBrainz.ArtistDetails && artist.id.displayName.length <= 5) {
//                            "mbid" to remote.id
//                        } else {
//                            "artist" to artist.id.name
//                        }
//                        val res = Http.get(
//                            "https://ws.audioscrobbler.com/2.0/",
//                            params = mapOf(
//                                "api_key" to BuildConfig.LASTFM_API_KEY,
//                                "method" to "artist.getsimilar",
//                                "format" to "json",
//                                "autocorrect" to "1",
//                                query
//                            )
//                        ).gson.obj
//
//                        res["similarartists"]["artist"].array.map { it.obj }.map {
//                            val img = it["image"][2]["#text"].string
//                            val mbid = it["mbid"].nullString
//                            val name = it["name"].string
//                            // Use Last.FM data directly if the id is real short,
//                            // because the mapping to MusicBrainz is quite likely wrong
//                            Artist(
//                                ArtistId(name),
//                                given(mbid.provided(name.length > 3)) {
//                                    MusicBrainz.ArtistDetails(it)
//                                },
//                                listOf(),
//                                img
//                            )
//                        }
//            }.success(UI) {
//                updateData(it)
//            }
        }
    }
}