package com.loafofpiecrust.turntable.util

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.View
import android.view.ViewAnimationUtils
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.replaceMainContent
import org.jetbrains.anko.find
import kotlin.math.max

//fun Context.circularReveal(newFragment: Fragment, cx: Int, cy: Int) {
//    if (this is MainActivity) {
//        // on tapping button, save touch location.
//        // push fragment
//        //
//        val container = find<View>(R.id.mainContentContainer)
//        val finalRadius = max(container.height, container.width).toFloat()
//    }
//}