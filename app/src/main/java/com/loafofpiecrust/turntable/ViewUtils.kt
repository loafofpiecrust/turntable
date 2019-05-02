package com.loafofpiecrust.turntable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import com.github.daemontus.Option
import com.github.daemontus.Result
import com.github.daemontus.asError
import com.github.daemontus.unwrapOrElse
import com.github.salomonbrys.kotson.jsonNull
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.google.gson.GsonBuilder
import com.loafofpiecrust.turntable.util.hasValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.first
import org.jetbrains.anko.alert
import org.jetbrains.anko.wrapContent
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.*


fun msToTimeString(ms: Int): String {
    // seconds: divide by 1000
    val sec = ms / 1000
    // minutes: divide seconds by 60
    val min = sec / 60
    val secWithinMin = sec % 60
    return String.format(Locale.US, "%02d:%02d", min, secWithinMin)
}


inline fun broadcastReceiver(crossinline receive: (Context, Intent) -> Unit) =
    object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            receive(context, intent)
        }
    }

fun <T> List<T>.dedup(pred: (T, T) -> Boolean = { a, b -> a == b }): List<T> =
    dedupMerge(pred) { a, b -> a }

fun <T> Collection<T>.dedupMerge(isDup: (T, T) -> Boolean, merger: (T, T) -> T): List<T> {
    val result = ArrayList<T>(this.size)
    for (curr in this) {
        val dupIdx = result.indexOfFirst { isDup(curr, it) }
        if (dupIdx != -1) {
            val orig = result[dupIdx]
            result[dupIdx] = merger(orig, curr)
        } else {
            result.add(curr)
        }
    }
    return result
}

inline fun <T> Collection<T>.dedupMergeSorted(isDup: (T, T) -> Boolean, merger: (T, T) -> T): List<T> {
    val result = ArrayList<T>(this.size)
    for (curr in this) {
        val prev = result.lastOrNull()
        if (prev != null && isDup(prev, curr)) {
            result[result.size - 1] = merger(prev, curr)
        } else {
            result.add(curr)
        }
    }
    return result //.apply { trimToSize() }
}

fun <T> Sequence<T>.dedupMergeSorted(isDup: (T, T) -> Boolean, merger: (T, T) -> T) = sequence {
    var prev: T? = null
    forEach { curr ->
        prev = if (prev != null) {
            if (isDup(prev!!, curr)) {
                merger(prev!!, curr)
            } else {
                yield(prev!!)
                curr
            }
        } else {
            yield(curr)
            curr
        }
    }
}

inline fun <T, R : Comparable<R>> Sequence<T>.toListSortedBy(crossinline selector: (T) -> R?): List<T> {
    val list = toMutableList()
    list.sortWith(compareBy(selector))
    return list
}



fun <T> List<T>.mergeSortDedup(other: List<T>, comp: Comparator<T>, merger: (T, T) -> T): List<T> {
    val result = ArrayList<T>(size + other.size)
    var leftIdx = 0
    var rightIdx = 0
    while (leftIdx < this.size && rightIdx < other.size) {
        val left = this[leftIdx]
        val right = other[rightIdx]
        val diff = comp.compare(left, right)
        when {
            diff < 0 -> { // left < right
                result.add(left)
                leftIdx += 1
            }
            diff > 0 -> { // left > right
                result.add(right)
                rightIdx += 1
            }
            else -> {
                result.add(merger(left, right))
                leftIdx += 1
                rightIdx += 1
            }
        }
    }

    if (leftIdx < this.size) {
        result.addAll(this.subList(leftIdx, this.size))
    } else if (rightIdx < other.size) {
        result.addAll(other.subList(rightIdx, other.size))
    }
    return result
}

fun <T> List<T>.withDedup(newVal: T, pred: (T, T) -> Boolean = { a, b -> a == b }): List<T> {
    val dup = this.find { pred(newVal, it) }
    return if (dup == null) (this + newVal) else this
}

fun <T> List<T>.withAllDedup(others: List<T>, pred: (T, T) -> Boolean = { a, b -> a == b }): List<T> {
    val result = this.toMutableList()
    others.forEach { curr ->
        val dup = result.find { pred(curr, it) }
        if (dup == null) {
            result.add(curr)
        }
    }
    return result
}

fun <T> List<T>.shifted(from: Int, to: Int): List<T> = run {
    if (from != to) {
        val newList = this.toMutableList()
        val finalTo = clamp(when {
            from < to -> to - 1
            else -> to
        }, 0, size - 1)
        val finalFrom = clamp(from, 0, size - 1)
        newList.add(finalTo, newList.removeAt(finalFrom))
        newList
    } else this
}

fun <T> Sequence<T>.shifted(from: Int, to: Int): Sequence<T> = when {
    to == from -> this
    to > from -> {
        val upToSource = take(from)
        val source = upToSource.first()
        sequenceOf(
            // up until the source
            upToSource,
            // after the source until the destination
            drop(from + 1).take(to - from),
            // the shifted element
            sequenceOf(source),
            // all after the destination
            drop(to + 1)
        ).flatten()
    }
    else -> TODO()
}

