package com.loafofpiecrust.turntable

import android.app.Application
import android.content.Context
import android.net.*
import android.util.DisplayMetrics
import com.chibatching.kotpref.Kotpref
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.evernote.android.state.StateSaver
import com.google.firebase.FirebaseApp
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.player.StaticQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.util.*
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.SubListSerializers
import de.javakaffee.kryoserializers.UUIDSerializer
import de.javakaffee.kryoserializers.dexx.ListSerializer
import de.javakaffee.kryoserializers.dexx.MapSerializer
import de.javakaffee.kryoserializers.dexx.SetSerializer
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import org.jetbrains.anko.connectivityManager
import org.jetbrains.anko.windowManager
import org.objenesis.strategy.StdInstantiatorStrategy
import java.lang.ref.WeakReference
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext


class App: Application() {
    enum class InternetStatus {
        UNLIMITED, LIMITED, OFFLINE
    }

    companion object: CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main

        private lateinit var _instance: WeakReference<App>
        val instance: App get() = _instance.get()!!

        val kryo by threadLocalLazy {
            Kryo().apply {
                instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())
                isRegistrationRequired = false
                references = true

                // TODO: Switch to CompatibleFieldSerializer or TagFieldSerializer once Song.artworkUrl is removed or any other refactoring goes down.
                setDefaultSerializer(CompatibleFieldSerializer::class.java)
                addDefaultSerializer(ConflatedBroadcastChannel::class.java, CBCSerializer::class.java)
                addDefaultSerializer(UUID::class.java, UUIDSerializer::class.java)
                SubListSerializers.addDefaultSerializers(this)
//                MapSerializer.registerSerializers(this)
//                ListSerializer.registerSerializers(this)
//                SetSerializer.registerSerializers(this)

                register(SongId::class.java, 100)
                register(AlbumId::class.java, 101)
                register(ArtistId::class.java, 102)

                register(Song::class.java, 103)
                register(Album::class.java, 104)

                register(emptyList<Nothing>().javaClass, 105)
                register(UUID::class.java, UUIDSerializer(), 106)
                register(ArrayList::class.java, 107)
                register(Arrays.asList(0).javaClass, ArraysAsListSerializer(), 108)

                register(SyncService.User::class.java, 109)
                register(StaticQueue::class.java, 110)
                register(SyncService.Friend::class.java, 111)
                register(ConflatedBroadcastChannel::class.java, 112)
            }
        }

//        fun <T> kryoWithRefs(block: (Kryo) -> T): T {
//            kryo.references = true
//            return block(kryo).also { kryo.references = false }
//        }

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

        FirebaseApp.initializeApp(this)

//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for heap analysis.
//            // You should not init your app in this process.
//            return
//        }
//        LeakCanary.install(this)

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
        launch {
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