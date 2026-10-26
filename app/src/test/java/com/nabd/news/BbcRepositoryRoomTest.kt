package com.nabd.news

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** Real generated Room DAO and on-disk SQLite, real Galaxy HTML; only HTTP transport/access-policy responses are controlled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class BbcRepositoryRoomTest {
    private lateinit var context: Application
    private lateinit var db: NabdDatabase
    private val requests = mutableListOf<String>()
    private val source = Source(id = "bbc-test-source", name = "BBC", url = "https://feeds.bbci.co.uk/arabic/rss.xml", htmlAllowed = true, termsUrl = "https://www.bbc.com/usingthebbc/terms/")
    private val databaseName = "bbc-room-reference-test.db"
    @Before fun before() { context = RuntimeEnvironment.getApplication(); context.deleteDatabase(databaseName); reopen() }
    private fun reopen() { db = Room.databaseBuilder(context, NabdDatabase::class.java, databaseName).build() }
    @After fun after() { db.close(); context.deleteDatabase(databaseName) }
    private fun client(html: String = BbcArticleExtractorTest.html(), code: Int = 200, offline: Boolean = false) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request(); requests += request.url.encodedPath
        if (offline) throw IOException("Deliberately offline transport for cache verification")
        val robots = request.url.encodedPath == "/robots.txt"
        val type = if (robots) "text/plain" else "text/html; charset=utf-8"
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (robots) 200 else code).message("Controlled test transport")
            .header("Content-Type", type).body((if (robots) "User-agent: *\nAllow: /" else html).toResponseBody(type.toMediaType())).build()
    }.build()
    private suspend fun seed(allowed: Boolean = true): Article {
        val title = org.jsoup.Jsoup.parse(BbcArticleExtractorTest.html()).selectFirst("h1")!!.text()
        val article = Article(id = "real-galaxy-article", title = title, body = "", summary = "", url = BbcArticleExtractorTest.TRACKED, source = source.name, sourceId = source.id, saved = true)
        db.dao().saveSource(source.copy(htmlAllowed = allowed)); db.dao().addArticles(listOf(article)); return article
    }
    @Test fun catalogActivationParsesAndPersistsFeedWithoutOverwritingPermission() = runBlocking {
        val feed = """<rss version="2.0"><channel><title>RSS test</title><item><title>عنوان اختبار نقل البيانات</title><link>https://example.org/article</link><description>مقتطف من مصدر الاختبار</description></item></channel></rss>"""
        val repository = NewsRepository(db.dao(), client(html = feed))
        val entry = CatalogSource("catalog-test", "مصدر اختبار", "https://example.org/", "https://example.org/feed", "https://example.org/", "test", "اقتصاد")
        val quality = repository.activate(entry)
        assertEquals("ok", quality.status)
        assertEquals(1, db.dao().allArticles().size)
        assertEquals("اقتصاد", db.dao().allSources().single().category)
        db.dao().updateSource(db.dao().allSources().single().copy(htmlAllowed = true, termsUrl = "https://example.org/terms"))
        repository.activate(entry)
        assertEquals(1, db.dao().allSources().size)
        assertEquals(1, db.dao().allArticles().size)
        assertTrue(db.dao().allSources().single().htmlAllowed)
    }
    @Test fun brokenCatalogFeedDoesNotCreateEnabledSource() = runBlocking {
        val entry = CatalogSource("broken", "معطل", "https://example.org/", "https://example.org/feed", "https://example.org/", "test")
        try { NewsRepository(db.dao(), client(code = 403)).activate(entry); fail("Must reject") }
        catch (_: IllegalArgumentException) { assertTrue(db.dao().allSources().isEmpty()) }
    }
    @Test fun actualRepositoryWritesOrderedGalaxyBodyToRoomAndReopensOffline() = runBlocking {
        val original = seed()
        val repository = NewsRepository(db.dao(), client())
        assertEquals("ready", repository.loadDetails(original.id))
        val saved = db.dao().article(original.id)!!
        assertEquals(5373, saved.fullContent.length); assertEquals("article_page", saved.contentOrigin)
        assertEquals(BbcArticleExtractor.VERSION, saved.extractorId)
        assertEquals(36, ContentCodec.readBlocks(saved.contentBlocks).count { it.type == "paragraph" })
        assertEquals(2, ContentCodec.readImages(saved.images).size); assertTrue(saved.saved)
        assertEquals(ArticleReadingState.FULL_ARTICLE, articleReadingState(saved, false))
        assertEquals(listOf("/robots.txt", "/arabic/articles/c6ly0gdxd5jlo"), requests)
        db.close(); reopen(); requests.clear()
        assertEquals("ready", NewsRepository(db.dao(), client(offline = true)).loadDetails(original.id))
        assertTrue("No request should occur when reopening a valid saved article", requests.isEmpty())
        assertEquals(saved.contentBlocks, db.dao().article(original.id)!!.contentBlocks)
    }
    @Test fun rssMergeCannotReplaceSavedBbcBodyImagesStateOrBookmark() = runBlocking {
        val original = seed(); NewsRepository(db.dao(), client()).loadDetails(original.id)
        val saved = db.dao().article(original.id)!!
        db.dao().mergeArticles(listOf(original.copy(id = "different-feed-id", fullContent = "", contentBlocks = "[]", images = "[]", image = "", saved = false, url = BbcArticleExtractorTest.URL)))
        val merged = db.dao().article(original.id)!!
        assertEquals(saved.fullContent, merged.fullContent); assertEquals(saved.contentBlocks, merged.contentBlocks)
        assertEquals(saved.images, merged.images); assertEquals(saved.extractorId, merged.extractorId)
        assertEquals(saved.detailState, merged.detailState); assertTrue(merged.saved)
        assertEquals(1, db.dao().allArticles().size)
    }
    @Test fun disabledSourcePersistsExplicitReasonWithoutSilentlyTreatingExcerptAsFull() = runBlocking {
        val original = seed(false)
        assertEquals("permission_required", NewsRepository(db.dao(), client()).loadDetails(original.id))
        val saved = db.dao().article(original.id)!!
        assertTrue(requests.isEmpty()); assertTrue(saved.detailReason.isNotBlank())
        assertEquals(ArticleReadingState.ACCESS_RESTRICTED, articleReadingState(saved, false))
        assertTrue(saved.fullContent.isBlank())
    }
    @Test fun failedUpdatePreservesSavedBodyAndReportsRestriction() = runBlocking {
        val original = seed(); NewsRepository(db.dao(), client()).loadDetails(original.id)
        val saved = db.dao().article(original.id)!!; requests.clear()
        assertEquals("restricted", NewsRepository(db.dao(), client(code = 403)).loadDetails(original.id, true))
        val restricted = db.dao().article(original.id)!!
        assertEquals(saved.contentBlocks, restricted.contentBlocks)
        assertEquals(ArticleReadingState.ACCESS_RESTRICTED, articleReadingState(restricted, false))
    }
    @Test fun partialDomFallbackIsSavedAsPartialAndNeverPresentedAsFull() = runBlocking {
        val original = seed(); val doc = org.jsoup.Jsoup.parse(BbcArticleExtractorTest.html()); doc.select("script#__NEXT_DATA__").remove()
        assertEquals("partial", NewsRepository(db.dao(), client(doc.outerHtml())).loadDetails(original.id))
        val saved = db.dao().article(original.id)!!
        assertEquals(36, ContentCodec.readBlocks(saved.contentBlocks).count { it.type == "paragraph" })
        assertTrue(saved.isExcerpt); assertTrue(saved.detailReason.isNotBlank())
        assertEquals(ArticleReadingState.PARTIAL_ARTICLE, articleReadingState(saved, false))
    }
    @Test fun oldGenericBbcCacheIsReprocessedInsteadOfHidingTheFix() = runBlocking {
        val original = seed()
        val old = original.copy(fullContent = org.jsoup.Jsoup.parse(BbcArticleExtractorTest.html()).selectFirst("main > div[dir=rtl] > p")!!.text(), contentOrigin = "article_page", detailState = "ready", detailFetchedAt = System.currentTimeMillis())
        db.dao().updateArticle(old)
        assertEquals("ready", NewsRepository(db.dao(), client()).loadDetails(original.id))
        assertEquals(5373, db.dao().article(original.id)!!.fullContent.length)
        assertEquals(BbcArticleExtractor.VERSION, db.dao().article(original.id)!!.extractorId)
        assertFalse(requests.isEmpty())
    }
    @Test fun partialUpdateDoesNotDestroyPreviouslyVerifiedBody() = runBlocking {
        val original = seed(); NewsRepository(db.dao(), client()).loadDetails(original.id)
        val saved = db.dao().article(original.id)!!
        val doc = org.jsoup.Jsoup.parse(BbcArticleExtractorTest.html()); doc.select("main > div[dir=rtl] > p").last()!!.parent()!!.remove()
        assertEquals("partial", NewsRepository(db.dao(), client(doc.outerHtml())).loadDetails(original.id, true))
        val kept = db.dao().article(original.id)!!
        assertEquals(saved.contentBlocks, kept.contentBlocks); assertEquals(saved.images, kept.images)
        assertEquals(5373, kept.fullContent.length); assertTrue(kept.detailReason.contains("احتُفظ"))
    }

}
