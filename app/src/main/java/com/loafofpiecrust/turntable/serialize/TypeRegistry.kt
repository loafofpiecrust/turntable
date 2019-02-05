package com.loafofpiecrust.turntable.serialize

import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.typeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.LocalAlbum
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.model.playlist.SongPlaylist
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.queue.Queue
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.repository.remote.Discogs
import com.loafofpiecrust.turntable.repository.remote.MusicBrainz
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.sync.SyncSession
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.util.*

fun GsonBuilder.registerAllTypes() {
    // general settings
    enableComplexMapKeySerialization()

    // type registrations
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Playlist>().registerSubtypes(
            SongPlaylist::class to "SongPlaylist"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Recommendable>().registerSubtypes(
            MusicId::class to "MusicId",
            SongId::class to "SongId",
            AlbumId::class to "AlbumId",
            ArtistId::class to "ArtistId",
            PlaylistId::class to "PlaylistId"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Album>().registerSubtypes(
            LocalAlbum::class to "LocalAlbum",
            RemoteAlbum::class to "RemoteAlbum"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Album.RemoteDetails>().registerSubtypes(
            Spotify.AlbumDetails::class to "Spotify.AlbumDetails",
            Discogs.AlbumDetails::class to "Discogs.AlbumDetails",
            MusicBrainz.AlbumDetails::class to "MusicBrainz.AlbumDetails"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Message>().registerSubtypes(
            Message.Recommend::class to "Recommendation",
            Sync.Request::class to "SyncRequest",
            Sync.Response::class to "SyncResponse",
            Friend.Request::class to "FriendRequest",
            Friend.Response::class to "FriendResponse",
            Friend.Remove::class to "FriendRemove",
            PlayerAction::class to "PlayerAction",
            PlayerAction.Play::class to "Play",
            PlayerAction.Pause::class to "Pause",
            PlayerAction.PlaySongs::class to "PlaySongs",
            PlayerAction.TogglePause::class to "TogglePause",
            PlayerAction.Stop::class to "Stop",
            PlayerAction.Enqueue::class to "Enqueue",
            PlayerAction.QueuePosition::class to "QueuePosition",
            PlayerAction.RelativePosition::class to "RelativePosition",
            PlayerAction.RemoveFromQueue::class to "RemoveQueueItem",
            PlayerAction.ShiftQueueItem::class to "ShiftQueueItem",
            PlayerAction.ReplaceQueue::class to "ReplaceQueue",
            PlayerAction.SeekTo::class to "Seek",
            SyncSession.Ping::class to "Ping",
            SyncSession.EndSync::class to "EndSync"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Sync.Mode>().registerSubtypes(
            Sync.Mode.None::class to "None",
            Sync.Mode.OneOnOne::class to "OneOnOne"
        )
    )
    registerTypeAdapterFactory(
        RuntimeTypeAdapterFactory.of<Queue>().registerSubtypes(
            StaticQueue::class to "StaticQueue",
            CombinedQueue::class to "CombinedQueue"
        )
    )

    registerTypeAdapterFactory(object: TypeAdapterFactory {
        override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            return try {
                val instance = type.rawType.getDeclaredField("INSTANCE")
                typeAdapter {
                    read {
                        nextString()
                        instance.get(null) as T
                    }
                    write {
                        value(type.rawType.simpleName)
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    })

    registerTypeAdapter(ConflatedBroadcastChannel::class.java, CBCTypeAdapter<Any>())
    registerTypeAdapter(Date::class.java, DateTimestampSerializer())
    registerTypeAdapter(ImmutableList::class.java, ImmutableListTypeAdapter<Any>())
    registerTypeAdapter(ImmutableSet::class.java, ImmutableSetTypeAdapter<Any>())
    registerTypeAdapter(ImmutableMap::class.java, ImmutableMapTypeAdapter<Any, Any>())
}