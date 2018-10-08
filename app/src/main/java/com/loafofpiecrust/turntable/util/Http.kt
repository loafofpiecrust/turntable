package com.loafofpiecrust.turntable.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mcxiaoke.koi.ext.closeQuietly
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.suspendCoroutine

object Http {
    val client by lazy {
//        HttpClient()
        OkHttpClient.Builder().apply {
            interceptors().clear()
        }.build()!!
    }

    enum class CacheLevel(val controller: CacheControl) {
        // 0 = no cache (?), 1 = few minutes (artists), 2 = hours (albums)
        MINIMAL(CacheControl.Builder()
            .maxAge(10, TimeUnit.SECONDS)
            .build()
        ),
        PAGE(CacheControl.Builder()
            .maxAge(10, TimeUnit.MINUTES) // when we have internet
            .maxStale(6, TimeUnit.HOURS) // if we can't get online
            .build()
        ),
        SESSION(CacheControl.Builder()
            .maxAge(1, TimeUnit.HOURS)
            .maxStale(1, TimeUnit.DAYS)
            .build()
        )
    }
    
    suspend fun get(
        url: String,
        params: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf(),
        cacheLevel: CacheLevel = CacheLevel.MINIMAL
    ): Response {
        val url = HttpUrl.parse(url)!!.newBuilder().apply {
            params.forEach { k, v ->
                addQueryParameter(k, v)
            }
        }.build()

        val req = Request.Builder()
            .url(url)
            .get()
            .headers(Headers.of(headers))
            .cacheControl(cacheLevel.controller).build()

        return client.newCall(req).executeSuspended()
    }

    suspend fun post(
        url: String,
        body: Any,
        params: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf()
    ): Response {
        val url = HttpUrl.parse(url)!!.newBuilder().apply {
            params.forEach { k, v ->
                addQueryParameter(k, v)
            }
        }.build()

        val req = Request.Builder()
            .url(url)
            .post(when (body) {
                is Map<*, *> -> FormBody.Builder().apply {
                    body.forEach { k, v -> add(k.toString(), v.toString()) }
                }.build()
                is JsonElement -> RequestBody.create(MediaType.parse("application/json"), body.toString())
                is RequestBody -> body
                else -> RequestBody.create(MediaType.parse("text/plain"), body.toString())
            })
            .headers(Headers.of(headers))
            .build()

        return client.newCall(req).executeSuspended()
    }
}

suspend fun Call.executeSuspended(): Response = suspendCancellableCoroutine { cont ->
    var completed = false
    cont.invokeOnCancellation {
        if (!isCanceled && !completed) {
            cancel()
        }
    }

    enqueue(object: Callback {
        override fun onFailure(call: Call, e: IOException) {
            completed = true
            if (call.isCanceled) {
                cont.cancel()
            } else {
                cont.resumeWithException(e)
            }
        }
        override fun onResponse(call: Call, response: Response) {
            completed = true
            try {
                cont.resume(response)
            } catch (e: CancellationException) {
                response.closeQuietly()
            }
        }
    })
}

val Response.text: String get() = body()!!.string()!!
val Response.gson: JsonElement get() = use { JsonParser().parse(body()!!.charStream()!!) }
