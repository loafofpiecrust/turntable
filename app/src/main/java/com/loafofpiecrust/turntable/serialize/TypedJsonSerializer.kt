package com.loafofpiecrust.turntable.serialize

import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.serialization.*
import kotlinx.serialization.context.CompositeModule
import kotlinx.serialization.context.MutableSerialContext
import kotlinx.serialization.context.SerialModule
import kotlinx.serialization.context.SimpleModule
import kotlinx.serialization.internal.NullableSerializer
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlin.reflect.KClass


object TypedJson {
    val types = typeHierarchyModule {
        // Each tree has its own mapping of types
        // this allows the same name to be used in different trees
        // if we're pretty sure that there's no type overlap
        subTypesOf<Message> {
            abstract<PlayerAction>()
    //        concrete("Play", PlayerAction.Play.serializer())
            concrete("PlaySongs", PlayerAction.PlaySongs.serializer())
        }
    }

    val module = CompositeModule(listOf(
        subTypesOf<Message>(
//            "Play" to subType(PlayerAction.Play.`$serializer`),
//            "Pause" to subType(PlayerAction.Pause.`$serializer`),
//            "PlaySongs" to subType(PlayerAction.PlaySongs.serializer()),
//            "TogglePause" to subType(PlayerAction.TogglePause.`$serializer`),
//            "Stop" to subType(PlayerAction.Stop.`$serializer`),
//            "Seek" to subType(PlayerAction.SeekTo.serializer()),
//            "Enqueue" to subType(PlayerAction.Enqueue.serializer()),
//            "QueuePosition" to subType(PlayerAction.QueuePosition.serializer()),
//            "RelativePosition" to subType(PlayerAction.RelativePosition.serializer()),
//            "RemoveQueueItem" to subType(PlayerAction.RemoveFromQueue.serializer()),
//            "ShiftQueueItem" to subType(PlayerAction.ShiftQueueItem.serializer()),
//            "Recommendation" to subType(Message.Recommend.serializer()),
//            "SyncRequest" to subType(Sync.Request.serializer()),
//            "SyncResponse" to subType(Sync.Response.serializer()),
//            "FriendRequest" to subType(Friend.Request.`$serializer`),
//            "FriendResponse" to subType(Friend.Response.serializer())
        ),
        subTypesOf<MusicId>(
//            "Song" to subType(SongId.serializer()),
//            "Album" to subType(AlbumId.serializer()),
//            "Artist" to subType(ArtistId.serializer())
        ),
        subTypesOf<Playlist>(
//            "Songs" to subType(SongPlaylist.serializer())
        )
    ))
}


class SubTypeModule: SerialModule {
    class Tree<SuperType: Any> {
        val nameToSerializer = mutableMapOf<String, KSerializer<*>>()
        val classToName = mutableMapOf<KClass<*>, String>()
        val abstractTypes = mutableListOf<SerialModule>()

        inline fun <reified T : SuperType> abstract() {
            abstractTypes.add(SimpleModule(T::class, AbstractSerializer()))
        }

        inline fun <reified T : SuperType> concrete(typeName: String, serializer: KSerializer<T>) {
            nameToSerializer[typeName] = serializer
            classToName[T::class] = typeName
        }

        inline fun <reified T: SuperType> open(typeName: String, serializer: KSerializer<T>) {
            abstract<T>()
            concrete(typeName, serializer)
        }


