package com.loafofpiecrust.turntable.util

import com.loafofpiecrust.turntable.tryOr


inline fun <T> List<T>.with(elem: T, pos: Int): List<T>
    = ListWith(this, listOf(elem), pos)
inline fun <T> List<T>.with(sub: List<T>, pos: Int = size): List<T>
    = ListWith(this, sub, pos)
inline fun <T> List<T>.without(pos: Int): List<T>
    = (asSequence().take(pos) + asSequence().drop(pos + 1)).toList()
inline fun <T> List<T>.withoutFirst(picker: (T) -> Boolean): List<T> {
    val pos = indexOfFirst(picker)
    return without(pos)
}
fun <T> List<T>.withoutElem(elem: T): List<T> {
    val idx = indexOfFirst { it === elem }
    return if (idx >= 0) {
        this.without(idx)
    } else this
}
fun <T> List<T>.withReplaced(pos: Int, newVal: T): List<T>
    = take(pos) + newVal + drop(pos + 1)

fun <T> Sequence<T>.with(sub: Sequence<T>, pos: Int) =
    sequenceOf(take(pos), sub, drop(pos)).flatten()
fun <T> Sequence<T>.with(elem: T, pos: Int) = with(sequenceOf(elem), pos)
fun <T> Sequence<T>.without(pos: Int) = take(pos) + drop(pos + 1)
fun <T> Sequence<T>.replace(pos: Int, newVal: T): Sequence<T> =
    sequenceOf(take(pos), sequenceOf(newVal), drop(pos + 1)).flatten()

val <T> Iterable<T>.lazy inline get() = asSequence()
val <K, T> Map<K, T>.lazy inline get() = asSequence()

inline fun <T, R: Any> Sequence<T>.mapNotFailed(crossinline transform: (T) -> R): Sequence<R> =
    mapNotNull { e -> tryOr(null) { transform(e) } }

inline fun <T, R: Any> Iterable<T>.mapNotFailed(transform: (T) -> R): List<R> =
    mapNotNull { e -> tryOr(null) { transform(e) } }


class ListWith<T>(
    private val base: List<T>,
    private val nested: List<T>,
    private val insertedIndex: Int
): List<T> {
    override val size: Int
        get() = base.size + nested.size

    override fun contains(element: T): Boolean =
        base.contains(element) || nested.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean =
        elements.all { this.contains(it) }

    override fun get(index: Int): T = when {
        index < insertedIndex -> base[index]
        index < insertedIndex + nested.size -> nested[index - insertedIndex]
        else -> base[index - nested.size]
    }

    override fun indexOf(element: T): Int {
        val index = base.indexOf(element)
        return if (index != -1) {
            if (index < insertedIndex) {
                index
            } else {
                index + nested.size
            }
        } else nested.indexOf(element).let { index ->
            if (index != -1) {
                index + insertedIndex
            } else {
                -1
            }
        }
    }

    override fun isEmpty(): Boolean = base.isEmpty() && nested.isEmpty()

    private fun resolveSequence() =
        base.asSequence().take(insertedIndex) + nested.asSequence() + base.asSequence().drop(insertedIndex)

    override fun iterator(): Iterator<T> =
        resolveSequence().iterator()

    override fun lastIndexOf(element: T): Int {
        val index = base.lastIndexOf(element)
        return if (index != -1) {
            if (index < insertedIndex) {
                index
            } else {
                index + nested.size
            }
        } else nested.lastIndexOf(element).let { index ->
            if (index != -1) {
                index + insertedIndex
            } else {
                -1
            }
        }
    }

    override fun listIterator(): ListIterator<T> {
        return resolveSequence().toList().listIterator()
    }

    override fun listIterator(index: Int): ListIterator<T> {
        return resolveSequence().toList().listIterator(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        return resolveSequence().drop(fromIndex).take(toIndex - fromIndex).toList()
    }

}