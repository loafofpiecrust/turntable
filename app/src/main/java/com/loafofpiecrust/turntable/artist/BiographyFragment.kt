package com.loafofpiecrust.turntable.artist

import activitystarter.Arg
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.provided
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.turntableToolbar
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*

class BiographyFragment: BaseFragment() {
    @Arg lateinit var artist: Artist

    override fun makeView(ui: ViewManager) = ui.verticalLayout {
        fitsSystemWindows = true

        val remote = artist.remote.provided {
            it?.description != null
        } ?: runBlocking { SearchApi.find(artist) }


        // TODO: Add full-res artist image, as not-cropped as possible.
        // TODO: Multiple artist images?
        turntableToolbar(this@BiographyFragment) {
            title = artist.id.displayName
        }

        scrollView {
            textView(remote?.description)
        }.lparams(matchParent, matchParent) {
            padding = dimen(R.dimen.text_content_margin)
        }
    }
}