package com.loafofpiecrust.turntable

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.chibatching.kotpref.Kotpref
import com.evernote.android.state.StateSaver
import com.github.ajalt.timberkt.Timber
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.GsonBuilder
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.serialize.registerAllTypes
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.sync.MessageReceiverService
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.sync.SyncSession
import com.loafofpiecrust.turntable.util.distinctSeq
import io.paperdb.Paper
import io.paperdb.PaperSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.first
import org.jetbrains.anko.connectivityManager
import org.jetbrains.anko.toast
import java.lang.reflect.Type
import kotlin.coroutines.CoroutineContext


class App: Application() {
    enum class InternetStatus {
        UNLIMITED,
        LIMITED,
        OFFLINE
    }

    companion object: CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main

        /// Safe because `App` is **always** a singleton by definition.
        lateinit var instance: App
            private set

        inline fun launchWith(crossinline block: suspend (context: Context) -> Unit) {
            launch {
                block(instance)
            }
        }

        inline fun withInternet(block: () -> Unit) {
            if (currentInternetStatus.valueOrNull == InternetStatus.OFFLINE) {
                instance.toast(R.string.no_internet)
            } else {
                block()
            }
        }

//        val kryo by threadLocalLazy {
//            Kryo().apply {
//                instantiatorStrategy = SingletonInstantiatorStrategy(
//                    Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())
//                )
//
//                isRegistrationRequired = false
//                references = true
//
//                // TODO: Switch to CompatibleFieldSerializer or TagFieldSerializer once Song.artworkUrl is removed or any other refactoring goes down.
//                setDefaultSerializer(CompatibleFieldSerializer::class.java)
//                addDefaultSerializer(ConflatedBroadcastChannel::class.java, CBCSerializer::class.java)
//                addDefaultSerializer(UUID::class.java, UUIDSerializer::class.java)
//                SubListSerializers.addDefaultSerializers(this)
//
//                // Register types under unique IDs
//                // This method is resilient to moves and renames.
//                // Last ID: 115
//                register(SongId::class.java, 100)
//                register(AlbumId::class.java, 101)
//                register(ArtistId::class.java, 102)
//
//                register(Song::class.java, 103)
//                register(LocalAlbum::class.java, 104)
//
//                register(StaticQueue::class.java, 110)
//                register(CombinedQueue::class.java, 113)
//
//                register(emptyList<Nothing>().javaClass, 105)
//                register(UUID::class.java, UUIDSerializer(), 106)
//                register(ArrayList::class.java, 107)
//                register(Arrays.asList(0).javaClass, ArraysAsListSerializer(), 108)
//
//                register(User::class.java, 109)
//                register(Friend::class.java, 111)
//                register(ConflatedBroadcastChannel::class.java, 112)
//
//                // playlists
//                register(SongPlaylist::class.java, 114)
//                register(SongPlaylist.Track::class.java, 115)
//            }
//        }

//        fun <T> kryoWithRefs(block: (Kryo) -> T): T {
//            kryo.references = true
//            return block(kryo).also { kryo.references = false }
//        }

        val currentInternetStatus = ConflatedBroadcastChannel(InternetStatus.OFFLINE)
        val internetStatus: ReceiveChannel<InternetStatus>
            get() = currentInternetStatus.openSubscription().distinctSeq()

        val gson = GsonBuilder().apply {
            registerAllTypes()
        }.create()
    }

//    val fileSync = FileSyncService()
    lateinit var search: OnlineSearchService

    /**
     * Serialization reasoning:
     * Use JSON for everything.
     * - Saving to disk/preferences
     * - Sending across network
     * - Backup to cloud
     * This provides us with maximum compatibility between platforms and versions.
     * TODO: Allow Paper to have switchable serialization & storage backends
     * Even if there are issues with it on different platforms, using JSON
     * for everything provides the best possible forward-compatibility.
     *
     * Use GSON on Android platform, keeping as compatible as possible with using kotlinx.serialization.
     * Transition over to kotlinx.serialization once they figure out multiplatform polymorphism:
     * https://github.com/Kotlin/kotlinx.serialization/issues/194
     */
    private fun prepareSerialization() = with(Paper) {
        init(filesDir)
        serializer = object: PaperSerializer {
            override suspend fun <T> deserialize(bytes: ByteArray, typeToken: Type?): T {
                val s = bytes.toString(Charsets.UTF_8)
                return gson.fromJson(s, typeToken)
            }

            override suspend fun <T> serialize(value: T?, typeToken: Type?): ByteArray {
                return gson.toJson(value, typeToken).toByteArray(Charsets.UTF_8)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for heap analysis.
//            // You should not init your app in this process.
//            return
//        }
//        LeakCanary.install(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        prepareSerialization()

        FirebaseApp.initializeApp(this)
        FirebaseAnalytics.getInstance(this)

        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true)

        Kotpref.init(this)
        search = OnlineSearchService()

        Sync.initDeviceId()

        SyncSession.processMessages(
            MessageReceiverService.messages
        )

        runBlocking {
            UserPrefs.lastOpenTime puts UserPrefs.currentOpenTime.openSubscription().first()
            UserPrefs.currentOpenTime puts System.currentTimeMillis()
        }

        // Initial internet status
        launch {
            delay(300)
            watchConnectionStatus()
        }
    }

    private fun onConnectionChanged(networks: Array<Network>) {
        val onlineConns = networks.asSequence().map {
            connectivityManager.getNetworkCapabilities(it)
        }.filter {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        currentInternetStatus puts when {
            onlineConns.any {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } -> InternetStatus.UNLIMITED
            onlineConns.iterator().hasNext() -> InternetStatus.LIMITED
            else -> InternetStatus.OFFLINE
        }
        Timber.i { "internet status: ${currentInternetStatus.valueOrNull}" }
    }

    private fun watchConnectionStatus() {
        onConnectionChanged(connectivityManager.allNetworks)

        val netReq = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(netReq, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                if (!hasInternet) {
                    currentInternetStatus puts InternetStatus.OFFLINE
                }
                Timber.i { "internet status: ${currentInternetStatus.valueOrNull}" }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                if (!hasInternet) {
                    currentInternetStatus puts InternetStatus.OFFLINE
                }
                Timber.i { "internet status: ${currentInternetStatus.valueOrNull}" }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                onConnectionChanged(arrayOf(network))
            }
        })
    }

    val hasInternet: Boolean get() = run {
        val mgr = connectivityManager
        mgr.allNetworks.any {
            mgr.getNetworkCapabilities(it).hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}