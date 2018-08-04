package com.loafofpiecrust.turntable.style

import android.support.v7.widget.Toolbar
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip


fun Toolbar.standardStyle(subs: Job) {
    minimumHeight = dip(48)
    popupTheme = R.style.AppTheme_PopupOverlay
    UserPrefs.primaryColor.consumeEach(UI + subs) {
        backgroundColor = it
    }
}

fun Toolbar.detailsStyle(subs: Job) {
    standardStyle(subs)
    minimumHeight = dimen(R.dimen.details_toolbar_height)
}