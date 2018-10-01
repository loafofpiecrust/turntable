package com.loafofpiecrust.turntable.artist

import android.view.ViewManager
import android.widget.LinearLayout
import android.widget.TextView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar

class BiographyFragment: BaseDialogFragment() {
    //    @Arg(optional = true) lateinit var artistId: ArtistId
    lateinit var artist: ReceiveChannel<Artist>

    companion object {
        fun fromChan(channel: ReceiveChannel<Artist>): BiographyFragment {
            return BiographyFragment().apply {
                artist = channel
            }
        }
    }

    override fun ViewManager.createView() = linearLayout {
        orientation = LinearLayout.VERTICAL
        fitsSystemWindows = true

        // TODO: Add full-res artist image, as not-cropped as possible.
        // TODO: Multiple artist images?
        val toolbar = toolbar {
            standardStyle(true)
            setNavigationOnClickListener { dismiss() }
        }

        lateinit var bioText: TextView
        scrollView {
            padding = dimen(R.dimen.text_content_margin)
            bioText = textView()
        }.lparams(matchParent, matchParent)

        artist.consumeEach(Dispatchers.Main) { artist ->
            toolbar.title = artist.id.displayName
            bioText.text = artist.biography
        }
    }
}