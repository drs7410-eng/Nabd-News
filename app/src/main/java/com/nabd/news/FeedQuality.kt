package com.nabd.news

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A completeness heuristic, never a claim that RSS equals the entire publisher article. */
object ContentCompleteness {
    fun isShort(text: String) = text.trim().length < 1200
    fun needsPage(article: Article, force: Boolean, now: Long): Boolean =
        article.contentOrigin != "manual" && (force || (ArticleExtractors.forUrl(article.url) != null && article.extractorId != ArticleExtractors.forUrl(article.url)?.id) || article.contentOrigin != "article_page" || article.detailState != "ready" || article.detailFetchedAt <= now - 86_400_000)

}

@Entity(tableName = "feed_quality")
data class FeedQuality(@PrimaryKey val sourceId: String, val feedUrl: String, val checkedAt: Long,
    val status: String, val sampleSize: Int, val extendedCount: Int, val shortCount: Int, val excerptCount: Int,
    val imageCount: Int, val latestPublished: Long, val httpCode: Int, val error: String) {
    val contentLabel: String get() = when {
        status != "ok" -> "المحتوى: غير معروف؛ لم ينجح الفحص"
        sampleSize == 0 -> "الخلاصة فارغة؛ لا يمكن تحديد اكتمال النص"
        extendedCount == sampleSize -> "نص موسّع في حقل المحتوى؛ يلزم مقارنته بالأصل لتأكيد الاكتمال"
        excerptCount == sampleSize -> "مقتطفات فقط في العينة"
        shortCount == sampleSize -> "حقل محتوى قصير؛ اكتمال المقال غير مؤكد"
        else -> "محتوى مختلط: $extendedCount موسّع، $shortCount قصير، $excerptCount مقتطف"
    }
    companion object {
        fun assess(source: Source, articles: List<Article>, code: Int = 200, now: Long = System.currentTimeMillis()): FeedQuality {
            val sample = articles.take(50)
            return FeedQuality(source.id, source.url, now, "ok", sample.size,
                sample.count { it.contentOrigin == "rss_content" && !ContentCompleteness.isShort(it.fullContent) },
                sample.count { it.contentOrigin == "rss_content" && ContentCompleteness.isShort(it.fullContent) },
                sample.count { it.fullContent.isBlank() || it.contentOrigin == "excerpt" },
                sample.count { it.image.isNotBlank() || ContentCodec.readImages(it.images).isNotEmpty() },
                articles.map { it.published }.filter { it > 0 && it <= now + 300_000 }.maxOrNull() ?: 0,
                code, "")
        }
        fun failure(source: Source, code: Int = 0) = FeedQuality(source.id, source.url, System.currentTimeMillis(), "error", 0, 0, 0, 0, 0, 0, code,
            if (code > 0) "تعذر قراءة الخلاصة • HTTP $code" else "تعذر الاتصال أو تحليل RSS؛ تحقق من الشبكة والرابط")
    }
}
