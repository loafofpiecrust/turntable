package com.loafofpiecrust.turntable.ui;

import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.view.MotionEvent;
import android.view.View;

public class CustomBottomSheetBehavior<V extends View> extends BottomSheetBehavior<V> {

    private boolean allowDragging = true;

    public void setAllowDragging(boolean allowDragging) {
        this.allowDragging = allowDragging;
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        return allowDragging && super.onInterceptTouchEvent(parent, child, event);
    }
}