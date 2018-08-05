package com.loafofpiecrust.turntable

//import quatja.com.vorolay.VoronoiView
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.view.ViewPager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.arlib.floatingsearchview.FloatingSearchView
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.github.daemontus.Option
import com.github.daemontus.Result
import com.github.daemontus.asError
import com.github.daemontus.unwrapOrElse
import com.github.salomonbrys.kotson.jsonNull
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.google.gson.GsonBuilder
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.hasValue
import com.loafofpiecrust.turntable.util.task
import com.lsjwzh.widget.recyclerviewpager.RecyclerViewPager
import com.mcxiaoke.koi.ext.closeQuietly
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import fr.castorflex.android.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.AnkoViewDslMarker
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.childrenSequence
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.onPageChangeListener
import org.jetbrains.anko.wrapContent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.collections.ArrayList
import kotlin.coroutines.experimental.CoroutineContext


//inline fun ViewManager.slidingUpPanelLayout(init: @AnkoViewDslMarker SlidingUpPanelLayout.() -> Unit = {}): SlidingUpPanelLayout =
//    ankoView({ SlidingUpPanelLayout(it) }, theme = 0, init = init)

inline fun ViewManager.fastScrollRecycler(init: @AnkoViewDslMarker FastScrollRecyclerView.() -> Unit = {}): FastScrollRecyclerView =
    ankoView({ FastScrollRecyclerView(it) }, theme = 0, init = init)

inline fun ViewManager.recyclerViewPager(init: @AnkoViewDslMarker RecyclerViewPager.() -> Unit): RecyclerViewPager =
    ankoView({ RecyclerViewPager(it) }, theme = 0, init = init)

inline fun ViewManager.floatingSearchView(theme: Int = 0, init: FloatingSearchView.() -> Unit = {}): FloatingSearchView =
    ankoView({ FloatingSearchView(it) }, theme, init)

//fun ViewManager.voronoiView(theme: Int = 0, init: @AnkoViewDslMarker VoronoiView.() -> Unit = {}): VoronoiView =
//    ankoView({ VoronoiView(it) }, theme = theme, init = init)

inline fun ViewManager.circularProgressBar(theme: Int = 0, init: @AnkoViewDslMarker CircularProgressBar.() -> Unit = {}): CircularProgressBar =
    ankoView({ CircularProgressBar(it) }, theme = theme, init = init)

//inline fun ViewManager.seekBarCompat(theme: Int = 0, init: @AnkoViewDslMarker SeekBarCompat.() -> Unit): SeekBarCompat =
//    ankoView({ SeekBarCompat(it) }, theme = theme, init = init)

//fun ViewManager.circularSlider(theme: Int = 0, init: @AnkoViewDslMarker CircularSlider.() -> Unit = {}): CircularSlider =
//    ankoView({ CircularSlider(it) }, theme = theme, init = init)


//fun ViewManager.playbackControlView(theme: Int = 0, init: PlaybackControlView.() -> Unit = {}): PlaybackControlView =
//    ankoView({ PlaybackControlView(it) }, theme = theme, init = init)

//fun ViewManager.defaultTimeBar(theme: Int = 0, init: DefaultTimeBar.() -> Unit = {}): DefaultTimeBar =
//    ankoView({ DefaultTimeBar(it, null) }, theme = theme, init = init)

//fun ViewManager.playbackControlView(theme: Int = 0, init: PlaybackControlView.() -> Unit = {}): FloatingSearchView =
//    ankoView({ FloatingSearchView(it) }, theme = theme, init = init)


inline fun <reified T: Fragment> View.fragment(
    manager: FragmentManager?,
    fragment: T
): T {
    if (this.id == View.NO_ID) {
        this.id = View.generateViewId()
    }
    manager?.beginTransaction()
        ?.add(this.id, fragment)
        ?.commit()
//    fragment.view?.init()
    return fragment
}

fun Toolbar.menuItem(
    title: String,
    iconId: Int? = null,
    color: Int? = null,
    showIcon: Boolean = false,
    init: MenuItem.() -> Unit = {}
): MenuItem {
    return menu.menuItem(title, iconId, color, showIcon, init)
}

