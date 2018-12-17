package com.loafofpiecrust.turntable.ui

import activitystarter.ActivityStarter
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.view.*
import com.github.ajalt.timberkt.Timber
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.UserPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.AnkoContext
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface ViewComponentScope: CoroutineScope {
    val job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    fun <T> ReceiveChannel<T>.consumeEachAsync(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend (T) -> Unit
    ) {
        launch(context) {
            consumeEach { action(it) }
        }
    }
    fun <T> BroadcastChannel<T>.consumeEachAsync(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend (T) -> Unit
    ) = openSubscription().consumeEachAsync(context, action)
}

abstract class BaseFragment: Fragment(), ViewComponentScope {
    /**
     * Allows abstraction over managing channel closing.
     * Use cases (bound to lifecycle):
     * - albums.consumeEach(UI) { ... }
     * - images.map(BG_POOL + job) { ...process... }.consumeEach(UI) { ... }
     * - thing.onClick(BG_POOL + job) { ...network activity... }
     * - async(BG_POOL + job) { ...search stuff... }.then(UI) { ...display... }
     *
     * This implementation still allows coroutines *not* bound to the lifecycle:
     * - thing.onClick(BG_POOL) { ...long processing outlives fragment... }
     *
     * Alternative Considerations:
     * - We could provide versions of other contexts here, like BG_POOL + job,
     *   but `UI` is the absolute most common case and we almost *never* want a
     *   UI task that isn't bound to the lifecycle. Background tasks are more ambiguous.
     */
    override val job = SupervisorJob()

    abstract fun ViewManager.createView(): View

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.createOptions()
    }

    open fun Menu.createOptions() {}

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        ActivityStarter.fill(this)
        onCreate()
    }

    private var isFirstInit = true
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (savedInstanceState != null) {
            isFirstInit = false
        }
        return (view ?: AnkoContext.create(requireContext(), this).createView())
            .also { isFirstInit = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancelChildren()
    }

    /**
     * Fills a view with the given nested fragment.
     * If the child fragment is being restored, we won't replace it :)
     */
    fun View.fragment(alwaysReplace: Boolean = false, createFragment: () -> Fragment) {
        if (isFirstInit || alwaysReplace) {
            Timber.d { "creating fragment" }
            if (this.id == View.NO_ID) {
                // BEWARE: This will crash if within a navigable fragment!!
                this.id = View.generateViewId()
            }

            childFragmentManager.beginTransaction()
                .replace(this.id, createFragment())
                .commit()
        }
    }

    fun show(context: Context) {
        context.replaceMainContent(this, true)
    }
}

abstract class BaseDialogFragment: DialogFragment(), ViewComponentScope {
    override val job = SupervisorJob()

    abstract fun ViewManager.createView(): View?

    open fun onCreate() {}

    override fun onCreate(savedInstanceState: Bundle?) {
//        existingContentView = null
        super.onCreate(savedInstanceState)
//        ActivityStarter.fill(this)
        onCreate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        ActivityStarter.fill(this
        return AnkoContext.create(requireContext(), this).createView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job.cancelChildren()
    }

    fun show(ctx: Context, fullscreen: Boolean = false) {
        if (ctx is FragmentActivity) {
            if (fullscreen) {
                ctx.supportFragmentManager.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(android.R.id.content, this)
                    .addToBackStack(null)
                    .commit()
            } else {
                super.show(ctx.supportFragmentManager, this.javaClass.simpleName)
            }
        }
    }
}

abstract class BaseActivity: AppCompatActivity(), ViewComponentScope {
    override val job = SupervisorJob()

    private var isFirstInit = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch(start = CoroutineStart.UNDISPATCHED) {
            val useDark = UserPrefs.useDarkTheme.openSubscription()
            toggleTheme(useDark.receive())
            useDark.consumeEach {
                recreate()
            }
        }

        isFirstInit = savedInstanceState == null
        ActivityStarter.fill(this, savedInstanceState)
        setContentView(AnkoContext.create(this, this).createView())
    }

    private fun toggleTheme(isDark: Boolean) {
        val theme = if (isDark) {
            R.style.AppTheme_Dark
        } else {
            R.style.AppTheme_Light
        }
//        application.setTheme(theme)
        setTheme(theme)
    }

    abstract fun ViewManager.createView(): View

    override fun onDestroy() {
        super.onDestroy()
        job.cancelChildren()
    }

    fun View.fragment(alwaysReplace: Boolean = false, createFragment: () -> Fragment) {
        if (isFirstInit || alwaysReplace) {
            Timber.d { "creating fragment" }
            // BEWARE: This will crash if within a navigable fragment!!
            if (this.id == View.NO_ID) {
                this.id = View.generateViewId()
            }

            supportFragmentManager.beginTransaction()
                .replace(this.id, createFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        currentWeak = WeakReference(this)
    }

    companion object {
        private var currentWeak: WeakReference<BaseActivity>? = null
        val current: BaseActivity? get() = currentWeak?.get()
    }
}

abstract class BaseService: Service(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancelChildren()
    }
}