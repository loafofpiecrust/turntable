package com.loafofpiecrust.turntable.ui

import activitystarter.ActivityStarter
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.support.v4.ctx
import kotlin.coroutines.experimental.CoroutineContext

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

    abstract fun makeView(ui: ViewManager): View

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
//        existingContentView = null
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        onCreate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        ActivityStarter.fill(this)
        return makeView(AnkoContext.create(ctx, this))
    }

    override fun onDestroy() {
        super.onDestroy()
        jobs.cancel()
    }
}

abstract class BaseDialogFragment: DialogFragment(), AnkoLogger {
    private val jobs = Job()
    val UI get() = kotlinx.coroutines.experimental.android.UI + jobs
    abstract fun makeView(parent: ViewGroup?, manager: ViewManager): View

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
//        existingContentView = null
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        onCreate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        ActivityStarter.fill(this)
        return makeView(container, AnkoContext.create(ctx, this))
    }

    fun show() {
        super.show(MainActivity.latest.supportFragmentManager, this.javaClass.simpleName)
    }
}

abstract class BaseActivity: AppCompatActivity(), AnkoLogger {
    private val jobs = Job()
    val UI get() = kotlinx.coroutines.experimental.android.UI + jobs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
        setTheme(R.style.AppTheme)
        setContentView(makeView(AnkoContext.create(this, this)))
    }

    abstract fun makeView(ui: ViewManager): View

    override fun onDestroy() {
        super.onDestroy()
        jobs.cancel()
    }

//    fun <T> task(ctx: CoroutineContext = BG_POOL, block: suspend () -> T): Deferred<T> {
//        return async(ctx, parent = jobs) { block() }
//    }
}
