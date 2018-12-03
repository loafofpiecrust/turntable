package com.loafofpiecrust.turntable.views

import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.view.MotionEvent
import android.view.View

class CustomBottomSheetBehavior<V : View> : BottomSheetBehavior<V>() {
    var allowDragging = true

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        return allowDragging && super.onInterceptTouchEvent(parent, child, event)
    }
}