package com.loafofpiecrust.turntable.style

import android.support.v7.widget.RecyclerView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.consumeEach
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScroller
import kotlin.coroutines.experimental.CoroutineContext


inline fun FastScrollRecyclerView.turntableStyle(uiContext: CoroutineContext) {
    (RecyclerView::turntableStyle)(this, uiContext)

    UserPrefs.secondaryColor.consumeEach(uiContext) {
        setThumbColor(it)
        setPopupBgColor(it)
    }
    setPopupTextColor(resources.getColor(R.color.text))
    setPopupPosition(FastScroller.FastScrollerPopupPosition.ADJACENT)
    setAutoHideEnabled(true)
    addOnItemTouchListener(this)
}

inline fun RecyclerView.turntableStyle(uiContext: CoroutineContext) {
//    itemAnimator = SlideInUpAnimator()
}