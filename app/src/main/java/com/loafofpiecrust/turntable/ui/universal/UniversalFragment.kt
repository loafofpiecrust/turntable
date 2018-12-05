package com.loafofpiecrust.turntable.ui.universal

import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.Fragment
import android.view.*
import com.loafofpiecrust.turntable.ui.currentFragment
import com.loafofpiecrust.turntable.util.arg
import com.loafofpiecrust.turntable.util.getValue
import org.jetbrains.anko.childrenRecursiveSequence
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Closable {
    fun close()
}


class UniversalFragment: Fragment(), Closable {
    var component: UIComponent by arg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

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
            if (context != null) {
                menu.prepareOptions(context!!)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        component.onResume()
    }

    override fun onPause() {
        super.onPause()
        component.onPause()
    }

//    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu)
//    }

    override fun onDestroy() {
        super.onDestroy()
        // Only shut down the component when the fragment is being removed.
        // Otherwise, it's a config change and the same component instance
        // will be revived for use in the fragment.
//        if (isRemoving) {
            component.onDestroy()
//        }
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

/// Allow creating fragments/activities from Parcelable Components
/// Otherwise, only plain views can be created.
fun <T> T.createFragment(): Fragment where T : UIComponent, T: Parcelable {
    return UniversalFragment().also {
        it.component = this
        it.onCreate()
    }
}