package com.loafofpiecrust.turntable.ui.universal

import android.app.Activity
import android.content.Context
import android.os.Parcelable
import android.support.annotation.CallSuper
import android.support.v4.app.Fragment
import android.view.Menu
import android.view.View
import android.view.ViewGroup
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

fun IUIComponent.createView(context: Context, container: Closable): View {
    return createView(AnkoContext.create(context, container))
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
                    viewScope.job.cancel()
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
    open fun AlertBuilder<*>.prepare() {}
    open fun Menu.prepareOptions(context: Context) {}

    open fun onPause() {}
    open fun onResume() {}

    fun onDestroy() {
        supervisor.cancel()
    }

    @CallSuper
    open fun onDestroyView() {
//        viewScope.cancelChildren()
    }

    fun pushToMain(context: Context) {
        if (context is UniversalActivity) {
            (context.component as? Navigable)?.push(this)
        }
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

class ViewContext(
    val scope: CoroutineScope,
    val context: AnkoContext<Any>
): CoroutineScope by scope, AnkoContext<Any> by context