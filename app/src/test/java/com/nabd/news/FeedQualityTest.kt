package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

class FeedQualityTest {
    private val source = Source(id = "quality-source", name = "اختبار", url = "https://publisher.test/feed")
    private fun article(text: String = "", origin: String = "excerpt", image: String = "", date: Long = 0) =
        Article(title = "عنوان", body = "مقتطف", url = "https://publisher.test/a", source = source.name,
            fullContent = text, contentOrigin = origin, image = image, published = date)
    @Test fun descriptionNeverBecomesFullEvenWhenLong() {
        val a = article().copy(body = "وصف طويل ".repeat(300), summary = "وصف طويل ".repeat(300))
        val q = FeedQuality.assess(source, listOf(a), now = 1000)
        assertEquals(1, q.excerptCount); assertEquals(0, q.extendedCount); assertTrue(a.isExcerpt)
    }
    @Test fun shortEncodedContentDoesNotProveCompletenessOrStopPageFetch() {
        val a = article("سطر أول\n\nسطر ثان", "rss_content").copy(detailState = "ready")
        assertTrue(a.isExcerpt); assertTrue(ContentCompleteness.needsPage(a, false, 1000))
        val q = FeedQuality.assess(source, listOf(a), now = 1000)
        assertEquals(1, q.shortCount); assertEquals(0, q.extendedCount)
    }
    @Test fun longEncodedContentStillAllowsOriginalPageComparison() {
        val a = article("نص منشور أصلي ".repeat(300), "rss_content")
        assertEquals(1, FeedQuality.assess(source, listOf(a)).extendedCount)
        assertTrue(ContentCompleteness.needsPage(a, false, 1000))
    }
    @Test fun mixedFeedImageCountsAndLatestValidDateAreIndependent() {
        val q = FeedQuality.assess(source, listOf(article(), article("قصير", "rss_content", date = 800),
            article("أصلي ".repeat(400), "rss_content", "https://publisher.test/a.jpg", 900), article(date = 900000)), now = 1000)
        assertEquals(4, q.sampleSize); assertEquals(1, q.extendedCount); assertEquals(1, q.shortCount)
        assertEquals(2, q.excerptCount); assertEquals(1, q.imageCount); assertEquals(900L, q.latestPublished)
    }
    @Test fun emptyAndFailedFeedNeverPromiseFullTextOrInventDate() {
        val empty = FeedQuality.assess(source, emptyList())
        assertEquals(0L, empty.latestPublished); assertTrue(empty.contentLabel.contains("فارغة"))
        val failed = FeedQuality.failure(source, 403)
        assertEquals("error", failed.status); assertEquals(403, failed.httpCode)
        assertTrue(failed.contentLabel.contains("غير معروف"))
    }
    @Test fun pageCacheIsUsedOfflineAndForceAllowsRefresh() {
        val cached = article("نص أصلي", "article_page").copy(detailState = "ready", detailFetchedAt = 1000)
        assertFalse(ContentCompleteness.needsPage(cached, false, 2000))
        assertTrue(ContentCompleteness.needsPage(cached, true, 2000))
        assertTrue(ContentCompleteness.needsPage(cached, false, 1000 + 86_400_001))
    }
    @Test fun sourceReaderRejectsNonHttpsSchemesAndCredentials() {
        listOf("javascript:alert(1)", "intent://article", "file:///private", "http://publisher.test/a", "https://user:password@publisher.test/").forEach { assertFalse(ReaderNavigation.allowed(it)) }
        assertTrue(ReaderNavigation.allowed("https://publisher.test/article?a=1"))
    }
    @Test fun invalidHtmlFeedIsNotReportedAsEmptySuccessfulRss() {
        assertThrows(IllegalArgumentException::class.java) { FeedParser().parse("<html><body>Access denied</body></html>".toByteArray(), source) }
    }
}