fun Menu.menuItem(title: String, iconId: Int? = null, color: Int? = null, showIcon: Boolean = false, init: MenuItem.() -> Unit = {}): MenuItem {
    val item = add(title)
    if (iconId != null) {
        item.icon = App.instance.resources.getDrawable(iconId)
        // TODO: Contrast with primary color instead?
        val color = color ?: if (UserPrefs.useDarkTheme.valueOrNull != false) {
            Color.WHITE
        } else Color.BLACK
        item.icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }
    init.invoke(item)
    item.setShowAsAction(
        if (showIcon) {
            MenuItem.SHOW_AS_ACTION_IF_ROOM
        } else MenuItem.SHOW_AS_ACTION_NEVER
    )
    return item
}

fun Toolbar.subMenu(title: String, init: SubMenu.() -> Unit) {
    val sub = menu.addSubMenu(title)
    init(sub)
//    return sub
}
fun Menu.subMenu(title: String, init: SubMenu.() -> Unit) {
    val sub = this.addSubMenu(title)
    init(sub)
//    return sub
}

data class MenuGroup(val menu: Menu, val id: Int) {
    var enabled: Boolean = true
        set(value) {
            menu.setGroupEnabled(id, value)
            field = value
        }
    var visible: Boolean = true
        set(value) {
            menu.setGroupVisible(id, value)
            field = value
        }

    fun menuItem(title: String, iconId: Int? = null, color: Int? = null, showIcon: Boolean = false, init: MenuItem.() -> Unit = {}): MenuItem {
        val showType = if (showIcon) {
            MenuItem.SHOW_AS_ACTION_IF_ROOM
        } else {
            MenuItem.SHOW_AS_ACTION_NEVER
        }
        return menuItem(title, iconId, color, showType, init)
    }

    private fun menuItem(title: String, iconId: Int? = null, color: Int? = null, showType: Int = MenuItem.SHOW_AS_ACTION_NEVER, init: MenuItem.() -> Unit = {}): MenuItem {
        val item = menu.add(id, Menu.NONE, Menu.NONE, title)
        if (iconId != null) {
            item.icon = App.instance.resources.getDrawable(iconId)
            val color = color ?: if (UserPrefs.useDarkTheme.value) {
                Color.WHITE
            } else Color.BLACK
            item.icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        init.invoke(item)
        item.setShowAsAction(showType)
        return item
    }
}

inline fun Menu.group(
    id: Int,
    checkable: Boolean = false,
    exclusive: Boolean = false,
    cb: MenuGroup.() -> Unit
): MenuGroup = run {
    val g = MenuGroup(this, id)
    cb(g)
    setGroupCheckable(g.id, checkable, exclusive)
    g
}

inline fun MenuItem.onClick(
    context: CoroutineContext = UI,
    noinline handler: suspend (v: MenuItem) -> Unit
) {
    setOnMenuItemClickListener { v ->
        task(context) {
            handler.invoke(v)
        }
        true
    }
}

fun msToTimeString(ms: Int): String {
    // seconds: divide by 1000
    // minutes: divide seconds by 60
    val sec = ms / 1000
    val min = sec / 60
    val subsec = sec % 60
    return String.format(Locale.US, "%02d:%02d", min, subsec)
}

inline fun broadcastReceiver(crossinline init: (Context, Intent?) -> Unit): BroadcastReceiver {
    return object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            init(context, intent)
        }
    }
}

inline fun <T> List<T>.with(elem: T, pos: Int): List<T>
    = take(pos) + elem + drop(pos)
inline fun <T> List<T>.with(sub: List<T>, pos: Int = size): List<T>
    = take(pos) + sub + drop(pos)
inline fun <T> List<T>.without(pos: Int): List<T>
    = take(pos) + drop(pos + 1)
