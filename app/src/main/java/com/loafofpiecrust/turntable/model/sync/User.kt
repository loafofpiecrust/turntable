package com.loafofpiecrust.turntable.model.sync

import android.os.Parcelable
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIgnore
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.loafofpiecrust.turntable.service.OnlineSearchService
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

/**
 *
 */
@Parcelize
@DynamoDBTable(tableName="TurntableUsers")
data class User(
    @DynamoDBHashKey(attributeName="email")
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

    companion object: AnkoLogger by AnkoLogger<User>() {
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
                error("Failed uploading user data", e)
            }
        }
    }

    fun refresh(): Deferred<User> = GlobalScope.async(Dispatchers.IO) {
        resolve(username)!!
    }
}