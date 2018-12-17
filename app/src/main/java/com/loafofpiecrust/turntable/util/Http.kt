package com.loafofpiecrust.turntable.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.loafofpiecrust.turntable.serialize.registerAllTypes
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.HttpPlainText
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.content.TextContent
import io.ktor.http.*

val http = HttpClient(OkHttp) {
    install(HttpPlainText) {
        defaultCharset = Charsets.UTF_8
    }
    install(JsonFeature) {
        serializer = GsonSerializer {
            registerAllTypes()
        }
    }
    defaultRequest {
        userAgent("com.loafofpiecrust.turntable/0.1alpha")
    }
}

suspend fun HttpResponse.gson(): JsonElement = use {
    JsonParser().parse(readText())
}

fun HttpRequestBuilder.parameters(vararg params: Pair<String, Any?>) {
    for ((key, value) in params) {
        parameter(key, value)
    }
}

var HttpRequestBuilder.jsonBody: Any
    @Deprecated("No getter", level = DeprecationLevel.ERROR)
    get() = TODO()
    inline set(content) {
        contentType(ContentType.Application.Json)
        body = content
    }

var HttpRequestBuilder.urlEncodedFormBody: Map<String, String>
    @Deprecated("No getter", level = DeprecationLevel.ERROR)
    get() = TODO()
    inline set(content) {
        body = TextContent(
            Parameters.build {
                for ((k, v) in content) {
                    append(k, v)
                }
            }.formUrlEncode(),
            ContentType.Application.FormUrlEncoded
        )
    }