inline fun <T> List<T>.withoutFirst(picker: (T) -> Boolean): List<T> {
    val pos = indexOfFirst(picker)
    return take(pos) + drop(pos + 1)
}
fun <T> List<T>.withoutElem(elem: T): List<T> {
    val idx = indexOfFirst { it === elem }
    return if (idx >= 0) {
        this.without(idx)
    } else this
}
fun <T> List<T>.withReplaced(pos: Int, newVal: T): List<T>
    = take(pos) + newVal + drop(pos + 1)

fun <T> List<T>.dedup(pred: (T, T) -> Boolean = { a, b -> a == b }): List<T> {
    val result = ArrayList<T>(this.size)
    this.forEach { curr ->
        val dup = result.find { pred(curr, it) }
        if (dup == null) {
            result.add(curr)
        }
    }
    return result
}

inline fun <T> List<T>.dedupMerge(pred: (T, T) -> Boolean, merger: (T, T) -> T): List<T> {
    val result = ArrayList<T>(this.size)
    this.forEach { curr ->
        val dupIdx = result.indexOfFirst { pred(curr, it) }
        if (dupIdx != -1) {
            val orig = result.removeAt(dupIdx)
            result.add(dupIdx, merger(orig, curr))
        } else {
            result.add(curr)
        }
    }
    return result
}

inline fun <T> List<T>.dedupMergeSorted(isDup: (T, T) -> Boolean, merger: (T, T) -> T): List<T> {
    val result = ArrayList<T>(this.size)
    var prev: T? = null
    this.forEach { curr ->
        if (prev != null && isDup(prev!!, curr)) {
            result.removeAt(result.size - 1)
            result.add(merger(prev!!, curr))
        } else {
            result.add(curr)
        }
        prev = curr
    }
    return result
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
    init: android.support.design.widget.CollapsingToolbarLayout.LayoutParams.() -> kotlin.Unit = {}): T
{
    val layoutParams = android.support.design.widget.CollapsingToolbarLayout.LayoutParams(width, height)
    layoutParams.init()
    this.layoutParams = layoutParams
    return this
}

inline fun <T> T?.toOption() = if (this == null) Option.None<T>() else Option.Some(this)
inline fun <T> Option<T>.toNullable(): T? = this.unwrapOrElse { null }
inline fun <T> T?.toNullable() = this

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

inline fun View.postDelayedLoop(intervalMs: Long, crossinline cb: () -> Boolean) = run {
    postDelayed(object: Runnable {
        override fun run() {
            val keepGoing = cb()
            if (keepGoing) {
                postDelayed(this, intervalMs)
            }
        }
    }, intervalMs)
}

inline fun <T: Any, R> given(cond: T?, block: (T) -> R): R? = run {
    if (cond != null && cond != false) {
        block(cond)
    } else null
}
inline fun <A, B, R> given(a: A?, b: B?, block: (A, B) -> R): R? = run {
    if (a != null && b != null) {
        block(a, b)
    } else null
}
inline fun <A, B, C, R> given(a: A?, b: B?, c: C?, block: (A, B) -> R): R? = run {
    if (a != null && b != null && c != null) {
        block(a, b)
    } else null
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

fun compress(input: String): String {
    val compressed = ByteArray(input.length)
    val len = Deflater().run {
        setInput(input.toByteArray(Charsets.UTF_8))
        finish()
        val len = deflate(compressed)
        end()
        len
    }
    return String(compressed, 0, len, Charsets.ISO_8859_1)
}

fun decompress(input: String): String {
    val decompressed = ByteArray(input.length * 2)
    val len = Inflater().run {
        val bytes = input.toByteArray(Charsets.ISO_8859_1)
        setInput(bytes, 0, bytes.size)
        val len = inflate(decompressed)
        end()
        len
    }
    return String(decompressed, 0, len, Charsets.UTF_8)
}

var TextView.textStyle: Int
    get() = typeface?.style ?: 0
    set(value) = setTypeface(null, value)

inline fun <T, E, R> Result<T, E>.flatMap(
    mapper: (T) -> Result<R, E>
): Result<R, E> = when (this) {
    is Result.Ok -> mapper(this.ok)
    is Result.Error -> this.error.asError()
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
infix fun <T> T.provided(block: (T) -> Boolean): T? = if (block(this)) this else null

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
    } catch (e: Throwable) {
//        e.printStackTrace()
        v
    }
}

