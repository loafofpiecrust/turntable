package com.loafofpiecrust.turntable

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.test.runner.AndroidJUnit4
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.junit.Test
import org.junit.runner.RunWith


interface Grandparent: Parcelable

/**
 * Tests inheriting from a Parcelable open class
 * where the child class does *not* want to parcelize
 * any extra data.
 */
@Parcelize
open class Parent(
    val x: Int = 1
): Grandparent {
    open val y get() = 1

    class Child(
        override val y: Int
    ): Parent()
}

@RunWith(AndroidJUnit4::class)
class DataTest {
    @Test
    fun parcelableSubclass() {
        val bundle = Bundle()
        bundle.putParcelable("child", Parent.Child(2))

        val bytes = Parcel.obtain().run {
            writeBundle(bundle)
            marshall().also {
                recycle()
            }
        }

        val saved = Parcel.obtain().apply {
            unmarshall(bytes, 0, bytes.size)
            setDataPosition(0)
        }.readBundle()!!.apply {
            classLoader = Parent.Child::class.java.classLoader
        }

        val result = saved.getParcelable<Grandparent>("child")
        assert(result is Parent)
        val parent = result as Parent
        assert(parent.y == 1)
        assert(parent.x == 1)
        println(parent)
    }
}