package com.loafofpiecrust.turntable.artist

import android.content.Context
import android.os.Parcelable
import android.support.annotation.StringRes
import android.support.v7.widget.GridLayoutManager
import android.view.Gravity
import android.view.Menu
import android.view.ViewManager
import android.widget.TextView
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.SearchFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView

sealed class ArtistsUI(
    private val detailsMode: ArtistDetailsUI.Mode = ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE,
    @StringRes
    private val emptyDetailsRes: Int = 0
) : UIComponent() {
    abstract val artists: BroadcastChannel<List<Artist>>

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.search, R.drawable.ic_search, showIcon = true).onClick {
            context.replaceMainContent(
                SearchFragment.newInstance(SearchFragment.Category.Artists())
            )
        }

        if (BuildConfig.DEBUG) {
            subMenu(R.string.set_grid_size) {
                group(0, true, true) {
                    val items = (1..4).map { idx ->
                        menuItem(idx.toString()).apply {
                            onClick { UserPrefs.artistGridColumns puts idx }
                        }
                    }

                    launch {
                        UserPrefs.artistGridColumns.consumeEach { cols ->
                            items.forEach { it.isChecked = false }
                            items[cols - 1].isChecked = true
                        }
                    }
                }
            }
        }
    }

    override fun ViewContext.render() = refreshableRecyclerView {
//        if (startRefreshing) {
//            isRefreshing = true
//        }

        channel = artists.openSubscription()

        contents {
            if (this@ArtistsUI is All) {
                fastScrollRecycler {
                    turntableStyle()
                }
            } else {
                recyclerView {
                    turntableStyle()
                }
            }.apply {
                adapter = ArtistsAdapter(coroutineContext, artists.openSubscription()) { holder, artists, idx ->
                    // smoothly transition the cover image!
                    holder.itemView.context.replaceMainContent(
                        ArtistDetailsUI.Resolved(artists[idx], detailsMode).createFragment(),
//                        ArtistDetailsFragment.fromArtist(artists[idx], detailsMode),
                        true,
                        holder.transitionViews
                    )
                }
                layoutManager = GridLayoutManager(context, 3)

                val gutter = dimen(R.dimen.grid_gutter)
                padding = gutter
                addItemDecoration(ItemOffsetDecoration(gutter))
            }
        }

        emptyState {
            emptyContentView(
                R.string.artists_empty,
                emptyDetailsRes
            )
        }
    }

    @Parcelize
    class All: ArtistsUI(
        ArtistDetailsUI.Mode.LIBRARY,
        R.string.artists_empty_details
    ), Parcelable {
        override val artists = Library.artistsMap
            .openSubscription()
            .map { it.values.sortedBy { it.id } }
            .replayOne()
    }

    class Custom(
        override val artists: BroadcastChannel<List<Artist>>,
        @StringRes emptyDetailsRes: Int = 0
    ): ArtistsUI(
        ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE,
        emptyDetailsRes
    )
}


fun ViewManager.emptyContentView(
    @StringRes title: Int,
    @StringRes details: Int
) = verticalLayout {
    gravity = Gravity.CENTER
    padding = dimen(R.dimen.empty_state_padding)
    textView(title) {
        textSizeDimen = R.dimen.title_text_size
        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        bottomPadding = dip(8)
    }
    textView {
        if (details != 0) {
            textResource = details
        }
        textSizeDimen = R.dimen.subtitle_text_size
        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
    }
}