package com.nabd.news

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class NewsHttp(private val client: OkHttpClient) {
    data class Document(val code: Int, val body: String, val url: String, val headers: Headers, val bytes: ByteArray = byteArrayOf())
    // No auth headers, cookies, browser impersonation, JS execution, AMP/paywall variants, or retry loops.
    fun get(url: String, limit: Int = 4_000_000, redirects: Int = 3, conditional: Map<String,String> = emptyMap()): Document {
        var current = url.toHttpUrlOrNull() ?: throw IOException("Invalid URL")
        require(current.isHttps && current.username.isBlank() && current.password.isBlank())
        repeat(redirects + 1) { index ->
            client.newCall(Request.Builder().url(current).header("User-Agent", "NabdPersonal/1.2 (+personal RSS reader)").apply { conditional.filterKeys { it in setOf("If-None-Match","If-Modified-Since") }.forEach { (key,value)->header(key,value) } }.get().build()).execute().use { response ->
                if (response.code in 300..399 && response.code != 304) {
                    val next = response.header("Location")?.let { current.resolve(it) } ?: throw IOException("Missing redirect")
                    if (index == redirects || !next.isHttps || next.username.isNotBlank() || next.password.isNotBlank() || !PublisherScope.samePublisher(url, next.toString()))
                        return Document(response.code, "", current.toString(), response.headers)
                    current = next
                } else {
                    val body = response.body
                    if (body == null || !response.isSuccessful) return Document(response.code, "", current.toString(), response.headers)
                    require(body.contentLength() <= limit)
                    val bytes = body.byteStream().use { it.readBounded(limit + 1) }
                    require(bytes.size <= limit)
                    val charset = body.contentType()?.charset()
                    val text = if (body.contentType()?.subtype?.contains("html", true) == true)
                        org.jsoup.Jsoup.parse(bytes.inputStream(), charset?.name(), current.toString()).outerHtml()
                    else bytes.toString(charset ?: Charsets.UTF_8)
                    return Document(response.code, text, current.toString(), response.headers, bytes)
                }
            }
        }
        throw IOException("Too many redirects")
    }
}
