package com.loafofpiecrust.turntable.ui

import android.view.View
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.universal.ParcelableComponent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.topPadding
import org.jetbrains.anko.verticalLayout


@Parcelize
class HeaderContainer(
    private val headerTitle: String,
    private val child: ParcelableComponent
): UIComponent(), ParcelableComponent {
    override fun ViewContext.render(): View = verticalLayout {
        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            toolbar {
                standardStyle()
                title = headerTitle
            }
        }

        renderChild(child)
    }
}