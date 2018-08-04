package com.loafofpiecrust.turntable.style

import android.support.v7.widget.RecyclerView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.consumeEach
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScroller
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI


fun FastScrollRecyclerView.turntableStyle(subs: Job) {
    (RecyclerView::turntableStyle)(this, subs)

    UserPrefs.secondaryColor.consumeEach(UI + subs) {
        setThumbColor(it)
        setPopupBgColor(it)
    }
    setPopupTextColor(resources.getColor(R.color.text))
    setPopupPosition(FastScroller.FastScrollerPopupPosition.ADJACENT)
    setAutoHideEnabled(true)
    addOnItemTouchListener(this)
}

fun RecyclerView.turntableStyle(subs: Job) {
    itemAnimator = SlideInUpAnimator()
}