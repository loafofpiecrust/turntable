package com.loafofpiecrust.turntable.ui

import android.animation.ValueAnimator
import android.content.Context
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.loafofpiecrust.turntable.R
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.dimen
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.sdk27.coroutines.onClick

class MultiSheetView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : CoordinatorLayout(context, attrs, defStyleAttr) {

    constructor(context: Context, init: MultiSheetView.() -> Unit): this(context) {
        block = init
        block.invoke(this)

        mainContainer = frameLayout {
            mainContentInit?.invoke(this)
            layoutParams = CoordinatorLayout.LayoutParams(matchParent, matchParent).apply {
                bottomMargin = sheet1PeekHeight
            }
        }

        sheet1 = frameLayout {
            sheet1Container = frameLayout {
                firstSheetInit?.invoke(this)
            }.lparams(matchParent, matchParent) {
//                bottomMargin = sheet1PeekHeight // bottomsheet peek 1 height
            }
            sheet1Peek = frameLayout {
                firstSheetPeekInit?.invoke(this)
            }.lparams(width=matchParent, height=sheet1PeekHeight)

            coordinatorLayout {
                sheet2 = frameLayout {
                    sheet2Container = frameLayout {
                        secondSheetInit?.invoke(this)
                    }.lparams(matchParent, matchParent)

                    sheet2Peek = frameLayout {
                        secondSheetPeekInit?.invoke(this)
                    }.lparams(width=matchParent, height=sheet2PeekHeight)
                }.lparams(width=matchParent, height=sheet2PeekHeight*3) {
                    behavior = CustomBottomSheetBehavior<FrameLayout>().apply {
                        peekHeight = sheet2PeekHeight
                    }.also { bottomSheetBehavior2 = it }
                }
            }.lparams(matchParent, matchParent)
        }.apply {
            layoutParams = CoordinatorLayout.LayoutParams(matchParent, matchParent).apply {
                behavior = CustomBottomSheetBehavior<FrameLayout>().apply {
                    peekHeight = dimen(R.dimen.song_item_height)
                }.also { bottomSheetBehavior1 = it }
            }
        }

//        bottomSheetBehavior1 = BottomSheetBehavior.from(sheet1) as CustomBottomSheetBehavior<*>
        bottomSheetBehavior1.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                fadeView(sheet1Peek, newState)

                sheetStateChangeListener?.onSheetStateChanged(Sheet.FIRST, newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                fadeView(sheet1Peek, slideOffset)
                sheetStateChangeListener?.onSlide(Sheet.FIRST, slideOffset)
            }
        })

//        val sheet2 = findViewById<View>(R.id.sheet2)
//        bottomSheetBehavior2 = BottomSheetBehavior.from(sheet2) as CustomBottomSheetBehavior<*>
        bottomSheetBehavior2.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_DRAGGING) {
                    bottomSheetBehavior1.allowDragging = false
                } else {
                    bottomSheetBehavior1.allowDragging = true
                }

                fadeView(sheet2Peek, newState)

                sheetStateChangeListener?.onSheetStateChanged(Sheet.SECOND, newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                bottomSheetBehavior1.allowDragging = false
                fadeView(sheet2Peek, slideOffset)

                sheetStateChangeListener?.onSlide(Sheet.SECOND, slideOffset)
            }
        })

        //First sheet view click listener
        sheet1Peek.onClick { v -> expandSheet(Sheet.FIRST) }

        //Second sheet view click listener
        sheet2Peek.onClick { v -> expandSheet(Sheet.SECOND) }

        sheet2Peek.setOnTouchListener { v, event ->
            bottomSheetBehavior1.allowDragging = false
            bottomSheetBehavior2.allowDragging = true
            false
        }
    }

    lateinit var block: MultiSheetView.() -> Unit

    private lateinit var bottomSheetBehavior1: CustomBottomSheetBehavior<*>
    private lateinit var  bottomSheetBehavior2: CustomBottomSheetBehavior<*>

    private var sheetStateChangeListener: SheetStateChangeListener? = null

    private val sheet1PeekHeight: Int = dimen(R.dimen.song_item_height)
    private val sheet2PeekHeight: Int = dimen(R.dimen.song_item_height) * 2 + dimen(R.dimen.small_text_size) + dimen(R.dimen.text_content_margin)

    val isHidden: Boolean
        get() = bottomSheetBehavior1.peekHeight < sheet1PeekHeight

    /**
     * @return the currently expanded Sheet
     */
    private val currentSheet: Sheet
        get() = when {
            bottomSheetBehavior2.state == BottomSheetBehavior.STATE_EXPANDED -> Sheet.SECOND
            bottomSheetBehavior1.state == BottomSheetBehavior.STATE_EXPANDED -> Sheet.FIRST
            else -> Sheet.NONE
        }