fun <T> MutableList<T>.shift(from: Int, to: Int) {
    if (from != to) {
        val finalTo = clamp(when {
            from < to -> to - 1
            else -> to
        }, 0, size - 1)
        val finalFrom = clamp(from, 0, size - 1)
        add(finalTo, removeAt(finalFrom))
    }
}

inline fun <T, R: Comparable<R>> List<T>.binarySearchElem(key: R, crossinline comp: (T) -> R) =
    getOrNull(binarySearchBy(key) { comp(it) })

inline fun <T, R: Comparable<R>> List<T>.binarySearchNearestElem(key: R, crossinline comp: (T) -> R): T? {
    val idx = binarySearchBy(key) { comp(it) }
    // idx is negative if no _exact_ match found
    return getOrNull(if (idx < 0) -idx else idx)
}

fun <T> List<T>.binarySearchElem(key: T, comparator: Comparator<in T>) =
    getOrNull(binarySearch(key, comparator))
fun <T> List<T>.binarySearchNearestElem(key: T, comparator: Comparator<in T>): T? {
    val idx = binarySearch(key, comparator)
    // idx is negative if no _exact_ match found
    return getOrNull(if (idx < 0) -idx else idx)
}

fun <T: kotlin.Comparable<T>> clamp(x: T, min: T, max: T): T
    = maxOf(min, minOf(x, max))

/// Allow subscription on a certain thread, otherwise it happens in the calling thread
//fun <T> Observable<T>.subscribe(
//    context: CoroutineContext, f: suspend (T) -> Unit
//): Disposable {
////    return if (context == UI) {
////        observeOn(AndroidSchedulers.mainThread())
////            .subscribe { v -> runBlocking { f(v) } }
////    } else {
//        return subscribe { v -> task(context) { f(v) } }
////    }
//}


//fun <T> BehaviorSubject<T>.refresh() {
//    onNext(value)
//}


fun <T : android.view.View> T.collapsingToolbarlparams(
    width: kotlin.Int = wrapContent, height: kotlin.Int = wrapContent,
    init: CollapsingToolbarLayout.LayoutParams.() -> kotlin.Unit = {}): T
{
    val layoutParams = CollapsingToolbarLayout.LayoutParams(width, height)
    layoutParams.init()
    this.layoutParams = layoutParams
    return this
}

fun <T> T?.toOption() = if (this == null) Option.None<T>() else Option.Some(this)
fun <T> Option<T>.toNullable(): T? = this.unwrapOrElse { null }

fun Int.toHSV(): FloatArray = run {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv
}

fun Int.darken(ratio: Float): Int = run {
    val hsv = toHSV()
    hsv[2] -= hsv[2] * ratio
    Color.HSVToColor(hsv)
}

fun Int.lighten(ratio: Float): Int = run {
    val hsv = toHSV()
    hsv[2] = 1f - ratio * (1f - hsv[2])
    Color.HSVToColor(hsv)
}

inline fun <T: Any, R> given(cond: T?, block: (T) -> R): R? = cond?.let(block)
inline fun <A, B, R> given(a: A?, b: B?, block: (A, B) -> R): R? {
    return if (a != null && b != null) {
        block(a, b)
    } else null
}
inline fun <A, B, C, R> given(a: A?, b: B?, c: C?, block: (A, B) -> R): R? = run {
    if (a != null && b != null && c != null) {
        block(a, b)
    } else null
}
inline fun <T: Any> mergeNullables(a: T?, b: T?, block: (T, T) -> T): T? {
    return if (a != null) {
        if (b != null) {
            return block(a, b)
        } else a
    } else b
}

fun String.substrings(substrLength: Int): List<String> {
    val strings = ArrayList<String>()
    var index = 0
    while (index < this.length) {
        strings.add(this.substring(index, minOf(index + substrLength, this.length)))
        index += substrLength
    }
    return strings
}


inline fun <T, E, R> Result<T, E>.flatMap(
    mapper: (T) -> Result<R, E>
): Result<R, E> = when (this) {
    is Result.Ok -> mapper(this.ok)
    is Result.Error -> this.error.asError()
}

