package com.loafofpiecrust.turntable.util

import android.graphics.Color
import android.graphics.PorterDuff
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
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
import fr.castorflex.android.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
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

inline fun ViewManager.circularProgressBar(theme: Int = 0, init: @AnkoViewDslMarker CircularProgressBar.() -> Unit = {}): CircularProgressBar =
    ankoView({ CircularProgressBar(it) }, theme = theme, init = init)

inline fun Toolbar.menuItem(
    @StringRes titleRes: Int,
    @DrawableRes iconId: Int? = null,
    @ColorInt color: Int? = null,
    showIcon: Boolean = false,
    init: MenuItem.() -> Unit = {}
): MenuItem {
    return menu.menuItem(titleRes, iconId, color, showIcon, init)
}

inline fun Menu.menuItem(
    @StringRes titleRes: Int,
    @DrawableRes iconId: Int? = null,
    @ColorInt color: Int? = null,
    showIcon: Boolean = false,
    init: MenuItem.() -> Unit = {}
): MenuItem {
    val item = add(App.instance.getString(titleRes))
    if (iconId != null) {
        item.icon = App.instance.getDrawable(iconId)
        // TODO: Contrast with primary color instead?
        val color = color ?: if (UserPrefs.useDarkTheme.valueOrNull != false) {
            Color.WHITE
        } else Color.BLACK
        item.icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }
    init.invoke(item)
    item.setShowAsAction(
        if (showIcon) {
            MenuItem.SHOW_AS_ACTION_IF_ROOM
        } else MenuItem.SHOW_AS_ACTION_NEVER
    )
    return item
}

inline fun Toolbar.subMenu(title: String, init: SubMenu.() -> Unit) {
    val sub = menu.addSubMenu(title)
    init(sub)
//    return sub
}

inline fun Menu.subMenu(titleRes: Int, init: SubMenu.() -> Unit) {
    val sub = this.addSubMenu(titleRes)
    init(sub)
//    return sub
}

data class MenuGroup(val menu: Menu, val id: Int) {
    var enabled: Boolean = true
        set(value) {
            menu.setGroupEnabled(id, value)
            field = value
        }
    var visible: Boolean = true
        set(value) {
            menu.setGroupVisible(id, value)
            field = value
        }

    inline fun menuItem(title: String, color: Int? = null, iconId: Int? = null, showIcon: Boolean = false, init: MenuItem.() -> Unit = {}): MenuItem {
        val showType = if (showIcon) {
            MenuItem.SHOW_AS_ACTION_IF_ROOM
        } else {
            MenuItem.SHOW_AS_ACTION_NEVER
        }
        val item = menu.add(id, Menu.NONE, Menu.NONE, title)
        if (iconId != null) {
            item.icon = App.instance.getDrawable(iconId)
            val color = color ?: if (UserPrefs.useDarkTheme.value) {
                Color.WHITE
            } else Color.BLACK
            item.icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        init.invoke(item)
        item.setShowAsAction(showType)
        return item
    }
    inline fun menuItem(titleRes: Int, color: Int? = null, iconId: Int? = null, showIcon: Boolean = false, init: MenuItem.() -> Unit = {}): MenuItem {
        return menuItem(App.instance.getString(titleRes), color, iconId, showIcon, init)
    }
}

inline fun Menu.group(
    id: Int,
    checkable: Boolean = false,
    exclusive: Boolean = false,
    cb: MenuGroup.() -> Unit
): MenuGroup = run {
    val g = MenuGroup(this, id)
    cb(g)
    setGroupCheckable(g.id, checkable, exclusive)
    g
}

fun MenuItem.onClick(
    context: CoroutineContext = UI,
    handler: suspend (v: MenuItem) -> Unit
) {
    setOnMenuItemClickListener { v ->
        async(context) {
            handler(v)
        }
        true
    }
}

inline fun <T> measureTime(tag: CharSequence, block: () -> T): T {
    val start = System.nanoTime()
    val res = block()
    println("$tag took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)}ms")
    return res
}