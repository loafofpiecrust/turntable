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
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.support.v4.ctx
import java.lang.ref.WeakReference

abstract class BaseFragment: Fragment(), AnkoLogger {
//    private var existingContentView: WeakReference<View>? = null
    protected var jobs = Job()
    val UI get() = kotlinx.coroutines.experimental.android.UI + jobs

    abstract fun ViewManager.createView(): View

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStarter.fill(this)
//        StateSaver.restoreInstanceState(this, savedInstanceState)
        onCreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
//        StateSaver.saveInstanceState(this, outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ActivityStarter.fill(this)
//        if (existingContentView?.get() == null) {
//            existingContentView = WeakReference(createView(AnkoContext.create(ctx, this)))
//        }
//        return existingContentView?.get()
        jobs = Job()
        return AnkoContext.create(ctx, this).createView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        jobs.cancel()
    }

    fun <T> ReceiveChannel<T>.connect() = connect(WeakReference(lifecycle))
    fun <T> BroadcastChannel<T>.connect() = openSubscription().connect()


    inline fun <reified T: Fragment> View.fragment(
        fragment: T,
        manager: FragmentManager? = fragmentManager
    ): T {
        if (this.id == View.NO_ID) {
            this.id = View.generateViewId()
        }
        manager?.beginTransaction()
            ?.add(this.id, fragment)
            ?.commit()
        return fragment
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        jobs.cancel()
//    }

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
        return AnkoContext.create(ctx, this).createView()
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

//    fun <T> task(ctx: CoroutineContext = BG_POOL, block: suspend () -> T): Deferred<T> {
//        return async(ctx, parent = jobs) { block() }
//    }
}