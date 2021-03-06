package com.loafofpiecrust.turntable.views

import android.support.constraint.ConstraintSet.CHAIN_PACKED
import android.support.constraint.ConstraintSet.PARENT_ID
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.style.ripple
import com.loafofpiecrust.turntable.util.iconButton
import com.loafofpiecrust.turntable.util.size
import com.loafofpiecrust.turntable.util.tint
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.sdk27.coroutines.textChangedListener


open class RecyclerListItem(
    parent: ViewGroup,
    maxTextLines: Int = 3,
    fullSizeImage: Boolean = false
): RecyclerItem(AnkoContext.create(parent.context, parent).constraintLayout {
    id = R.id.card
    ripple()

    val textPadding = (dimen(R.dimen.text_content_margin) * 0.75).toInt()
    lparams(width = matchParent, height = (dimen(R.dimen.subtitle_text_size) * maxOf(2.2f, maxTextLines.toFloat())).toInt() + textPadding * 2)

    val mainLine = textView {
        id = R.id.mainLine
        textSizeDimen = R.dimen.subtitle_text_size
        maxLines = maxTextLines - 1
    }

    val subLine = textView {
        id = R.id.subLine
        maxLines = 1
//        textSizeDimen = R.dimen.small_text_size
        visibility = View.GONE
        textChangedListener {
            afterTextChanged {
                visibility = if (text.isEmpty()) {
                    View.GONE
                } else View.VISIBLE
            }
        }
    }

    val track = textView {
        id = R.id.track
        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
    }

    val statusIcon = imageView {
        id = R.id.image
    }

    val overflow = iconButton(R.drawable.ic_overflow) {
        id = R.id.itemMenuDots
        tint = context.colorAttr(android.R.attr.textColor)
    }

    val progress = horizontalProgressBar {
        id = R.id.progressBg
        visibility = View.GONE
        isIndeterminate = false
        progressTintList = resources.getColorStateList(R.color.md_green_400)
        progressDrawable = context.getDrawable(R.drawable.progress_ring)
    }

    applyConstraintSet {
        track {
            connect(
                START to START of PARENT_ID,
                TOP to TOP of mainLine,
                BOTTOM to BOTTOM of subLine
            )
            width = dimen(R.dimen.overflow_icon_space)
        }
        statusIcon {
            connect(
                START to START of track,
                END to END of track,
                TOP to TOP of mainLine,
                BOTTOM to BOTTOM of subLine
            )
            dimensionRation = "H,1:1"
            size = if (fullSizeImage) {
                dip(40)
            } else {
                dimen(R.dimen.icon_size)
            }
        }
        mainLine {
            connect(
                TOP to TOP of PARENT_ID,
                END to START of overflow margin dip(16),
                BOTTOM to TOP of subLine,
                START to END of track
            )
            verticalChainStyle = CHAIN_PACKED
            width = matchConstraint
        }
        subLine {
            connect(
                TOP to BOTTOM of mainLine,
                START to START of mainLine,
                END to END of mainLine,
                BOTTOM to BOTTOM of PARENT_ID
            )
            width = matchConstraint
        }
        overflow {
            connect(
                END to END of PARENT_ID margin dimen(R.dimen.text_content_margin),
                TOP to TOP of mainLine,
                BOTTOM to BOTTOM of subLine
            )
            size = dimen(R.dimen.overflow_icon_size)
        }
        progress {
            connect(
                BOTTOM to BOTTOM of PARENT_ID,
                TOP to TOP of PARENT_ID,
                START to START of overflow,
                END to END of overflow
            )
            size = dimen(R.dimen.overflow_icon_size)
        }
    }
}) {
    val track: TextView = itemView.find(R.id.track)
    val menu: ImageButton = itemView.find(R.id.itemMenuDots)
    val progressBar: ProgressBar = itemView.findViewById(R.id.progressBg)
//    val playingIcon: ImageView = itemView.findViewById(R.uuid.playing_icon)
//    val statusIcon: ImageView = itemView.find(R.id.status_icon)
}