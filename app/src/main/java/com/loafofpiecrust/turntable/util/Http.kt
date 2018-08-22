package com.loafofpiecrust.turntable.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.suspendCoroutine

object Http {
    val client by lazy {
        OkHttpClient.Builder().apply {
            interceptors().clear()
        }.build()!!
    }

    suspend fun get(
        url: String,
        params: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf(),
        cacheLevel: Int = 1 // 0 = no cache (?), 1 = few minutes (artists), 2 = hours (albums)
    ): Response {
        val url = HttpUrl.parse(url)?.newBuilder()?.apply {
            params.forEach { k, v ->
                addQueryParameter(k, v)
            }
        }?.build()

        val req = Request.Builder()
            .url(url!!)
            .get()
            .headers(Headers.of(headers))
            .cacheControl(when (cacheLevel) {
                0 -> CacheControl.Builder()
                    .maxAge(10, TimeUnit.SECONDS)
                2 -> CacheControl.Builder()
                    .maxAge(1, TimeUnit.HOURS)
                    .maxStale(1, TimeUnit.DAYS)
                else -> CacheControl.Builder()
                    .maxAge(10, TimeUnit.MINUTES) // when we have internet
                    .maxStale(6, TimeUnit.HOURS) // if we can't get online
            }.build())
            .build()
        return suspendCoroutine { cont ->
            client.newCall(req).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }
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
        return suspendCoroutine { cont ->
            val call = client.newCall(req)
            call.enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }
    }
}

val Response.text: String get() = body()!!.string()!!
val Response.gson: JsonElement get() = JsonParser().parse(body()!!.string()!!)