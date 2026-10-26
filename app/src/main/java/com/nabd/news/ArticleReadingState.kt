package com.nabd.news

enum class ArticleReadingState { FETCHING_ARTICLE, FULL_ARTICLE, PARTIAL_ARTICLE, EXTRACTION_FAILED, ACCESS_RESTRICTED }
fun articleReadingState(article: Article, loading: Boolean): ArticleReadingState = when {
    loading -> ArticleReadingState.FETCHING_ARTICLE
    article.detailState in setOf("permission_required", "robots_denied", "restricted", "blocked") -> ArticleReadingState.ACCESS_RESTRICTED
    article.detailState == "partial" -> ArticleReadingState.PARTIAL_ARTICLE
    article.detailState in setOf("identity_mismatch", "bbc_structure_changed", "unavailable", "javascript_or_unavailable") -> ArticleReadingState.EXTRACTION_FAILED
    article.fullContent.isNotBlank() && !article.isExcerpt &&
        (article.contentOrigin == "manual" || ArticleExtractors.forUrl(article.url) == null || article.extractorId == ArticleExtractors.forUrl(article.url)?.id) -> ArticleReadingState.FULL_ARTICLE
    else -> ArticleReadingState.PARTIAL_ARTICLE
}
