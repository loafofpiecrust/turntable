package com.loafofpiecrust.turntable.ui

import android.support.constraint.ConstraintSet.CHAIN_PACKED
import android.support.constraint.ConstraintSet.PARENT_ID
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.first
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.sdk25.coroutines.textChangedListener



class RecyclerListItem(
    parent: ViewGroup,
    maxTextLines: Int = 3,
    useIcon: Boolean = false
): RecyclerItem(AnkoContext.create(parent.context, parent).linearLayout {
    val textPadding = (dimen(R.dimen.text_content_margin) * 0.75).toInt()

    id = R.id.card
//    padding = dip(20)
    gravity = Gravity.CENTER_VERTICAL
    lparams(width = matchParent, height = (dimen(R.dimen.subtitle_text_size) * maxOf(2.2f, maxTextLines.toFloat())).toInt() + textPadding * 2)

    backgroundColor = context.getColorCompat(R.color.background)

    frameLayout {
        linearLayout {
            // DL'd progress
            frameLayout {
                id = R.id.progressBg
                backgroundResource = R.color.md_blue_A400
                minimumWidth = 0
            }.lparams(height = matchParent)
        }.lparams(height = matchParent)

        val iconSize = dimen(R.dimen.icon_size)
        linearLayout {
            gravity = Gravity.CENTER_VERTICAL
            leftPadding = dip(12)

            linearLayout {
                id = R.id.status_icon
                visibility = View.GONE
                gravity = Gravity.CENTER
                imageView {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    imageResource = R.drawable.ic_sd_storage
                    task(UI) {
                        setColorFilter(UserPrefs.accentColor.openSubscription().first())
                    }
                }.lparams(width = iconSize, height = iconSize)
            }.lparams(width = dip(28), height = matchParent)

            frameLayout {
                // Track numer / Queue Position / etc.
                linearLayout {
                    gravity = Gravity.CENTER
                    textView {
                        id = R.id.track
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    }.lparams {
                        //        horizontalPadding = dip(6)
                        width = dimen(R.dimen.overflow_icon_space)
                    }
                }.lparams(width= matchParent, height= matchParent)

                if (useIcon) { // Centered icon
                    linearLayout {
                        gravity = Gravity.CENTER
                        iconView {
                            id = R.id.image
                        }
                    }.lparams(width= matchParent, height= matchParent)
                } else { // square image filling height, used for album/artist artwork
                    imageView {
                        id = R.id.image
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.lparams(width= matchParent, height= matchParent)
                }

                // Currently playing icon
                linearLayout {
                    gravity = Gravity.CENTER
                    iconView(R.drawable.ic_play_circle_outline) {
                        id = R.id.playing_icon
                        visibility = View.GONE
                        task(UI) {
                            setColorFilter(UserPrefs.accentColor.openSubscription().first())
                        }
                    }.lparams(width = iconSize, height = iconSize)
                }.lparams(width= matchParent, height= matchParent)
            }.lparams(width = dimen(R.dimen.song_item_height) * 2/3, height = matchParent)


            linearLayout {
                id = R.id.title
                padding = textPadding
                leftPadding = textPadding / 2
                gravity = Gravity.CENTER_VERTICAL
                clipToPadding = false
//        clipToOutline = false

                val mainLine = textView {
                    id = R.id.mainLine
                    maxLines = maxTextLines - 1
                }

                val subLine = textView {
                    id = R.id.subLine
                    maxLines = 1
                    textSizeDimen = R.dimen.small_text_size
                    visibility = View.GONE
                    textChangedListener {
                        afterTextChanged {
                            visibility = if (text.isEmpty()) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                        }
                    }
                }

                if (maxTextLines <= 2) {
                    orientation = LinearLayout.HORIZONTAL
                    mainLine.lparams { weight = 1f }
                    mainLine.maxLines = maxTextLines
                } else {
                    orientation = LinearLayout.VERTICAL
                }
            }.lparams {
                weight = 1f
            }

            val clickSize = dimen(R.dimen.overflow_icon_space)
            val btnSize = dimen(R.dimen.overflow_icon_size)
            linearLayout {
                gravity = Gravity.CENTER

                // Dots context popupMenu
                iconButton(R.drawable.ic_overflow) {
                    id = R.id.itemMenuDots
                }.lparams {
                    width = btnSize
                    height = btnSize
                }

            }.lparams(width = clickSize, height = matchParent)
        }.lparams(height= matchParent, width= matchParent)

    }.lparams(height= matchParent, width= matchParent)
}) {
    val track: TextView = itemView.findViewById(R.id.track)
    val menu: ImageButton = itemView.findViewById(R.id.itemMenuDots)
    val progress: View = itemView.findViewById(R.id.progressBg)
    val playingIcon: ImageView = itemView.findViewById(R.id.playing_icon)
    val statusIcon: View = itemView.findViewById(R.id.status_icon)
}

//class SongItemAdapter<T: IFlexible<*>>: FlexibleAdapter<T>(listOf()) {
//    override fun getItemViewType(position: Int): Int {
//        // TODO: Return whether downloading or not.
//        return super.getItemViewType(position)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
//        val item =
//    }
//}


//class SongListItem: AbstractFlexibleItem<SongListItem.ViewHolder>() {
//
//
//    override fun bindViewHolder(
//        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
//        holder: ViewHolder,
//        position: Int,
//        payloads: MutableList<Any>
//    ) {
//    }
//
//    override fun equals(other: Any?): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun createViewHolder(view: View?, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ViewHolder {
//    }
//
//    override fun getLayoutRes(): Int {
//
//    }
//
//
//    class ViewHolder(view: View, adapter: FlexibleAdapter<SongListItem>): FlexibleViewHolder(view, adapter) {
//
//    }
//}


class RecyclerListItemOptimized(
    parent: ViewGroup,
    maxTextLines: Int = 3,
    useIcon: Boolean = false
): RecyclerItem(AnkoContext.create(parent.context, parent).constraintLayout {
    id = R.id.card

    val textPadding = (dimen(R.dimen.text_content_margin) * 0.75).toInt()
    lparams(width = matchParent, height = (dimen(R.dimen.subtitle_text_size) * maxOf(2.2f, maxTextLines.toFloat())).toInt() + textPadding * 2)

    val mainLine = textView {
        id = R.id.mainLine
        maxLines = maxTextLines - 1
    }

    val subLine = textView {
        id = R.id.subLine
        maxLines = 1
        textSizeDimen = R.dimen.small_text_size
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
        id = R.id.status_icon
    }

    val overflow = iconButton(R.drawable.ic_overflow) {
        id = R.id.itemMenuDots
    }

    val iconSize = dimen(R.dimen.icon_size)
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
            size = iconSize
        }
        mainLine {
            connect(
                TOP to TOP of PARENT_ID,
                START to END of track,
                END to START of overflow margin dip(16),
                BOTTOM to TOP of subLine
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
    }
}) {
    val track: TextView = itemView.find(R.id.track)
    val menu: View = itemView.find(R.id.itemMenuDots)
//    val progress: View = itemView.findViewById(R.id.progressBg)
//    val playingIcon: ImageView = itemView.findViewById(R.id.playing_icon)
    val statusIcon: ImageView = itemView.find(R.id.status_icon)
}