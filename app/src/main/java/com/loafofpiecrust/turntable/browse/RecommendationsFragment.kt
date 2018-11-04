package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.ViewManager
import com.loafofpiecrust.turntable.browse.MusicAdapter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseFragment
import org.jetbrains.anko.recyclerview.v7.recyclerView

/**
 * Created by snead on 12/10/17.
 */
class RecommendationsFragment: BaseFragment() {
    override fun ViewManager.createView() = recyclerView {
        layoutManager = LinearLayoutManager(context)
        adapter = MusicAdapter(
            coroutineContext,
            UserPrefs.recommendations.openSubscription()
        )
    }
}