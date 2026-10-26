package com.nabd.news

import org.jsoup.nodes.Document

/** Invoked only after shared identity, paywall and access checks in ArticleHtmlExtractor. */
interface SourceArticleExtractor {
    val id: String
    fun matches(url: String): Boolean
    fun extract(document: Document, url: String): ExtractedContent
}
object ArticleExtractors {
    private val specialized: List<SourceArticleExtractor> = listOf(BbcArticleExtractor())
    fun forUrl(url: String): SourceArticleExtractor? = specialized.firstOrNull { it.matches(url) }
}
