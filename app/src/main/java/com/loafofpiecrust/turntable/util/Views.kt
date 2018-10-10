package com.loafofpiecrust.turntable.util

import android.graphics.Color
import android.graphics.PorterDuff
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.ViewManager
import android.widget.ImageButton
import android.widget.ImageView
import com.lapism.searchview.widget.SearchView
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.style.rippleBorderless
import com.lsjwzh.widget.recyclerviewpager.RecyclerViewPager
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.ankoView
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext


inline fun ViewManager.iconView(imageRes: Int, block: ImageView.() -> Unit) = iconView {
    imageResource = imageRes
    block()
}

inline fun ViewManager.iconView(block: ImageView.() -> Unit) = imageView {
    scaleType = ImageView.ScaleType.FIT_CENTER
    scaleType = ImageView.ScaleType.FIT_CENTER
    minimumHeight = dimen(R.dimen.icon_size)
    minimumWidth = dimen(R.dimen.icon_size)
    block()
}

inline fun ViewManager.iconButton(iconRes: Int, block: ImageButton.() -> Unit) = imageButton(iconRes) {
    rippleBorderless()
    padding = dimen(R.dimen.fab_margin)
    block()
}

inline fun ViewManager.fastScrollRecycler(init: @AnkoViewDslMarker FastScrollRecyclerView.() -> Unit = {}): FastScrollRecyclerView =
    ankoView({ FastScrollRecyclerView(it) }, theme = 0, init = init)

inline fun ViewManager.recyclerViewPager(init: @AnkoViewDslMarker RecyclerViewPager.() -> Unit): RecyclerViewPager =
    ankoView({ RecyclerViewPager(it) }, theme = 0, init = init)

inline fun ViewManager.searchBar(theme: Int = 0, init: SearchView.() -> Unit = {}): SearchView =
    ankoView({ SearchView(it) }, theme, init)


inline fun <T> measureTime(tag: CharSequence, block: () -> T): T {
    val start = System.nanoTime()
    val res = block()
    println("$tag took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)}ms")
    return res
}