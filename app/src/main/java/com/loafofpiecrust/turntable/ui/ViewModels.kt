package com.loafofpiecrust.turntable.ui

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

/**
 * View model usage:
 * Library -> ArtistsFragment =(all)> ArtistsViewModel (0)
 * (tap artist) -(idx)> AlbumsFragment =(idx)> ArtistsViewModel (0) =(artist)> AlbumsViewModel (1)
 * (similars) -> ArtistsFragment =(idx)> ArtistsViewModel (0) =(similars)> ArtistsViewModel (2) <=
 * (tap artist) -> AlbumsFragment =(idx)> ArtistsViewModel (2) =(artist)> AlbumsViewModel (3) <=
 * (tap album) -> SongsFragment =(idx)> AlbumsViewModel (3) =(album)> SongsViewModel (4) <=
 */
//class SongsModel: ViewModel() {
//    lateinit var songs: ReceiveChannel<List<Song>>
//    override fun onCleared() {
//        super.onCleared()
//    }
//}

/**
 * Ideal data connection inside Fragment view:
 * onCreateView: make view and setup all subscriptions
 * onPause: don't react to any changes that'd change the view
 * onResume: allow ui to react to changes and apply the latest change (if any) while paused
 * onDestroy: cancel all running tasks and channels.
 */

class LifeObserver<T>(val chan: SendChannel<T>): LifecycleObserver {
    var lastValue: T? = null
    var paused: Boolean = false

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        paused = false
        val v = lastValue
        if (v != null) {
            chan.offer(v)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onPause() {
        paused = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        chan.close()
    }
}
fun <T> ReceiveChannel<T>.connect(
    lifecycle: WeakReference<Lifecycle>,
    context: CoroutineContext = Dispatchers.Unconfined
): ReceiveChannel<T> = GlobalScope.produce(context) {
    val obs = LifeObserver(this)
    withContext(Dispatchers.Main) { lifecycle.get()?.addObserver(obs) }

    consumeEach {
        if (!obs.paused) {
            send(it)
        } else {
            obs.lastValue = it
        }
    }
}

fun <T> BroadcastChannel<T>.connect(
    lifecycle: WeakReference<Lifecycle>,
    context: CoroutineContext = Dispatchers.Unconfined
) = openSubscription().connect(lifecycle, context)