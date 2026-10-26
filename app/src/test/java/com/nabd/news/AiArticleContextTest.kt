package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

class AiArticleContextTest {
    private fun article(text: String) = Article(title = "عنوان", body = "مقتطف قديم", summary = "وصف فقط", fullContent = text,
        url = "https://example.org/story", source = "مصدر", contentOrigin = "article_page", detailState = "ready")
    @Test fun completeBodyIncludesLastParagraphBeyondOldTenThousandLimit() {
        val text = "أ".repeat(12000) + "\nآخر فقرة أصلية"
        val a = article(text)
        AiArticleContext.validate(listOf(a))
        val sent = AiArticleContext.source(a)
        assertTrue(sent.contains(text)); assertTrue(sent.contains("آخر فقرة أصلية")); assertFalse(sent.contains("مقتطف قديم"))
    }
    @Test fun missingBodyCannotFallBackToRss() {
        assertThrows(IllegalArgumentException::class.java) { AiArticleContext.validate(listOf(article(""))) }
    }
    @Test fun partialArticleCannotBeSentAsFull() {
        assertThrows(IllegalArgumentException::class.java) { AiArticleContext.validate(listOf(article("نص جزئي").copy(detailState = "partial"))) }
    }
    @Test fun oversizedContextIsRejectedRatherThanTruncated() {
        assertThrows(IllegalArgumentException::class.java) { AiArticleContext.validate(listOf(article("أ".repeat(100000)))) }
    }
}
