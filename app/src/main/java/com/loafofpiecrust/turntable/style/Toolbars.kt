package com.loafofpiecrust.turntable.style

import android.support.v7.widget.Toolbar
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.consumeEach
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip
import org.jetbrains.anko.matchParent
import kotlin.coroutines.experimental.CoroutineContext

fun Toolbar.standardStyle(uiContext: CoroutineContext) {
    UserPrefs.primaryColor.consumeEach(uiContext) {
        backgroundColor = it
    }
    minimumHeight = dip(48)
    minimumWidth = matchParent
    popupTheme = R.style.AppTheme_PopupOverlay
    navigationIconResource = R.drawable.ic_arrow_back_black_24dp
    setNavigationOnClickListener { context.popMainContent() }
}

fun Toolbar.detailsStyle(uiContext: CoroutineContext) {
    minimumHeight = dimen(R.dimen.details_toolbar_height)
    minimumWidth = matchParent
    popupTheme = R.style.AppTheme_PopupOverlay
    navigationIconResource = R.drawable.ic_arrow_back_black_24dp
    setNavigationOnClickListener { context.popMainContent() }
}