package com.loafofpiecrust.turntable.ui

import android.support.v7.widget.Toolbar
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.consumeEach
import org.jetbrains.anko.appcompat.v7._Toolbar
import org.jetbrains.anko.appcompat.v7.themedToolbar
import org.jetbrains.anko.backgroundColor

fun ViewManager.turntableToolbar(fragment: BaseFragment, block: _Toolbar.() -> Unit): Toolbar {
    return themedToolbar(R.style.AppTheme_AppBarOverlay) {
        fitsSystemWindows = true
        UserPrefs.primaryColor.consumeEach(fragment.UI) {
            backgroundColor = it
        }
        block()
    }
}

fun ViewManager.turntableToolbar(activity: BaseActivity, block: _Toolbar.() -> Unit): Toolbar {
    return themedToolbar(R.style.AppTheme_AppBarOverlay) {
        fitsSystemWindows = true
        UserPrefs.primaryColor.consumeEach(activity.UI) {
            backgroundColor = it
        }
        block()
    }
}