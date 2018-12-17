package com.loafofpiecrust.turntable.ui.universal

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import com.loafofpiecrust.turntable.serialize.arg
import com.loafofpiecrust.turntable.serialize.getValue
import com.loafofpiecrust.turntable.serialize.setValue
import org.jetbrains.anko.support.v4.alert

class UniversalDialogFragment(): DialogFragment() {
    internal constructor(args: DialogComponent): this() {
        this.composedArgs = args
    }

    private var composedArgs: DialogComponent by arg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composedArgs.apply {
            onCreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return alert {
            customView = composedArgs.createView(requireContext())
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

fun <T> T.show(context: Context) where T : DialogComponent, T: Parcelable {
    UniversalDialogFragment(this).show(
        (context as AppCompatActivity).supportFragmentManager,
        javaClass.canonicalName
    )
}