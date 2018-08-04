package com.loafofpiecrust.turntable.browse.ui

import android.support.v7.widget.LinearLayoutManager
import android.view.ViewManager
import com.loafofpiecrust.turntable.browse.MusicAdapter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.BaseFragment
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx

/**
 * Created by snead on 12/10/17.
 */
class RecommendationsFragment: BaseFragment() {
    override fun makeView(ui: ViewManager) = ui.recyclerView {
        layoutManager = LinearLayoutManager(ctx)
        adapter = MusicAdapter(UserPrefs.recommendations.openSubscription())
    }
}