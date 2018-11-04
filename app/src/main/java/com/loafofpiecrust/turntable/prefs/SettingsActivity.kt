package com.loafofpiecrust.turntable.prefs

import activitystarter.MakeActivityStarter
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.ui.BaseActivity
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout

@MakeActivityStarter
class SettingsActivity: BaseActivity() {
    override fun ViewManager.createView() = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            UserPrefs.primaryColor.consumeEachAsync {
                backgroundColor = it
            }

            toolbar {
                title = getString(R.string.action_settings)
                navigationIconResource = R.drawable.ic_arrow_back
                setNavigationOnClickListener { onBackPressed() }
            }
        }

        frameLayout {
            id = R.id.mainContent
            fragmentManager.beginTransaction()
                .add(id, MainPrefsFragment())
                .commit()
        }
    }
}