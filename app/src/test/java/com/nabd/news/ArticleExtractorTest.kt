package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

class ArticleExtractorTest {
    private val url = "https://publisher.test/news/one"
    private val text = "هذه فقرة اختبار للتحقق من استخراج النص العربي الأصلي وترتيبه دون اختلاق أي تفاصيل إضافية. ".repeat(3)
    private val extractor = ArticleHtmlExtractor()
    private fun page(extra: String = "", body: String = "<article><p>$text</p><p>الفقرة التالية في المقال.</p></article>") = "<html><head><link rel='canonical' href='$url'/>$extra</head><body>$body</body></html>"
    @Test fun excludesNavigationAdsCommentsAndRelatedCoverage() {
        val result = extractor.extract(page(body = "<nav>navigation-secret</nav><article><p>$text</p><aside>aside-secret</aside><div class='ad-container'>advert-secret</div><div class='related-stories'><p>related-secret</p><img src='/unrelated.jpg'/></div><p>الفقرة الأخيرة</p><div id='comments'>comment-secret</div></article><footer>footer-secret</footer>"), url)
        assertEquals("ready", result.state)
        assertTrue(result.text.contains("الفقرة الأخيرة"))
        assertFalse(result.text.contains("secret")); assertFalse(result.images.any { it.url.endsWith("unrelated.jpg") })
    }
    @Test fun inlineImagesKeepTheirOriginalPositionAndCaption() {
        val result = extractor.extract(page(body = "<article><p>$text</p><h2>عنوان فرعي</h2><figure><img src='/inline.jpg'/><figcaption>وصف الصورة الأصلي</figcaption></figure><p>النهاية</p><ul><li>معلومة أولى</li><li>معلومة ثانية</li></ul></article>"), url)
        assertEquals(listOf("paragraph", "heading", "image", "paragraph", "list_item", "list_item"), result.blocks.map { it.type })
        assertEquals("وصف الصورة الأصلي", result.blocks[2].caption)
        assertEquals("https://publisher.test/inline.jpg", result.blocks[2].url)
    }
    @Test fun ogImageHasPriorityOverTwitterAndArticleJsonLdButNotFeedMedia() {
        val head = """<meta property="og:image" content="/og.jpg"/><meta name="twitter:image" content="/twitter.jpg"/><script type="application/ld+json">{"@type":"NewsArticle","url":"$url","image":"/json.jpg"}</script>"""
        val result = extractor.extract(page(head), url)
        assertEquals(listOf("og:image", "twitter:image", "json-ld"), result.images.map { it.origin })
        val feed = ArticleImage("https://publisher.test/feed.jpg", "media:content")
        assertEquals(feed.url, ImagePolicy.ordered(result.images + feed).first().url)
    }
    @Test fun organizationLogoAndOtherArticleJsonLdAreNotArticleImages() {
        val result = extractor.extract(page("""<script type="application/ld+json">{"@graph":[{"@type":"Organization","image":"/brand.jpg"},{"@type":"NewsArticle","url":"https://publisher.test/other","image":"/other.jpg"}]}</script>"""), url)
        assertTrue(result.images.isEmpty())
    }
    @Test fun nestedUnidentifiedAndDifferentIdStoriesCannotSupplyMetadataImages() {
        val head = """<script type="application/ld+json">{"@type":"WebPage","url":"$url","hasPart":[{"@type":"NewsArticle","image":"/unidentified-related.jpg"},{"@type":"NewsArticle","@id":"https://publisher.test/other#article","image":"/other-id.jpg"}]}</script>"""
        val result = extractor.extract(page(head), url)
        assertTrue(result.images.isEmpty())
    }
    @Test fun paywallIsNeverRemovedToRevealItsHiddenArticle() {
        val result = extractor.extract(page(body = "<div class='paywall'>Subscribe</div><article><p>$text</p><p hidden>locked text</p></article>"), url)
        assertEquals("restricted", result.state); assertTrue(result.text.isBlank()); assertTrue(result.images.isEmpty())
    }
    @Test fun jsonLdSubscriptionMarkerAlsoBlocksExtraction() {
        val result = extractor.extract(page("""<script type="application/ld+json">{"@type":"NewsArticle","url":"$url","isAccessibleForFree":false}</script>"""), url)
        assertEquals("restricted", result.state)
    }
    @Test fun redirectedHomeOrDifferentCanonicalCannotSupplyAnotherStoryImage() {
        val result = extractor.extract(page("<link rel='canonical' href='https://publisher.test/other'/>").replace("<link rel='canonical' href='$url'/>", ""), url)
        assertEquals("identity_mismatch", result.state); assertTrue(result.images.isEmpty())
    }
    @Test fun javascriptShellIsNotInventedIntoAnArticle() {
        val result = extractor.extract("<html><head></head><body><div id='app'></div><script src='/app.js'></script></body></html>", url)
        assertEquals("javascript_or_unavailable", result.state); assertTrue(result.text.isBlank())
    }
    @Test fun lazyAndResponsiveImageChooseLargestSafeCandidate() {
        val result = extractor.fragment("<p>نص</p><img src='data:image/gif;base64,AAAA' data-src='/actual.jpg' srcset='/small.jpg 320w, /large.jpg 1600w'/>", url)
        assertEquals("https://publisher.test/large.jpg", result.images.single().url)
    }
    @Test fun imagesInsideParagraphsAreNotMovedAfterAllText() {
        val result = extractor.fragment("<p>قبل الصورة <img src='/inside.jpg'/> بعد الصورة</p>", url)
        assertEquals(listOf("paragraph", "image", "paragraph"), result.blocks.map { it.type })
        assertEquals("قبل الصورة", result.blocks[0].text); assertEquals("بعد الصورة", result.blocks[2].text)
    }
    @Test fun unsafeAndCleartextImageUrlsAreRejected() {
        listOf("javascript:alert(1)", "data:image/png;base64,AA", "file:///private").forEach { assertNull(ImagePolicy.resolve(it, url)) }
    }
    @Test fun oldHttpImageLinksUseTheSameAssetOverVerifiedHttps() {
        assertEquals("https://publisher.test/img.jpg", ImagePolicy.resolve("http://publisher.test/img.jpg", url))
    }
    @Test fun duplicateSizedCopiesOfTheSameImageAreDeduplicated() {
        val images = listOf(ArticleImage("https://publisher.test/a.jpg?w=1200", "og:image"), ArticleImage("https://publisher.test/a.jpg?w=600", "article-body"))
        assertEquals(1, ImagePolicy.ordered(images).size)
    }
    @Test fun nativeBodyDoesNotRepeatHeroImageWithDifferentPublisherRendition() {
        val article = Article(title = "اختبار العرض", body = "", url = url, source = "مصدر",
            image = "https://static01.nyt.com/images/news/story-facebookJumbo.jpg",
            fullContent = "قبل الصورة وبعدها", contentOrigin = "article_page", contentBlocks = ContentCodec.blocks(listOf(
                ArticleBlock(text = "قبل الصورة"), ArticleBlock("image", url = "https://static01.nyt.com/images/news/story-master768.jpg"),
                ArticleBlock("image", url = "https://publisher.test/different-photo.jpg"), ArticleBlock(text = "بعد الصورة"))))
        val body = ContentCodec.displayBlocks(article)
        assertEquals(listOf("paragraph", "image", "paragraph"), body.map { it.type })
        assertEquals("https://publisher.test/different-photo.jpg", body[1].url)
    }
    @Test fun sameBbcPhotoAtDifferentSizesHasOneIdentity() {
        assertEquals(ImagePolicy.identity("https://ichef.bbci.co.uk/news/1024/cpsprodpb/one.jpg"), ImagePolicy.identity("https://ichef-1.bbci.co.uk/news/555/cpsprodpb/one.jpg"))
    }
    @Test fun relativeArticleIdentityAndHtmlBaseAreRespected() {
        val html = page("<base href='https://cdn.publisher.test/assets/'/><meta property='og:url' content='/news/one'/>", "<article><p>$text</p><p>النهاية</p><img src='photo.jpg'/></article>")
        val result = extractor.extract(html, url)
        assertEquals("ready", result.state)
        assertEquals("https://cdn.publisher.test/assets/photo.jpg", result.images.single().url)
    }
    @Test fun noImageArticleRemainsValidAndUnchanged() {
        val result = extractor.extract(page(), url)
        assertEquals("ready", result.state); assertTrue(result.images.isEmpty()); assertTrue(result.text.startsWith("هذه فقرة"))
    }
    @Test fun readabilityExtractsArabicWithoutPublisherSpecificSelectors() {
        val paragraph = "هذا نص عربي أصلي منشور في الصحيفة، يتضمن معلومات وتفاصيل وسياق الحدث دون اختلاق. ".repeat(12)
        val html = "<html><head><title>خبر عربي</title><meta property='og:type' content='article'/></head><body><nav><a href='/'>الرئيسية</a></nav><div class='layout'><div class='news-copy'><h2>خلفية القرار</h2><p>$paragraph</p><p>$paragraph</p><p>نهاية المقال الأصلية.</p></div></div><aside>خبر آخر غير مرتبط</aside></body></html>"
        val result = extractor.extract(html, url)
        assertEquals("ready", result.state); assertTrue(result.text.contains("نهاية المقال الأصلية"))
        assertFalse(result.text.contains("خبر آخر غير مرتبط")); assertFalse(result.text.contains("<p>"))
    }

}
