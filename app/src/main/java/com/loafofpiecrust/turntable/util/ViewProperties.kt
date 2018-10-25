package com.loafofpiecrust.turntable.util

import android.content.Context
import android.graphics.Color
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.jetbrains.anko.childrenSequence
import org.jetbrains.anko.constraint.layout.ViewConstraintBuilder

var TextView.textStyle: Int
    get() = typeface?.style ?: 0
    set(value) = setTypeface(null, value)

var ImageView.tintResource: Int
    @Deprecated("Cannot retrieve resource back from color", level = DeprecationLevel.ERROR)
    get() = 0
    inline set(id) {
        setColorFilter(context.getColorCompat(id))
    }

var ImageView.tint: Int
    @Deprecated("Cannot retrieve resource back from color", level = DeprecationLevel.ERROR)
    get() = 0
    inline set(color) {
        setColorFilter(color)
    }

var ViewGroup.LayoutParams.size: Int
    inline get() = width
    inline set(value) {
        width = value
        height = value
    }

var ViewConstraintBuilder.size: Int
    @Deprecated("No getter for size", level = DeprecationLevel.ERROR)
    get() = TODO()
    inline set(value) {
        width = value
        height = value
    }

fun View.generateChildrenIds() {
    if (id == View.NO_ID) {
        id = View.generateViewId()
    }
    childrenSequence().forEach { v ->
        if (v.id == View.NO_ID) {
            v.id = View.generateViewId()
        }
    }
}

val Int.complementaryColor: Int
    @ColorInt get() {
        val hsv = FloatArray(3)
        Color.RGBToHSV(
            Color.red(this),
            Color.green(this),
            Color.blue(this),
            hsv
        )
        hsv[0] = (hsv[0] + 180) % 360
        return Color.HSVToColor(hsv)
    }

fun Context.getColorCompat(@ColorRes res: Int) = ContextCompat.getColor(this, res)