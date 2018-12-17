package com.loafofpiecrust.turntable.style

import android.util.TypedValue
import android.view.View
import com.loafofpiecrust.turntable.R
import org.jetbrains.anko.backgroundResource

fun View.ripple() {
    val itemBg = TypedValue()
    context.theme.resolveAttribute(R.attr.selectableItemBackground, itemBg, true)
    backgroundResource = itemBg.resourceId
}

fun View.rippleCircle() {
    val itemBg = TypedValue()
    context.theme.resolveAttribute(R.attr.actionBarItemBackground, itemBg, true)
    backgroundResource = itemBg.resourceId
}