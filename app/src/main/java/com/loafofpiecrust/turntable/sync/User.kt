package com.loafofpiecrust.turntable.sync

import android.os.Parcelable
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIgnore
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.loafofpiecrust.turntable.service.OnlineSearchService
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

@Parcelize
@DynamoDBTable(tableName="TurntableUsers")
data class User(
    @get:DynamoDBHashKey(attributeName="email")
    var username: String,
    var deviceId: String,
    var displayName: String?
): Parcelable, AnkoLogger {
    constructor(): this("", "", null)

    @get:DynamoDBIgnore
    val name get() = displayName?.let {
        if (it.isNotBlank()) it else username
    } ?: username

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
            } catch (e: Throwable) {
                error("Failed uploading user data", e)
            }
        }
    }

    fun refresh() = GlobalScope.async {
        val db = OnlineSearchService.instance.dbMapper
        val remote = db.load(User::class.java, username)
        deviceId = remote.deviceId
        displayName = remote.displayName
        this@User
    }
}