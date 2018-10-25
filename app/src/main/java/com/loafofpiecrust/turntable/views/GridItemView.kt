package com.loafofpiecrust.turntable.views

import android.content.Context
import android.graphics.Typeface
import android.support.constraint.ConstraintSet.CHAIN_PACKED
import android.support.constraint.ConstraintSet.PARENT_ID
import android.support.v7.widget.CardView
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.generateChildrenIds
import com.loafofpiecrust.turntable.util.size
import com.loafofpiecrust.turntable.util.textStyle
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.sdk27.coroutines.textChangedListener

class GridItemView(
    context: Context,
    maxTextLines: Int = 3
): CardView(context) {
    init {
        id = R.id.card
        cardElevation = dimen(R.dimen.medium_elevation).toFloat()
        radius = dimen(R.dimen.card_corner_radius).toFloat()
    }

    private val constrain = constraintLayout {
        layoutParams = ViewGroup.LayoutParams(matchParent, matchParent)
    }

    val thumbnail = constrain.imageView(R.drawable.ic_default_album) {
        id = R.id.image
        scaleType = ImageView.ScaleType.CENTER_CROP
        adjustViewBounds = false
    }

//    private val textLines = constrain.verticalLayout {
//        gravity = Gravity.CENTER_VERTICAL
//    }

    val mainLine = constrain.textView {
        id = R.id.mainLine
        textStyle = Typeface.BOLD
        maxLines = maxTextLines - 1
    }

    val subLine = constrain.textView {
        id = R.id.subLine
        textSizeDimen = R.dimen.small_text_size
        lines = 1
        textChangedListener {
            afterTextChanged {
                lines = if (text.isEmpty()) {
                    mainLine.lines = maxTextLines
                    mainLine.gravity = Gravity.CENTER_VERTICAL
                    0
                } else {
                    1
                }
            }
        }
    }

    init {
        constrain.apply {
            generateChildrenIds()
            applyConstraintSet {
                val textPadding = dimen(R.dimen.text_content_margin)
                val linesGap = dimen(R.dimen.text_lines_gap)
                thumbnail {
                    connect(
                        TOP to TOP of PARENT_ID,
                        START to START of PARENT_ID,
                        END to END of PARENT_ID
                    )
                    size = matchConstraint
                    dimensionRation = "H,1:1"
                }
                mainLine {
                    connect(
                        START to START of PARENT_ID margin textPadding,
                        END to END of PARENT_ID margin textPadding,
                        TOP to BOTTOM of thumbnail margin linesGap,
                        BOTTOM to TOP of subLine
                    )
                    width = matchConstraint
                    verticalChainStyle = CHAIN_PACKED
                }
                subLine {
                    connect(
                        START to START of mainLine,
                        END to END of mainLine,
                        TOP to BOTTOM of mainLine,
                        BOTTOM to BOTTOM of PARENT_ID margin linesGap
                    )
                    width = matchConstraint
                }
            }
        }
    }
}