        inner class AbstractSerializer<T: SuperType>: KSerializer<T> {
            override val descriptor = SerialClassDescImpl("TypedValue").apply {
                addElement("type")
                addElement("value")
            }

            override fun deserialize(input: Decoder): T {
                val struct = input.beginStructure(descriptor)
                val typeName = struct.decodeStringElement(descriptor, struct.decodeElementIndex(descriptor))
                val serializer = nameToSerializer[typeName]
                val instance = if (serializer != null) {
                    struct.decodeSerializableElement(descriptor, struct.decodeElementIndex(descriptor), serializer)
                } else {
                    throw IllegalStateException("Unable to deserialize unknown subtype $typeName")
                }
                struct.endStructure(descriptor)
                return instance as T
            }

            override fun serialize(output: Encoder, obj: T) {
                val typeName = classToName[obj::class]
                    ?: throw IllegalStateException(
                        "Unable to serialize unknown subtype ${obj::class.qualifiedName}"
                    )

                val struct = output.beginStructure(descriptor)
                struct.encodeStringElement(descriptor, 0, typeName)
                val serializer = nameToSerializer[typeName] as KSerializer<T>
                struct.encodeSerializableElement(descriptor, 1, serializer, obj)
                struct.endStructure(descriptor)
            }
        }
    }

    val trees = mutableListOf<Tree<*>>()

    override fun registerIn(context: MutableSerialContext) {
        for (tree in trees) {
            for (module in tree.abstractTypes) {
                module.registerIn(context)
            }
        }
    }

    inline fun <reified T: Any> subTypesOf(block: Tree<T>.() -> Unit) {
        trees.add(Tree<T>().apply {
            abstract<T>()
            block()
        })
    }
}

fun typeHierarchyModule(block: SubTypeModule.() -> Unit) =
    SubTypeModule().apply(block)

data class SubtypeRegistration<T: Any>(
    val key: String,
    val klass: KClass<out T>,
    val serializer: KSerializer<*>
)

class SuperTypeSerializer<T: Any>(
    private val subtypes: List<SubtypeRegistration<T>>
): KSerializer<T> {
    override val descriptor = SerialClassDescImpl("TypedValue").apply {
        addElement("type")
        addElement("value")
    }

    override fun deserialize(input: Decoder): T {
        val struct = input.beginStructure(descriptor)
        val typeName = struct.decodeStringElement(descriptor, struct.decodeElementIndex(descriptor))
        val subtype = subtypes.find { it.key == typeName }
        val instance = if (subtype != null) {
            struct.decodeSerializableElement(descriptor, struct.decodeElementIndex(descriptor), subtype.serializer)
        } else {
            throw IllegalStateException("Unable to deserialize unknown subtype $typeName")
        }
        struct.endStructure(descriptor)
        return instance as T
    }

    override fun serialize(output: Encoder, obj: T) {
        val subtype = subtypes.find { it.klass == obj::class }
            ?: throw IllegalStateException(
                "Unable to serialize unknown subtype ${obj::class.simpleName}"
            )


        val struct = output.beginStructure(descriptor)
        struct.encodeStringElement(descriptor, 0, subtype.key)
        val serializer = output.context.getByValue(obj) ?: subtype.serializer as KSerializer<T>
        struct.encodeSerializableElement(descriptor, 1, serializer, obj)
        struct.endStructure(descriptor)
    }
}

private typealias SubTypePair<T> = Pair<KClass<T>, KSerializer<T>>
inline fun <reified T: Any> subType(serializer: KSerializer<T>): SubTypePair<out T> = T::class to serializer

inline fun <reified T: Any> subTypesOf(vararg subTypes: Pair<String, SubTypePair<out T>>) =
    SimpleModule(T::class, SuperTypeSerializer(subTypes.map {
        SubtypeRegistration(it.first, it.second.first, it.second.second)
    }))


@Serializer(forClass = ConflatedBroadcastChannel::class)
class CBCSerializer<T: Any>(element: KSerializer<T>): KSerializer<ConflatedBroadcastChannel<T>> {
    private val innerSerializer = NullableSerializer(element)
    override val descriptor get() = innerSerializer.descriptor

    override fun serialize(output: Encoder, obj: ConflatedBroadcastChannel<T>) {
        innerSerializer.serialize(output, obj.valueOrNull)
    }

    override fun deserialize(input: Decoder): ConflatedBroadcastChannel<T> {
        val value = innerSerializer.deserialize(input)
        return if (value == null) {
            ConflatedBroadcastChannel()
        } else ConflatedBroadcastChannel(value)
    }
}

