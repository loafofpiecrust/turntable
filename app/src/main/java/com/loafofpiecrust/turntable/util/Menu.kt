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
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.prefs.UserPrefs
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import kotlin.coroutines.experimental.CoroutineContext


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
    title: String,
    @DrawableRes iconId: Int? = null,
    @ColorInt color: Int? = null,
    showIcon: Boolean = false,
    init: MenuItem.() -> Unit = {}
): MenuItem {
    val item = add(title)
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

inline fun Menu.menuItem(
    @StringRes titleRes: Int,
    @DrawableRes iconId: Int? = null,
    @ColorInt color: Int? = null,
    showIcon: Boolean = false,
    init: MenuItem.() -> Unit = {}
) = menuItem(App.instance.getString(titleRes), iconId, color, showIcon, init)

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
    context: CoroutineContext = Dispatchers.Main,
    handler: suspend (v: MenuItem) -> Unit
) {
    setOnMenuItemClickListener { v ->
        GlobalScope.launch(context) {
            handler(v)
        }
        true
    }
}