inline fun <T> intoResult(block: () -> T): Result<T, Exception> {
    return try {
        Result.Ok(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
}


fun List<String>.longestSharedPrefix(ignoreCase: Boolean = false): String? {
    val shortest = this.minBy { it.length } ?: return null
    val stb = StringBuilder()
    for (idx in 0 until shortest.length) {
        val c = shortest[idx]
        val shared = this.all { it[idx].equals(c, ignoreCase) }
        if (shared && !c.isLetterOrDigit()) {
            stb.append(c)
        } else break
    }
    return stb.toString()
}

fun List<String>.longestSharedSuffix(ignoreCase: Boolean = false): String? {
    val shortest = this.minBy { it.length } ?: return null
    val stb = StringBuilder()
    for (idx in (0 until shortest.length)) {
        val c = shortest[shortest.length - idx - 1]
        val shared = this.all { it[it.length - idx - 1].equals(c, ignoreCase) }
        if (shared && !c.isLetterOrDigit()) {
            stb.append(c)
        } else break
    }
    return stb.reverse().toString()
}

infix fun <T> T.provided(b: Boolean): T? = if (b) this else null
inline infix fun <T> T.provided(block: (T) -> Boolean): T? = if (block(this)) this else null

inline fun <reified T: Any> GsonBuilder.registerSubjectType() = run {
    registerTypeAdapter<ConflatedBroadcastChannel<T>> {
        serialize {
            if (it.src.hasValue) {
                val value = it.src.value
                it.context.serialize(value)
            } else {
                jsonNull
            }
        }
        deserialize {
            if (it.json.isJsonNull) {
                ConflatedBroadcastChannel()
            } else {
                val value = it.context.deserialize<T>(it.json)
                ConflatedBroadcastChannel(value)
            }
        }
    }
}

inline fun onApi(version: Int, block: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT >= version) {
        block()
    }
}

inline fun <T> tryOr(v: T, block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
//        e.printStackTrace()
        v
    }
}


fun <T, R> Sequence<T>.parMap(
    context: CoroutineContext = EmptyCoroutineContext,
    mapper: suspend (T) -> R
): Sequence<Deferred<R>> = run {
    this.map {
        GlobalScope.async(context) { mapper(it) }
    }
}

fun <T, R> Iterable<T>.parMap(
    context: CoroutineContext = EmptyCoroutineContext,
    mapper: suspend (T) -> R
): List<Deferred<R>> = run {
    this.map {
        GlobalScope.async(context) { mapper(it) }
    }
}

inline fun <T, R: Any> Iterable<T>.tryMap(block: (T) -> R): List<R> = mapNotNull { tryOr(null) { block(it) } }

suspend fun <T: Any> Collection<Deferred<T>>.awaitAllNotNull(): List<T> = run {
    this.mapNotNull {
        try {
            it.await()
        } catch (e: Exception) {
            null
        }
    }
}

//infix fun <T> Subject<T>.puts(value: T) {
//    onNext(value)
//}

infix fun <T> SendChannel<T>.puts(value: T) {
    offer(value)
}

suspend inline infix fun <T> ConflatedBroadcastChannel<T>.putsMapped(transform: (T) -> T) {
    val prev = this.valueOrNull ?: this.openSubscription().first()
    synchronized(this) {
        offer(transform(prev))
    }
}

//infix fun <T> BehaviorSubject<List<T>>.appends(toAdd: T) {
//    synchronized(this) {
//        onNext(if (hasValue()) {
//            this.value + toAdd
//        } else {
//            listOf(toAdd)
//        })
//    }
//}


infix fun <T> ConflatedBroadcastChannel<List<T>>.appends(toAdd: T) {
    synchronized(this) {
        offer(if (hasValue) {
            this.value + toAdd
        } else {
            listOf(toAdd)
        })
    }
}

operator fun <K, T> ConflatedBroadcastChannel<Map<K, T>>.set(key: K, value: T) {
    synchronized(this) {
        val prev = valueOrNull
        offer(if (prev != null) {
            prev + (key to value)
        } else mapOf(key to value))
    }
}

fun <K, T> ConflatedBroadcastChannel<TreeMap<K, T>>.appends(key: K, value: T) {
    synchronized(this) {
        offer(if (hasValue) {
            TreeMap(this.value).apply {
                put(key, value)
            }
        } else {
            TreeMap<K, T>().apply {
                put(key, value)
            }
        })
    }
}

inline fun <T> ConflatedBroadcastChannel<T>.repeat() {
    offer(value)
}


fun View.popupMenu(gravity: Int = Gravity.CENTER, block: Menu.() -> Unit) {
    val popup = PopupMenu(
        context, this, gravity,
        0, R.style.AppTheme_PopupOverlay
    )
    block(popup.menu)
    popup.show()
}


suspend fun <T: Any> Context.selector(
    prompt: CharSequence,
    options: List<Pair<CharSequence, T>>
): T = suspendCoroutine { cont ->
    alert {
        this.title = prompt
        items(options.map { it.first }) { _, idx ->
            cont.resume(options[idx].second)
        }
        onCancelled {
            cont.resumeWithException(CancellationException())
        }
        show()
    }
}
suspend inline fun <T: Any> Fragment.selector(
    prompt: CharSequence,
    options: List<Pair<CharSequence, T>>
) = requireContext().selector(prompt, options)

suspend fun <T: Any> Context.selector(
    prompt: String,
    options: List<T>,
    format: (T) -> CharSequence
): T = suspendCoroutine { cont ->
    alert {
        this.title = prompt
        items(options.map { format(it) }) { _, idx ->
            cont.resume(options[idx])
        }
        onCancelled {
            cont.resumeWithException(CancellationException())
        }
        show()
    }
}


fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}