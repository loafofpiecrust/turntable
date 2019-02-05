package com.loafofpiecrust.turntable.browse

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.emptyContentView
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.collections.immutable.immutableListOf
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.recyclerview.v7.recyclerView

@Parcelize
class RecommendationsUI: UIComponent(), Parcelable {
    override fun ViewContext.render() = refreshableRecyclerView {
        val recs = UserPrefs.recommendations

        channel = recs.openSubscription()

        contents {
            recyclerView {
                layoutManager = LinearLayoutManager(context)
                adapter = MusicAdapter(
                    coroutineContext,
                    recs.openSubscription()
                )
            }
        }

        emptyState {
            emptyContentView(
                R.string.recommendations_empty,
                R.string.recommendations_empty_details
            )
        }
    }

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.recommendations_clear).onClick {
            context.alert {
                title = "Clear Recommendations"
                message = "Are you sure?"
                positiveButton("Clear") {
                    UserPrefs.recommendations.offer(immutableListOf())
                }
                cancelButton {}
            }.show()
        }
    }
}