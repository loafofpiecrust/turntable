package com.loafofpiecrust.turntable.views

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.textStyle
import org.jetbrains.anko.*

class SimpleHeaderViewHolder(
    parent: ViewGroup
) : SectionedViewHolder(AnkoContext.create(parent.context, parent).frameLayout {
    val outerPadding = dimen(R.dimen.text_content_margin)
    padding = outerPadding
    bottomPadding = outerPadding / 2

    textView {
        id = R.id.mainLine
        textStyle = Typeface.BOLD
    }
}) {
    val mainLine: TextView = itemView.find(R.id.mainLine)
}