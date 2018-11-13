package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.MusicAdapter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.alert

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

    override fun Menu.createOptions() {
        menuItem(R.string.recommendations_clear).onClick {
            alert {
                title = "Clear Recommendations"
                message = "Are you sure?"
                positiveButton("Clear") {
                    UserPrefs.recommendations.offer(emptyList())
                }
                cancelButton {}
            }.show()
        }
    }
}