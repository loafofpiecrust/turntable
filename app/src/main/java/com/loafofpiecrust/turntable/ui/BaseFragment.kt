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
import kotlinx.coroutines.experimental.Job
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.support.v4.ctx
import java.lang.ref.WeakReference

abstract class BaseFragment: Fragment(), AnkoLogger {
    private var existingContentView: WeakReference<View>? = null
    protected val jobs = Job()
    val UI = kotlinx.coroutines.experimental.android.UI + jobs

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
        if (existingContentView?.get() == null) {
            existingContentView = WeakReference(makeView(AnkoContext.create(ctx, this)))
        }
        return existingContentView?.get()
    }

//    fun <T> task(ctx: CoroutineContext = BG_POOL, block: suspend () -> T): Deferred<T> {
//        return async(ctx, parent = jobs) { block() }
//    }
//
//    fun <T: Any> subscribeTo(obs: Observable<T>, sub: (T) -> Unit) {
//        subscriptions.add(obs.subscribe(sub))
//    }

//    override fun onSaveInstanceState(outState: Bundle?) {
//        super.onSaveInstanceState(outState)
//        ActivityStarter.save(this, outState)
//    }

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