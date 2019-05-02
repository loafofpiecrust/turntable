package com.loafofpiecrust.turntable.model.sync

import android.os.Parcelable
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIgnore
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.github.ajalt.timberkt.Timber
import com.github.salomonbrys.kotson.string
import com.google.gson.*
import com.loafofpiecrust.turntable.md5
import com.loafofpiecrust.turntable.repository.remote.StreamCache
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.sync.Sync
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import java.lang.reflect.Type

/**
 *
 */
@Parcelize
@DynamoDBTable(tableName = "TurntableUsers")
data class User(
    @DynamoDBHashKey(attributeName = "email")
    var username: String,
    var deviceId: String,
    var displayName: String?
): Parcelable {
    internal constructor(): this("", "", null)

    override fun hashCode(): Int = username.hashCode()
    override fun equals(other: Any?) =
        other is User && other.username == this.username

    @get:DynamoDBIgnore
    val name: String get() =
        if (displayName.isNullOrBlank()) {
            username
        } else displayName!!

    @get:DynamoDBIgnore
    val profileImageUrl: String get() =
        "https://www.gravatar.com/avatar/${username.toLowerCase().md5()}?s=100&d=retro"

    companion object {
        fun resolve(username: String): User? = run {
            if (username.isBlank()) return null

            val db = OnlineSearchService.instance.dbMapper
            db.load(User::class.java, username)
        }
    }

    fun upload() {
        if (username.isBlank()) return

        println("sync: saving user info under $username")
        GlobalScope.launch {
            val db = OnlineSearchService.instance.dbMapper
            try {
                db.save(this@User)
            } catch (e: Exception) {
                Timber.e(e) { "Failed uploading user data" }
            }
        }
    }

    fun refresh(): Deferred<User> = GlobalScope.async(Dispatchers.IO) {
        val newer = resolve(username)
        if (newer != null) {
            deviceId = newer.deviceId
            displayName = newer.displayName
        }
        newer ?: this@User
    }
}

class UserSerializer: JsonSerializer<User>, JsonDeserializer<User> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): User {
        val username = json.string
        return if (username.isBlank() || username == Sync.selfUser.username) {
            Sync.selfUser
        } else {
            User.resolve(json.string)!!
        }
    }

    override fun serialize(src: User, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(src.username)
    }
}