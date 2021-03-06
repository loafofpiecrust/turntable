package com.loafofpiecrust.turntable.style

import android.support.v7.widget.RecyclerView
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.bind
import com.loafofpiecrust.turntable.util.consumeEach
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScroller
import kotlinx.coroutines.Dispatchers
import org.jetbrains.anko.colorAttr


fun FastScrollRecyclerView.turntableStyle() {
    (RecyclerView::turntableStyle)(this)

    UserPrefs.accentColor.bind(this@turntableStyle).consumeEach(Dispatchers.Main) {
        setThumbColor(it)
        setPopupBgColor(it)
    }
    setPopupTextColor(context.colorAttr(android.R.attr.textColor))
    setPopupPosition(FastScroller.FastScrollerPopupPosition.ADJACENT)
    setAutoHideEnabled(true)
    addOnItemTouchListener(this)
}

fun RecyclerView.turntableStyle() {
//    itemAnimator = SlideInUpAnimator()
}