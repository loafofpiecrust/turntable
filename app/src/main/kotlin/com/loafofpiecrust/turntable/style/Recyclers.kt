package com.loafofpiecrust.turntable.style

import android.support.v7.widget.RecyclerView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.getColorCompat
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.bind
import com.loafofpiecrust.turntable.util.consumeEach
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScroller
import kotlinx.coroutines.experimental.android.UI


inline fun FastScrollRecyclerView.turntableStyle() {
    (RecyclerView::turntableStyle)(this)

    UserPrefs.secondaryColor.bind(this).consumeEach(UI) {
        setThumbColor(it)
        setPopupBgColor(it)
    }
    setPopupTextColor(context.getColorCompat(R.color.text))
    setPopupPosition(FastScroller.FastScrollerPopupPosition.ADJACENT)
    setAutoHideEnabled(true)
    addOnItemTouchListener(this)
}

inline fun RecyclerView.turntableStyle() {
//    itemAnimator = SlideInUpAnimator()
}