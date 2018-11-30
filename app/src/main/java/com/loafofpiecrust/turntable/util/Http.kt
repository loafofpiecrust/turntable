package com.loafofpiecrust.turntable.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Http {
    val client by lazy {
//        HttpClient()
        HttpClient(Android)
//        OkHttpClient.Builder().apply {
//            interceptors().clear()
//        }.build()!!
    }

    enum class CacheLevel() {
        // 0 = no cache (?), 1 = few minutes (artists), 2 = hours (albums)
        MINIMAL(
//            CacheControl.Builder()
//            .maxAge(10, TimeUnit.SECONDS)
//            .build()
        ),
        PAGE(
//            CacheControl.Builder()
//            .maxAge(10, TimeUnit.MINUTES) // when we have internet
//            .maxStale(6, TimeUnit.HOURS) // if we can't get online
//            .build()
        ),
        SESSION(
//            CacheControl.Builder()
//            .maxAge(1, TimeUnit.HOURS)
//            .maxStale(1, TimeUnit.DAYS)
//            .build()
        )
    }
    
    suspend fun get(
        url: String,
        params: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf(),
        cacheLevel: CacheLevel = CacheLevel.MINIMAL
    ): HttpResponse = withContext(Dispatchers.IO) {
        client.get<HttpResponse> {
            url(url)
            params.forEach { k, v ->
                parameter(k, v)
            }
            headers.forEach { k, v ->
                header(k, v)
            }
        }
    }

    suspend fun post(
        url: String,
        body: Any,
        params: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf()
    ): HttpResponse = withContext(Dispatchers.IO) {
        client.post<HttpResponse> {
            url(url)
            headers.forEach { k, v ->
                header(k, v)
            }
            params.forEach { k, v ->
                parameter(k, v)
            }
            when (body) {
                is Map<*, *> -> this.body = FormDataContent(Parameters.build {
                    body.forEach { k, v -> parameter(k.toString(), v) }
                })
                is JsonElement -> this.body = TextContent(
                    body.toString(),
                    ContentType.parse("application/json")
                )
                else -> this.body = TextContent(
                    body.toString(),
                    ContentType.parse("text/plain")
                )
            }
        }
    }
}

//suspend fun Call.executeSuspended(): Response = suspendCancellableCoroutine { cont ->
//    var completed = false
//    cont.invokeOnCancellation {
//        if (!isCanceled && !completed) {
//            cancel()
//        }
//    }
//
//    enqueue(object: Callback {
//        override fun onFailure(call: Call, e: IOException) {
//            completed = true
//            if (call.isCanceled) {
//                cont.cancel()
//            } else {
//                cont.resumeWithException(e)
//            }
//        }
//        override fun onResponse(call: Call, response: Response) {
//            completed = true
//            try {
//                cont.resume(response)
//            } catch (e: CancellationException) {
//                response.close()
//            }
//        }
//    })
//}

//val Response.text: String get() = body()!!.string()!!
//val Response.gson: JsonElement get() = use { JsonParser().parse(body()!!.charStream()!!) }

suspend fun HttpResponse.text(): String = readText(Charsets.UTF_8)

suspend fun HttpResponse.gson(): JsonElement = use {
    JsonParser().parse(readText(Charsets.UTF_8))
}