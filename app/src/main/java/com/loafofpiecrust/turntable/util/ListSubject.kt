package com.loafofpiecrust.turntable.util

import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel

//
///**
// * Created by snead on 9/4/17.
// */
//class ListSubject<T: Any>: MutableList<T> {
//    override fun add(element: T): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun add(index: Int, element: T) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun addAll(index: Int, elements: Collection<T>): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun addAll(elements: Collection<T>): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun clear() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun remove(element: T): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun removeAll(elements: Collection<T>): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun removeAt(index: Int): T {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun retainAll(elements: Collection<T>): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun set(index: Int, element: T): T {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    private val subject = ConflatedBroadcastChannel(listOf<T>())
//
//    override val size: Int
//        get() = subject.value.size
//
//    override fun contains(element: T) = subject.value.contains(element)
//
//    override fun containsAll(elements: Collection<T>) = subject.value.containsAll(elements)
//
//    override fun get(index: Int): T = subject.value[index]
//
//    override fun indexOf(element: T): Int = subject.value.indexOf(element)
//
//    override fun isEmpty(): Boolean = subject.value.isEmpty()
//
//    override fun iterator(): MutableIterator<T> = object: MutableIterator<T> {
//        private var index: Int = 0
//
//        override fun hasNext() = index < size
//        override fun next(): T = get(index).also { index++ }
//        override fun remove() {
//            if (index > 0) {
//                index--
//                removeAt(index)
//            }
//        }
//    }
//
//    override fun lastIndexOf(element: T): Int = subject.value.lastIndexOf(element)
//
//    override fun listIterator() = object: MutableListIterator<T> {
//        private var index: Int = 0
//
//        override fun hasPrevious(): Boolean = index > 0
//
//        override fun nextIndex(): Int = index
//
//        override fun previous(): T {
//            index -= 1
//            return get(index)
//        }
//
//        override fun previousIndex(): Int = index - 1
//
//        override fun add(element: T) {
//            add(index, element)
//            index += 1
//        }
//
//        override fun hasNext(): Boolean = index < size
//
//        override fun next(): T = get(index).also { index += 1 }
//
//        override fun remove() {
//            if (index > 0) {
//                index--
//                removeAt(index)
//            }
//        }
//
//        override fun set(element: T) {
//            set(index, element)
//        }
//
//    }
//}