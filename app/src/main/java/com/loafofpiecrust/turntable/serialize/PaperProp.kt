package com.loafofpiecrust.turntable.serialize

import com.github.ajalt.timberkt.Timber
import com.loafofpiecrust.turntable.util.skip
import io.paperdb.Book
import io.paperdb.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class PaperProp<T: Any>(key: String): ReadOnlyProperty<Any, ConflatedBroadcastChannel<T>> {
    init {
        assert(!registeredPages.contains(key)) {
            "Cannot have two properties with the same key"
        }
        registeredPages.add(key)
    }

    private val subject = ConflatedBroadcastChannel<T>()
    private var initialized = false

    protected abstract fun produceDefault(): T
    protected abstract suspend fun readValue(book: Book): T?
    protected abstract suspend fun writeValue(book: Book, value: T)

    override fun getValue(thisRef: Any, property: KProperty<*>): ConflatedBroadcastChannel<T> {
        if (subject.valueOrNull == null && !initialized) {
            initialized = true
            val book = Paper.book(BOOK_NAME)
            val readValue = try {
                runBlocking { readValue(book) }
            } catch (e: Exception) {
                Timber.e(e) { "Failed to deserialize file" }
                null
            }
            subject.offer(readValue ?: produceDefault())

            GlobalScope.launch(Dispatchers.IO) {
                // skip the first because it'll be the value read from disk.
                subject.openSubscription().skip(1).consumeEach {
                    writeValue(book, it)
                }
            }
        }
        return subject
    }

    companion object {
        /**
         * Never **ever** change this book name
         * This means that user data will be stored as JSON in the folder:
         * <app_data>/userdata/<name>.paper
         */
        private const val BOOK_NAME = "userdata"

        private val registeredPages = mutableSetOf<String>()
    }
}

inline fun <reified T: Any> Paper.page(key: String, crossinline defaultValue: () -> T) =
    object: PaperProp<T>(key) {
        override fun produceDefault(): T = defaultValue()
        override suspend fun readValue(book: Book): T? = book.read<T>(key)
        override suspend fun writeValue(book: Book, value: T) {
            book.write(key, value)
        }
    }