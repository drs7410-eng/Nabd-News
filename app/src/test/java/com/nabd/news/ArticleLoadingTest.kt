package com.nabd.news

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercises the real repository, parser, access checks and persistence boundary with no real publisher requests. */
class ArticleLoadingTest {
    private val source = Source(id = "s", name = "مصدر اختبار", url = "https://publisher.test/feed", htmlAllowed = true, termsUrl = "https://publisher.test/terms")
    private val text = "هذه فقرة أصلية طويلة منشورة في صفحة الصحيفة، تحتوي على تفاصيل لا توجد في وصف الخلاصة. ".repeat(12)
    private class State(var article: Article, val source: Source) {
        val requests = mutableListOf<String>()
        val dao = Proxy.newProxyInstance(NabdDao::class.java.classLoader, arrayOf(NabdDao::class.java)) { _, method, args ->
            when (method.name) {
                "worldArticle" -> null // Existing Arabic article has no World metadata.
                "article" -> article
                "allSources" -> listOf(source)
                "saveDetails" -> {
                    article = article.copy(fullContent = args!![1] as String, contentBlocks = args[2] as String,
                        images = args[3] as String, image = args[4] as String, contentOrigin = args[5] as String,
                        detailState = args[6] as String, detailFetchedAt = args[7] as Long)
                    Unit
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as NabdDao
        fun repository(page: String, code: Int = 200): NewsRepository {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request(); requests += request.url.encodedPath
                val robots = request.url.encodedPath == "/robots.txt"
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (robots) 200 else code).message("Test").header("Content-Type", if (robots) "text/plain" else "text/html")
                    .body((if (robots) "User-agent: *\nAllow: /" else page).toResponseBody((if (robots) "text/plain" else "text/html").toMediaType())).build()
            }.build()
            return NewsRepository(dao, client)
        }
    }
    private fun initial() = Article(id = "a", title = "عنوان", body = "مقتطف", summary = "مقتطف", url = "https://publisher.test/a", source = source.name,
        sourceId = source.id, fullContent = "حقل content قصير من سطرين", contentOrigin = "rss_content", detailState = "ready")
    @Test fun shortContentFetchesActualPageAndPersistsFullNativeBlocks() = runBlocking {
        val state = State(initial(), source)
        val repository = state.repository("<html><article><h2>السياق</h2><p>$text</p><p>الفقرة الأخيرة المنشورة</p><img src='/photo.jpg'/></article></html>")
        assertEquals("ready", repository.loadDetails("a"))
        assertEquals(listOf("/robots.txt", "/a"), state.requests)
        assertEquals("article_page", state.article.contentOrigin)
        assertTrue(state.article.fullContent.contains("الفقرة الأخيرة المنشورة"))
        assertEquals("https://publisher.test/photo.jpg", state.article.image)
        repository.loadDetails("a")
        assertEquals("Cached article must not be refetched", 2, state.requests.size)
    }
    @Test fun forbiddenPublisherRetainsExactOriginalWithVisibleFailureState() = runBlocking {
        val state = State(initial(), source)
        assertEquals("restricted", state.repository("", 403).loadDetails("a"))
        assertEquals(initial().fullContent, state.article.fullContent)
        assertTrue(state.article.isExcerpt)
    }
    @Test fun sourceRightsNotEnabledMakesNoExtractionRequest() = runBlocking {
        val state = State(initial(), source.copy(htmlAllowed = false))
        assertEquals("permission_required", state.repository("<article>$text</article>").loadDetails("a"))
        assertTrue(state.requests.isEmpty()); assertEquals(initial().fullContent, state.article.fullContent)
    }
    @Test fun javascriptShellDoesNotBecomeInventedFullArticle() = runBlocking {
        val state = State(initial(), source)
        assertEquals("javascript_or_unavailable", state.repository("<div id='app'></div><script src='/app.js'></script>").loadDetails("a"))
        assertEquals(initial().fullContent, state.article.fullContent)
        assertTrue(state.article.isExcerpt)
    }
}