/**
 * Parallel map using Kovenant promises
 */
fun <T, R> Iterable<T>.parMap(context: CoroutineContext = BG_POOL, mapper: suspend (T) -> R): List<Deferred<R>> = run {
    this.map {
        task(context) { mapper(it) }
    }
}

suspend fun <T> List<Deferred<T>>.awaitAll(): List<T?> = run {
    this.map {
        try {
            it.await()
        } catch (e: Exception) {
            null
        }
    }
}

suspend fun <T: Any> List<Deferred<T>>.awaitAllNotNull(): List<T> = run {
    this.mapNotNull {
        try {
            it.await()
        } catch (e: Exception) {
            null
        }
    }
}

fun Kryo.concreteToBytes(obj: Any, expectedSize: Int = 256, compress: Boolean = false): ByteArray {
    val baos = ByteArrayOutputStream(expectedSize)
    val os = Output(if (compress) DeflaterOutputStream(baos) else baos)
    writeObject(os, obj)
    os.flush()
    return baos.toByteArray().also {
        os.closeQuietly()
    }
}

fun Kryo.objectToBytes(obj: Any, expectedSize: Int = 256, compress: Boolean = false): ByteArray {
    val baos = ByteArrayOutputStream(expectedSize)
    val os = Output(if (compress) DeflaterOutputStream(baos) else baos)
    writeClassAndObject(os, obj)
    os.flush()
    return baos.toByteArray().also { os.closeQuietly() }
}

inline fun <T: Any> Kryo.objectFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return (readClassAndObject(input) as T).also { input.closeQuietly() }
}

inline fun <reified T: Any> Kryo.concreteFromBytes(bytes: ByteArray, decompress: Boolean = false): T {
    val input = if (decompress) {
        Input(InflaterInputStream(ByteArrayInputStream(bytes)))
    } else Input(bytes)
    return readObject(input, T::class.java).also { input.closeQuietly() }
}


//infix fun <T> Subject<T>.puts(value: T) {
//    onNext(value)
//}

inline infix fun <T> SendChannel<T>.puts(value: T) {
    offer(value)
}

inline infix fun <T> ConflatedBroadcastChannel<T>.putsMapped(transform: (T) -> T) {
    offer(transform(this.value))
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

inline fun <T> ConflatedBroadcastChannel<T>.reiterate() {
    offer(value)
}

var ImageView.tintResource: Int
    get() = 0
    set(id) {
        setColorFilter(context.resources.getColor(id))
    }


fun <T> bindVal(obs: BroadcastChannel<T>, ctx: Job, initial: T, getter: ((T) -> Unit) -> Unit, setter: (T) -> Unit) = run {
    getter.invoke { obs.offer(it) }
    task(UI + ctx) { obs.consumeEach { setter.invoke(it) } }
}

fun ViewPager.bindCurrentPage(obs: BroadcastChannel<Int>, ctx: Job) = run {
    bindVal(obs, ctx, currentItem, {
        onPageChangeListener {
            onPageSelected(it)
        }
    }, { setCurrentItem(it, true) })
}


val View.clicks: ReceiveChannel<Unit> get() = produce(UI) {
    setOnClickListener {
        offer(Unit)
    }
}

fun ImageButton.menu(block: Menu.() -> Unit): ImageButton {
    backgroundColor = Color.TRANSPARENT
    val popup = PopupMenu(
        this@menu.context, this@menu, Gravity.CENTER,
        0, R.style.AppTheme_PopupOverlay
    )
    block(popup.menu)
    onClick {
        popup.show()
    }
    return this
}

fun View.generateChildrenIds() {
    if (id == View.NO_ID) {
        id = View.generateViewId()
    }
    childrenSequence().forEach {
        if (it.id == View.NO_ID) {
            it.id = View.generateViewId()
        }
    }
}