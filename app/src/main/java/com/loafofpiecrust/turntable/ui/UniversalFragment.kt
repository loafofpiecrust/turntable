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
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.alert
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Closable {
    fun close()
}

/// Implement AnkoComponent to access the Anko preview compiler.
abstract class UIComponent: CoroutineScope {
    private val supervisor = SupervisorJob()
    private val viewScope = SupervisorJob()
    private var currentScope = supervisor
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + currentScope

    fun createView(context: Context, container: Closable): View {
        currentScope = viewScope
        return AnkoContext.create(context, container).render().also {
            currentScope = supervisor
        }
    }
    fun createView(parent: ViewGroup): View {
        currentScope = viewScope
        return AnkoContext.createDelegate(parent).render().also {
            currentScope = supervisor
        }
    }
    protected abstract fun AnkoContext<*>.render(): View

    open fun Fragment.onCreate() {}
    open fun Activity.onCreate() {}
    open fun AlertBuilder<*>.prepare() {}
    open fun Menu.prepareOptions(context: Context) {}

    fun onDestroy() {
        supervisor.cancel()
    }
    fun onDestroyView() {
        viewScope.cancelChildren()
    }

    fun pushToMain(context: Context) {
        if (context is UniversalActivity) {
            (context.component as? Navigable)?.let { comp ->
                comp.push(this)
            }
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
}

/// Allow creating fragments/activities from Parcelable Components
/// Otherwise, only plain views can be created.
fun <T> T.createFragment(): Fragment where T : UIComponent, T: Parcelable {
    return UniversalFragment(this).also {
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
            composedArgs.apply {
                prepare()
            }
            customView { composedArgs.createView(requireContext(), this@UniversalDialogFragment) }
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

class UniversalFragment(): Fragment(), Closable {
    internal constructor(args: UIComponent): this() {
        this.composedArgs = args
    }

    private var composedArgs: UIComponent by arg()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return composedArgs.run {
            createView(requireContext(), this@UniversalFragment)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        composedArgs.apply {
            menu.prepareOptions(requireContext())
        }
    }

//    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu)
//    }

    override fun onDestroy() {
        super.onDestroy()
        composedArgs.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composedArgs.onDestroyView()
    }

    override fun close() {
        activity?.let { act ->
            if (act.currentFragment === this) {
                act.supportFragmentManager.popBackStack()
            }
        }
    }
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
