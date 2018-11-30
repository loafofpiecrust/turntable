package com.loafofpiecrust.turntable

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.chibatching.kotpref.Kotpref
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.evernote.android.state.StateSaver
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.LocalAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.SongPlaylist
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.MessageReceiverService
import com.loafofpiecrust.turntable.sync.SyncSession
import com.loafofpiecrust.turntable.util.CBCSerializer
import com.loafofpiecrust.turntable.util.SingletonInstantiatorStrategy
import com.loafofpiecrust.turntable.util.distinctSeq
import com.loafofpiecrust.turntable.util.threadLocalLazy
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.SubListSerializers
import de.javakaffee.kryoserializers.UUIDSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import org.jetbrains.anko.connectivityManager
import org.objenesis.strategy.StdInstantiatorStrategy
import java.util.*
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

        val kryo by threadLocalLazy {
            Kryo().apply {
                instantiatorStrategy = SingletonInstantiatorStrategy(
                    Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())
                )

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

                // Register types under unique IDs
                // This method is resilient to moves and renames.
                // Last ID: 115
                register(SongId::class.java, 100)
                register(AlbumId::class.java, 101)
                register(ArtistId::class.java, 102)

                register(Song::class.java, 103)
                register(LocalAlbum::class.java, 104)

                register(StaticQueue::class.java, 110)
                register(CombinedQueue::class.java, 113)

                register(emptyList<Nothing>().javaClass, 105)
                register(UUID::class.java, UUIDSerializer(), 106)
                register(ArrayList::class.java, 107)
                register(Arrays.asList(0).javaClass, ArraysAsListSerializer(), 108)

                register(User::class.java, 109)
                register(Friend::class.java, 111)
                register(ConflatedBroadcastChannel::class.java, 112)

                // playlists
                register(SongPlaylist::class.java, 114)
                register(SongPlaylist.Track::class.java, 115)
            }
        }

//        fun <T> kryoWithRefs(block: (Kryo) -> T): T {
//            kryo.references = true
//            return block(kryo).also { kryo.references = false }
//        }

        val currentInternetStatus = ConflatedBroadcastChannel(InternetStatus.OFFLINE)
        val internetStatus: ReceiveChannel<InternetStatus>
            get() = currentInternetStatus.openSubscription().distinctSeq()
    }

//    val fileSync = FileSyncService()
    lateinit var search: OnlineSearchService



    override fun onCreate() {
        super.onCreate()
        instance = this

//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for heap analysis.
//            // You should not init your app in this process.
//            return
//        }
//        LeakCanary.install(this)

        FirebaseApp.initializeApp(this)
        FirebaseAnalytics.getInstance(this)

        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true)

        Kotpref.init(this)
        search = OnlineSearchService()

//        search.onCreate()
//        fileSync.onCreate()

        Sync.initDeviceId()

        SyncSession.processMessages(
            MessageReceiverService.messages
        )

        UserPrefs.lastOpenTime puts UserPrefs.currentOpenTime.value

//        val currNet = connectivityManager.getNetworkCapabilities(connectivityManager.isActiveNetworkMetered)

        // Initial internet status
        launch {
            delay(500)

            val cm = connectivityManager
            val onlineConns = cm.allNetworks.map { cm.getNetworkCapabilities(it) }.filter {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            currentInternetStatus puts when {
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
                        currentInternetStatus puts InternetStatus.OFFLINE
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    currentInternetStatus puts when {
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