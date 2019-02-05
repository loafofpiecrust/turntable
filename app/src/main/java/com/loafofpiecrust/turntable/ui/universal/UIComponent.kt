package com.loafofpiecrust.turntable.ui.universal

import android.app.Activity
import android.content.Context
import android.os.Parcelable
import android.support.annotation.CallSuper
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.loafofpiecrust.turntable.ui.BaseActivity
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.ui.replaceMainContent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.AlertBuilder
import org.jetbrains.anko.AnkoComponent
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.sdk27.coroutines.onAttachStateChangeListener
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface IUIComponent: CoroutineScope, AnkoComponent<Any> {
    fun ViewContext.render(): View
}

interface ParcelableComponent: IUIComponent, Parcelable

fun IUIComponent.createView(context: Context): View {
    return createView(AnkoContext.create(context))
}

fun IUIComponent.createView(parent: ViewGroup): View {
    return createView(AnkoContext.createDelegate(parent))
}

/// Implement AnkoComponent to access the Anko preview compiler.
abstract class UIComponent: IUIComponent {
    private val supervisor = SupervisorJob()
    private var viewScope: CoroutineScope? = null
    final override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisor


    final override fun createView(ui: AnkoContext<Any>): View {
        val viewScope = object: CoroutineScope {
            val job = SupervisorJob(supervisor)
            override val coroutineContext
                get() = Dispatchers.Main + job
        }
        this.viewScope = viewScope
        return ViewContext(viewScope, ui).render().also { view ->
            view.onAttachStateChangeListener {
                onViewDetachedFromWindow {
                    viewScope.job.cancelChildren()
                }
            }
            this.viewScope = null
        }
    }

    fun ViewGroup.renderChild(child: IUIComponent) =
        child.createView(this)

    fun ViewContext.renderChild(child: IUIComponent) =
        child.run { render() }

    open fun Fragment.onCreate() {}
    open fun Activity.onCreate() {}
    open fun Menu.prepareOptions(context: Context) {}

    open fun onPause() {}
    open fun onResume() {}

    @CallSuper
    open fun onDestroy() {
        supervisor.cancelChildren()
    }

    @CallSuper
    open fun onDestroyView() {
//        viewScope.cancelChildren()
    }

    fun pushToMain() {
        BaseActivity.current?.replaceMainContent(this.createFragment())
    }

    fun <T> ReceiveChannel<T>.consumeEachAsync(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend (T) -> Unit
    ) {
        val scope = viewScope ?: this@UIComponent
        scope.launch(context, start = CoroutineStart.UNDISPATCHED) {
            consumeEach { action(it) }
        }
    }

    fun <T> BroadcastChannel<T>.consumeEachAsync(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend (T) -> Unit
    ) = openSubscription().consumeEachAsync(context, action)

//    operator fun <T> ViewGroup.invoke(block: ViewGroup.() -> Unit): View {
//        return createView(this).apply {
//            block()
//        }
//    }
}

abstract class DialogComponent: UIComponent() {
    open fun AlertBuilder<*>.prepare() {}
    fun dismiss() {
        BaseActivity.current?.supportFragmentManager?.popBackStack()
    }
}

class ViewContext(
    val scope: CoroutineScope,
    val context: AnkoContext<Any>
): CoroutineScope by scope, AnkoContext<Any> by context