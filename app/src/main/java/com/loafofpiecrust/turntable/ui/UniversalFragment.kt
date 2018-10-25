package com.loafofpiecrust.turntable.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.*
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.util.arg
import com.loafofpiecrust.turntable.util.getValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onAttachStateChangeListener
import org.jetbrains.anko.support.v4.alert
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Closable {
    fun close()
}



/// Implement AnkoComponent to access the Anko preview compiler.
abstract class UIComponent: CoroutineScope, AnkoComponent<Any> {
    private val supervisor = SupervisorJob()
    final override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisor

    fun createView(context: Context, container: Closable): View {
        return createView(AnkoContext.create(context, container))
    }
    fun createView(parent: ViewGroup): View {
        return createView(AnkoContext.createDelegate(parent))
    }

    final override fun createView(ui: AnkoContext<Any>): View {
        val viewScope = object: CoroutineScope {
            val job = SupervisorJob(supervisor)
            override val coroutineContext
                get() = Dispatchers.Main + job
        }
        return viewScope.render(ui).also { view ->
            view.onAttachStateChangeListener {
                onViewDetachedFromWindow {
                    viewScope.job.cancel()
                }
            }
        }
    }
    protected abstract fun CoroutineScope.render(ui: AnkoContext<Any>): View

    open fun Fragment.onCreate() {}
    open fun Activity.onCreate() {}
    open fun AlertBuilder<*>.prepare() {}
    open fun Menu.prepareOptions(context: Context) {}

    fun onDestroy() {
        supervisor.cancelChildren()
    }
    fun onDestroyView() {
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
        launch(context) {
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

/// Allow creating fragments/activities from Parcelable Components
/// Otherwise, only plain views can be created.
fun <T> T.createFragment(): Fragment where T : UIComponent, T: Parcelable {
    return UniversalFragment().also {
        it.component = this
        it.onCreate()
    }
}
fun <T> T.showDialog(context: Context) where T : UIComponent, T: Parcelable {
    UniversalDialogFragment(this).show((context as AppCompatActivity).supportFragmentManager, javaClass.canonicalName)
}
fun <T> T.startActivity(context: Context) where T : UIComponent, T: Parcelable {
    context.startActivity<UniversalActivity>("component" to this)
}

class UniversalDialogFragment(): DialogFragment(), Closable {
    internal constructor(args: UIComponent): this() {
        this.composedArgs = args
    }

    private var composedArgs: UIComponent by arg()

    override fun close() {
        dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composedArgs.apply {
            onCreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return alert {
            customView = composedArgs.createView(requireContext(), this@UniversalDialogFragment)
            composedArgs.apply {
                prepare()
            }
        }.build() as Dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composedArgs.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        composedArgs.onDestroy()
    }
}

class UniversalActivity: AppCompatActivity(), Closable {
    lateinit var component: UIComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = savedInstanceState?.getParcelable<Parcelable>("component") as UIComponent
        setTheme(R.style.AppTheme)
        setContentView(component.run {
            onCreate()
            createView(this@UniversalActivity, this@UniversalActivity)
        })
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putParcelable("component", component as Parcelable)
    }

    override fun onDestroy() {
        super.onDestroy()
        component.onDestroyView()
        component.onDestroy()
    }

    override fun close() {
        finish()
    }
}

class UniversalFragment: Fragment(), Closable {
    var component: UIComponent by arg()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return component.run {
            createView(requireContext(), this@UniversalFragment)
        }.also {
            // Stable method of recursively assigning sibling-unique IDs
            // to every view in the hierarchy.
            // This guarantees that every view can save and restore state.
            val className = component.javaClass.canonicalName
            it.childrenRecursiveSequence().forEachIndexed { index, child ->
                if (child.id == View.NO_ID) {
                    // Assigns a positive unique hash for each view ID
                    child.id = Objects.hash(className, index) and Int.MAX_VALUE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        component.apply {
            menu.prepareOptions(requireContext())
        }
    }

//    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu)
//    }

    override fun onDestroy() {
        super.onDestroy()
        // Only shut down the component when the fragment is being removed.
        // Otherwise, it's a config change and the same component instance
        // will be revived for use in the fragment.
        if (isRemoving) {
            component.onDestroy()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        component.onDestroyView()
    }

    override fun close() {
        activity?.let { act ->
            if (act.currentFragment === this) {
                act.supportFragmentManager.popBackStack()
            }
        }
    }

//    companion object {
//        internal fun from(component: UIComponent) = UniversalFragment().apply {
//            this.component = component
//        }
//    }
}

private class DefaultValue<T: Any>(val defaultBuilder: () -> T): ReadWriteProperty<Any, T> {
    private var value: T? = null

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return value
            ?: defaultBuilder().also {
                value = it
            }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        this.value = value
    }
}
fun <T: Any> lazyDefault(builder: () -> T): ReadWriteProperty<Any, T> = DefaultValue(builder)

interface Navigable {
    fun push(view: UIComponent)
    fun pop()
}