//    val mainContainerResId: Int
//        @IdRes
//        get() = R.id.mainContainer

    interface SheetStateChangeListener {
        fun onSheetStateChanged(sheet: Sheet, @BottomSheetBehavior.State state: Int)

        fun onSlide(sheet: Sheet, slideOffset: Float)
    }

    enum class Sheet {
        NONE,
        FIRST,
        SECOND
    }


    fun mainContent(init: FrameLayout.() -> Unit) {
        mainContentInit = init
    }

    fun firstSheet(init: FrameLayout.() -> Unit) {
        firstSheetInit = init
    }
    fun firstSheetPeek(init: FrameLayout.() -> Unit) {
        firstSheetPeekInit = init
    }

    fun secondSheet(init: FrameLayout.() -> Unit) {
        secondSheetInit = init
    }
    fun secondSheetPeek(init: FrameLayout.() -> Unit) {
        secondSheetPeekInit = init
    }


    private var mainContentInit: (FrameLayout.() -> Unit)? = null
    private var firstSheetInit: (FrameLayout.() -> Unit)? = null
    private var firstSheetPeekInit: (FrameLayout.() -> Unit)? = null
    private var secondSheetInit: (FrameLayout.() -> Unit)? = null
    private var secondSheetPeekInit: (FrameLayout.() -> Unit)? = null

    private lateinit var sheet1: View
    private lateinit var sheet1Container: View
    private lateinit var sheet1Peek: View

    private lateinit var sheet2: View
    private lateinit var sheet2Container: View
    private lateinit var sheet2Peek: View

    private lateinit var mainContainer: View

    fun onSheetStateChanged(block: (sheet: Sheet, state: Int) -> Unit) {
        sheetStateChangeListener = object: SheetStateChangeListener {
            override fun onSheetStateChanged(sheet: Sheet, state: Int) {
                block(sheet, state)
            }

            override fun onSlide(sheet: Sheet, slideOffset: Float) {}
        }
    }

    fun setSheetStateChangeListener(sheetStateChangeListener: SheetStateChangeListener?) {
        this.sheetStateChangeListener = sheetStateChangeListener
    }

    private fun expandSheet(sheet: Sheet) {
        when (sheet) {
            Sheet.FIRST -> bottomSheetBehavior1.setState(BottomSheetBehavior.STATE_EXPANDED)
            Sheet.SECOND -> bottomSheetBehavior2.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }

    private fun collapseSheet(sheet: Sheet) {
        when (sheet) {
            Sheet.FIRST -> bottomSheetBehavior1.setState(BottomSheetBehavior.STATE_COLLAPSED)
            Sheet.SECOND -> bottomSheetBehavior2.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    /**
     * Sets the peek height of sheet one to 0.
     *
     * @param collapse true if all expanded sheets should be collapsed.
     * @param animate  true if the change in peek height should be animated
     */
    fun hide(collapse: Boolean, animate: Boolean) {
        if (!isHidden) {
            val peekHeight = sheet1PeekHeight
            if (animate) {
                val valueAnimator = ValueAnimator.ofInt(peekHeight, 0)
                valueAnimator.duration = 200
                valueAnimator.addUpdateListener { valueAnimator1 -> bottomSheetBehavior1.setPeekHeight(valueAnimator1.animatedValue as Int) }
                valueAnimator.start()
            } else {
                bottomSheetBehavior1.setPeekHeight(0)
            }
            (mainContainer.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = 0
            if (collapse) {
                goToSheet(Sheet.NONE)
            }
        }
    }

    /**
     * Restores the peek height to its default value.
     *
     * @param animate true if the change in peek height should be animated
     */
    fun unhide(animate: Boolean) {
        if (isHidden) {
            val peekHeight = sheet1PeekHeight
            val currentHeight = bottomSheetBehavior1.peekHeight
            val ratio = (1 - currentHeight / peekHeight).toFloat()
            if (animate) {
                val valueAnimator = ValueAnimator.ofInt(bottomSheetBehavior1.peekHeight, peekHeight)
                valueAnimator.duration = (200 * ratio).toLong()
                valueAnimator.addUpdateListener { valueAnimator1 -> bottomSheetBehavior1.setPeekHeight(valueAnimator1.animatedValue as Int) }
                valueAnimator.start()
            } else {
                bottomSheetBehavior1.setPeekHeight(peekHeight)
            }
            (mainContainer.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = peekHeight
        }
    }

    /**
     * Expand the passed in sheet, collapsing/expanding the other sheet(s) as required.
     */
    private fun goToSheet(sheet: Sheet) {
        when (sheet) {
            Sheet.NONE -> {
                collapseSheet(Sheet.FIRST)
                collapseSheet(Sheet.SECOND)
            }
            Sheet.FIRST -> {
                collapseSheet(Sheet.SECOND)
                expandSheet(Sheet.FIRST)
            }
            Sheet.SECOND -> {
                expandSheet(Sheet.FIRST)
                expandSheet(Sheet.SECOND)
            }
        }
    }

    fun restoreSheet(sheet: Sheet) {
        goToSheet(sheet)
        fadeView(sheet1Peek, bottomSheetBehavior1.state)
        fadeView(sheet2Peek, bottomSheetBehavior2.state)
    }

    fun consumeBackPress(): Boolean = when (currentSheet) {
        Sheet.SECOND -> {
            collapseSheet(Sheet.SECOND)
            true
        }
        Sheet.FIRST -> {
            collapseSheet(Sheet.FIRST)
            true
        }
        else -> false
    }

//    @SuppressLint("DefaultLocale")
//    @IdRes
//    fun getSheetContainerViewResId(sheet: Sheet): Int {
//        when (sheet) {
//            Sheet.FIRST -> return R.id.sheet1Container
//            Sheet.SECOND -> return R.id.sheet2Container
//        }
//
//        throw IllegalStateException(String.format("No container view resId found for sheet: %d", sheet))
//    }

//    @SuppressLint("DefaultLocale")
//    @IdRes
//    fun getSheetPeekViewResId(@Sheet sheet: Int): Int {
//        when (sheet) {
//            Sheet.FIRST -> return R.id.sheet1Peek
//            Sheet.SECOND -> return R.id.sheet2PeekView
//        }
//
//        throw IllegalStateException(String.format("No peek view resId found for sheet: %d", sheet))
//    }

    private fun fadeView(sheet: View, @BottomSheetBehavior.State state: Int) {
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            fadeView(sheet, 1f)
        } else if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            fadeView(sheet, 0f)
        }
    }

    private fun fadeView(v: View, offset: Float) {
        val alpha = 1 - offset
        v.alpha = alpha
        v.visibility = if (alpha == 0f) View.GONE else View.VISIBLE
    }

    companion object {

        /**
         * A helper method to return the first MultiSheetView parent of the passed in View,
         * or null if none can be found.
         *
         * @param v the view whose hierarchy will be traversed.
         * @return the first MultiSheetView of the passed in view, or null if none can be found.
         */
        private fun getParentMultiSheetView(v: View?): MultiSheetView? {
            if (v == null) return null

            if (v is MultiSheetView) {
                return v
            }

            return if (v.parent is View) {
                getParentMultiSheetView(v.parent as View)
            } else null

        }
    }
}