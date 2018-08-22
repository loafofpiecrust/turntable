package com.loafofpiecrust.turntable

import android.app.Application
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelStore
import android.content.Context
import android.net.*
import android.support.annotation.MainThread
import android.support.v4.app.Fragment
import android.util.DisplayMetrics
import com.chibatching.kotpref.Kotpref
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.evernote.android.state.StateSaver
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.util.CBCSerializer
import com.loafofpiecrust.turntable.util.distinctSeq
import com.loafofpiecrust.turntable.util.task
import com.loafofpiecrust.turntable.util.threadLocalLazy
import com.squareup.leakcanary.LeakCanary
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.SubListSerializers
import de.javakaffee.kryoserializers.UUIDSerializer
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.delay
import org.jetbrains.anko.connectivityManager
import org.jetbrains.anko.windowManager
import org.objenesis.strategy.StdInstantiatorStrategy
import java.lang.ref.WeakReference
import java.util.*


class App: Application() {
    enum class InternetStatus {
        UNLIMITED, LIMITED, OFFLINE
    }

    companion object {
        private lateinit var _instance: WeakReference<App>
        val instance: App get() = _instance.get()!!

        val kryo by threadLocalLazy {
            Kryo().apply {
                // TODO: Switch to CompatibleFieldSerializer or TagFieldSerializer once Song.artworkUrl is removed or any other refactoring goes down.
                setDefaultSerializer(FieldSerializer::class.java)
                instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())
//                setDefaultSerializer(FieldSerializer::class.java)
//                addDefaultSerializer(BehaviorSubject::class.java, BehaviorSubjectSerializer::class.java)
                addDefaultSerializer(ConflatedBroadcastChannel::class.java, CBCSerializer::class.java)
                register(UUID::class.java, UUIDSerializer())
                register(ArrayList::class.java).apply {
                    instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())
                }
                register(Arrays.asList(0).javaClass, ArraysAsListSerializer())
//                register(Collections.EMPTY_LIST.javaClass, CollectionsEmptyListSerializer())
//                register(Collections.singleton("").javaClass, CollectionsSingletonListSerializer())
//                register(emptyList<Song>().javaClass, JavaSerializer())
//                references = false
                SubListSerializers.addDefaultSerializers(this)
            }
        }

        fun <T> kryoWithRefs(block: (Kryo) -> T): T {
            kryo.references = true
            return block(kryo).also { kryo.references = false }
        }

        var sdCardUri: Uri? = null
    }

//    val connection: BehaviorSubject<>
    /// Initialize directly rather than a service, so it lives as long as the app does :D
    lateinit var library: Library
//    val fileSync = FileSyncService()
    lateinit var search: OnlineSearchService

    private val _internetStatus = ConflatedBroadcastChannel(InternetStatus.OFFLINE)
    val internetStatus get() = _internetStatus.openSubscription().distinctSeq()


    override fun onCreate() {
        super.onCreate()
        _instance = WeakReference(this)

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        LeakCanary.install(this)

        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true)

        Kotpref.init(this)
        library = Library().apply {
            onCreate()
        }
        search = OnlineSearchService()

        search.onCreate()
//        library.onCreate()
//        fileSync.onCreate()

        // Start the MusicService
//        startService(Intent(this, MusicService::class.java))
//        startService(Intent(this, Library::class.java))
        SyncService.initDeviceId()
//        startService(Intent(this, OnlineSearchService::class.java))
//        startService(Intent(this, FileSyncService::class.java))

//        val currNet = connectivityManager.getNetworkCapabilities(connectivityManager.isActiveNetworkMetered)

        // Initial internet status
        task(UI) {
            delay(500)

            val cm = connectivityManager
            val onlineConns = cm.allNetworks.map { cm.getNetworkCapabilities(it) }.filter {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            _internetStatus puts when {
                onlineConns.any { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } -> InternetStatus.UNLIMITED
                onlineConns.isNotEmpty() -> InternetStatus.LIMITED
                else -> InternetStatus.OFFLINE
            }

            val netReq = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(netReq, object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    super.onLost(network)
                    if (!hasInternet) {
                        _internetStatus puts InternetStatus.OFFLINE
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _internetStatus puts when {
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> InternetStatus.UNLIMITED
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> InternetStatus.LIMITED
                        else -> InternetStatus.OFFLINE
                    }
                }
            })
        }
    }

    val hasInternet: Boolean get() = run {
        val mgr = connectivityManager
        mgr.allNetworks.any {
            mgr.getNetworkCapabilities(it).hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}

val Context.kryo inline get() = App.kryo

data class Size(val width: Int, val height: Int)
val Context.screenSize get(): Size {
    val metrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(metrics)
    return Size(metrics.widthPixels, metrics.heightPixels)
}

//val Context.music get() = MusicService.waitForIt(this)

/**
 * Pass ID from parent to child fragment that identifies the ViewModel to use?????????
 */
object MusicModelProviders {
    private val viewModelStores = mutableListOf<Pair<Int, WeakReference<ViewModelStore>>>()

    @MainThread
    fun of(fragment: Fragment, newScope: Boolean = true): ViewModelProvider {
        val application = fragment.activity!!.application!!
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)

        fun makeNewScope(): ViewModelProvider {
            return ViewModelProvider(
                fragment.viewModelStore.also {
                    if (viewModelStores.last().first != fragment.id) {
                        viewModelStores.add(fragment.id to WeakReference(it))
                    }
                },
                factory
            )
        }

        return if (newScope) {
            makeNewScope()
        } else {
            viewModelStores.removeAll { it.second.get() == null }
            // find most recent modelStore not associated with the given fragment
            // this should represent the fragment that created this one
            val existing = viewModelStores.findLast { it.first != fragment.id }?.second?.get()
            if (existing != null) {
                ViewModelProvider(existing, factory)
            } else {
                makeNewScope()
            }
        }
    }
}