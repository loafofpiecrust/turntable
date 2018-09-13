package com.loafofpiecrust.turntable.style

import android.support.v7.widget.Toolbar
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.bind
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.android.UI
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.dimen
import org.jetbrains.anko.matchParent

fun Toolbar.standardStyle(useDefaultColor: Boolean = false) {
    if (useDefaultColor) {
        UserPrefs.primaryColor.bind(this).consumeEach(UI) {
            backgroundColor = it
        }
    }
    minimumHeight = dimen(R.dimen.toolbar_height)
    minimumWidth = matchParent
    popupTheme = R.style.AppTheme_PopupOverlay
    navigationIconResource = R.drawable.ic_arrow_back
    setNavigationOnClickListener { context.popMainContent() }
}

fun Toolbar.detailsStyle() {
    minimumHeight = dimen(R.dimen.details_toolbar_height)
    minimumWidth = matchParent
    popupTheme = R.style.AppTheme_PopupOverlay
    navigationIconResource = R.drawable.ic_arrow_back
    setNavigationOnClickListener { context.popMainContent() }
}