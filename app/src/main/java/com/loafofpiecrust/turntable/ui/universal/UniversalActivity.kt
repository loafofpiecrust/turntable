package com.loafofpiecrust.turntable.ui.universal

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.app.AppCompatActivity
import com.loafofpiecrust.turntable.R
import kotlinx.io.core.Closeable
import org.jetbrains.anko.startActivity

class UniversalActivity: AppCompatActivity(), Closeable {
    lateinit var component: UIComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = savedInstanceState?.getParcelable<Parcelable>("component") as UIComponent
        setTheme(R.style.AppTheme)
        setContentView(component.run {
            onCreate()
            createView(this@UniversalActivity)
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

fun <T> T.startActivity(context: Context) where T : UIComponent, T: Parcelable {
    context.startActivity<UniversalActivity>("component" to this)
}