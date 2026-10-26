package com.nabd.news

/** Refuse silent excerpt fallback and silent truncation, including calls outside the UI. */
object AiArticleContext {
    const val MAX_UTF8_BYTES = 180_000
    fun validate(articles: List<Article>) {
        require(articles.isNotEmpty()) { "اختر خبرًا أولًا" }
        val incomplete = articles.firstOrNull { it.fullContent.isBlank() || it.isExcerpt }
        require(incomplete == null) { "لم يتوفر متن كامل للخبر: ${incomplete?.title}. افتح الخبر وأعد تحميله؛ لم يُرسل مقتطف إلى الذكاء الاصطناعي." }
        require(articles.sumOf { it.fullContent.toByteArray(Charsets.UTF_8).size.toLong() } <= MAX_UTF8_BYTES) {
            "المقالات أكبر من حد السياق المحلي الآمن. اختر أخبارًا أقل؛ لم يُحذف جزء من النص ولم يُنفّذ الطلب."
        }
    }
    fun source(a: Article) = """
<SOURCE id='${a.id}'>
المصدر: ${a.source}
حالة المحتوى: ${a.contentOrigin} • ${a.detailState}
النوع: ${a.kind}
التاريخ: ${java.time.Instant.ofEpochMilli(a.published)}
الرابط: ${a.url}
العنوان: ${a.title}
متن المقال كاملًا (${a.fullContent.length} حرف):
${a.fullContent}
</SOURCE>
"""
}
