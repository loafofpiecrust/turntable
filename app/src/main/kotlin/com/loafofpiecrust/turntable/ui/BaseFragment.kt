package com.loafofpiecrust.turntable.ui

import activitystarter.ActivityStarter
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.FragmentArgument
import kotlinx.coroutines.experimental.Job
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.ctx
import java.lang.ref.WeakReference

abstract class BaseFragment: Fragment(), AnkoLogger {
    /**
     * Allows abstraction over managing channel closing.
     * Use cases (bound to lifecycle):
     * - albums.consumeEach(UI) { ... }
     * - images.map(BG_POOL + jobs) { ...process... }.consumeEach(UI) { ... }
     * - thing.onClick(BG_POOL + jobs) { ...network activity... }
     * - async(BG_POOL + jobs) { ...search stuff... }.then(UI) { ...display... }
     *
     * This implementation still allows coroutines *not* bound to the lifecycle:
     * - thing.onClick(BG_POOL) { ...long processing outlives fragment... }
     *
     * Alternative Considerations:
     * - We could provide versions of other contexts here, like BG_POOL + jobs,
     *   but `UI` is the absolute most common case and we almost *never* want a
     *   UI task that isn't bound to the lifecycle. Background tasks are more ambiguous.
     */
    protected val jobs = Job()
    protected val UI = kotlinx.coroutines.experimental.android.UI + jobs

    abstract fun ViewManager.createView(): View

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        onCreate()
    }

    private var isFirstInit = true
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (savedInstanceState != null) {
            isFirstInit = false
        }
        return view ?: AnkoContext.create(requireContext(), this).createView()
            .also { isFirstInit = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        jobs.cancel()
    }

    /**
     * Fills a view with the given nested fragment.
     * If the child fragment is being restored, we won't replace it :)
     */
    fun View.fragment(alwaysReplace: Boolean = false, createFragment: () -> Fragment) {
        if (isFirstInit || alwaysReplace) {
            info { "creating fragment" }
            // BEWARE: This will crash if within a navigable fragment!!
            if (this.id == View.NO_ID) {
                this.id = View.generateViewId()
            }

            childFragmentManager.beginTransaction()
                .replace(this.id, createFragment())
                .commit()
        }
    }
}

abstract class BaseDialogFragment: DialogFragment(), AnkoLogger {
    private val jobs = Job()
    val UI get() = kotlinx.coroutines.experimental.android.UI + jobs
    abstract fun ViewManager.createView(): View?

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
//        existingContentView = null
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        onCreate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        ActivityStarter.fill(this)
        return AnkoContext.create(requireContext(), this).createView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        jobs.cancel()
    }

    fun show(ctx: Context) {
        if (ctx is FragmentActivity) {
            super.show(ctx.supportFragmentManager, this.javaClass.simpleName)
        }
    }
}

abstract class BaseActivity: AppCompatActivity(), AnkoLogger {
    private val jobs = Job()
    val UI get() = kotlinx.coroutines.experimental.android.UI + jobs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        setTheme(R.style.AppTheme)
        setContentView(AnkoContext.create(this, this).createView())
    }

    abstract fun ViewManager.createView(): View

    override fun onDestroy() {
        super.onDestroy()
        jobs.cancel()
    }

    inline fun <reified T: Fragment> View.fragment(
        fragment: T,
        manager: FragmentManager? = supportFragmentManager
    ): T {
        if (this.id == View.NO_ID) {
            this.id = View.generateViewId()
        }
        manager?.beginTransaction()
            ?.add(this.id, fragment)
            ?.commit()
        return fragment
    }
}
