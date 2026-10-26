package com.nabd.news

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** All positive article assertions use the actual user-supplied Galaxy response, never generated news. */
class BbcArticleExtractorTest {
    companion object {
        const val URL = "https://www.bbc.com/arabic/articles/c6ly0gdxd5jlo"
        const val TRACKED = "$URL?at_medium=RSS&at_campaign=rss"
        fun fixture() = BbcArticleExtractorTest::class.java.classLoader!!.getResourceAsStream("bbc-galaxy/c6ly0gdxd5jlo.html")!!.use { it.readBytes() }
        fun html() = fixture().toString(Charsets.UTF_8)
    }
    private val engine = ArticleHtmlExtractor()
    private fun extract(html: String = html(), url: String = TRACKED) = engine.extract(html, url)
    private fun expected() = JsonParser.parseString(javaClass.classLoader!!.getResourceAsStream("bbc-galaxy/expected-blocks.json")!!.bufferedReader().use { it.readText() }).asJsonArray
    @Test fun referenceDigestAndEveryTextAndBlockPositionMatchGalaxyEvidence() {
        assertEquals("eb863a857895d7cf1724a7c69e724242170608c94875b364f1229cc3ddf9e18a", MessageDigest.getInstance("SHA-256").digest(fixture()).joinToString("") { "%02x".format(it) })
        val result = extract()
        assertEquals("ready", result.state); assertEquals(BbcArticleExtractor.VERSION, result.extractorId)
        assertEquals(36, result.blocks.count { it.type == "paragraph" }); assertEquals(1, result.blocks.count { it.type == "heading" })
        assertEquals(2, result.images.size); assertEquals(5373, result.text.length)
        val expected = expected()
        assertEquals(expected.size(), result.blocks.size)
        expected.forEachIndexed { i, e ->
            val b = e.asJsonObject; val actual = result.blocks[i]
            assertEquals("Block $i type", b.get("type").asString, actual.type)
            if (actual.type == "image") assertEquals(ImagePolicy.identity(b.get("url").asString), ImagePolicy.identity(actual.url))
            else assertEquals("Block $i original text", b.get("text").asString, actual.text)
        }
        assertFalse(result.text.contains("البودكاست")); assertFalse(result.text.contains("مدة القراءة")); assertFalse(result.text.contains("الأكثر قراءة"))
    }
    @Test fun trackedAndBaseUrlsProduceTheSameOrderedArticle() {
        assertEquals(ArticleIdentity.canonical(URL), ArticleIdentity.canonical(TRACKED))
        assertEquals(extract(url = URL), extract(url = TRACKED))
    }
    @Test fun trackingIsBbcOnlyAndSignificantParametersRemain() {
        assertFalse(ArticleIdentity.same("https://www.cnn.com/a?at_medium=RSS", "https://www.cnn.com/a"))
        assertFalse(ArticleIdentity.same("https://bbc.com.evil.test/a?at_campaign=rss", "https://bbc.com.evil.test/a"))
        assertFalse(ArticleIdentity.same("$URL?edition=2&at_medium=RSS", URL))
        assertEquals(ArticleIdentity.canonical("$URL?edition=2"), ArticleIdentity.canonical("$URL?edition=2&at_medium=RSS&at_campaign=rss"))
    }
    @Test fun bbcRulesAreNotSelectedForOtherPublishersOrLegacyBbcPages() {
        listOf("https://arabic.cnn.com/article", "https://www.dw.com/ar/a-1", "https://aawsat.com/news/1", "https://www.bbc.com/news/world-us-canada-33646704").forEach { assertNull(ArticleExtractors.forUrl(it)) }
    }
    @Test fun missingOrInvalidNextDataIsPartialNotFakeFullArticle() {
        val doc = Jsoup.parse(html(), URL); doc.select("script#__NEXT_DATA__").remove()
        val result = extract(doc.outerHtml())
        assertEquals("partial", result.state); assertEquals(36, result.blocks.count { it.type == "paragraph" })
        doc.body().appendElement("script").attr("id", "__NEXT_DATA__").attr("type", "application/json").appendText("{broken")
        assertEquals("partial", extract(doc.outerHtml()).state)
    }
    @Test fun changedPageIdentityCannotSupplyBodyOrImages() {
        val doc = Jsoup.parse(html(), URL); val script = doc.selectFirst("script#__NEXT_DATA__")!!
        script.html(script.data().replace("/arabic/articles/c6ly0gdxd5jlo", "/arabic/articles/another"))
        val result = extract(doc.outerHtml())
        assertEquals("identity_mismatch", result.state); assertTrue(result.blocks.isEmpty()); assertTrue(result.images.isEmpty())
    }
    @Test fun missingContentBlockIsDetectedByVisibleDomComparison() {
        val doc = Jsoup.parse(html(), URL); val script = doc.selectFirst("script#__NEXT_DATA__")!!
        val json = JsonParser.parseString(script.data()).asJsonObject
        val groups = json.getAsJsonObject("props").getAsJsonObject("pageProps").getAsJsonObject("pageData").getAsJsonObject("content").getAsJsonObject("model").getAsJsonArray("blocks")
        val text = groups.first { it.asJsonObject.get("type").asString == "text" }.asJsonObject
        text.getAsJsonObject("model").getAsJsonArray("blocks").remove(0)
        script.html(json.toString())
        assertEquals("partial", extract(doc.outerHtml()).state)
    }
    @Test fun hiddenAccessWallStillStopsSpecializedExtraction() {
        val doc = Jsoup.parse(html(), URL); doc.body().prependElement("div").addClass("paywall")
        val result = extract(doc.outerHtml()); assertEquals("restricted", result.state); assertTrue(result.blocks.isEmpty())
    }
    @Test fun realInlineLinkRemainsClickableWithoutChangingOriginalText() {
        val doc = Jsoup.parse(html(), URL)
        val p = doc.selectFirst("main > div[dir=rtl] > p")!!
        val text = p.text(); val label = text.substring(0, 10)
        p.empty().appendElement("a").attr("href", "https://www.bbc.com/arabic").text(label)
        p.appendText(text.substring(10))
        val result = extract(doc.outerHtml())
        assertEquals("ready", result.state); assertEquals(5373, result.text.length)
        val paragraph = result.blocks.first { it.type == "paragraph" }
        assertEquals(label, paragraph.text.substring(paragraph.links!!.single().start, paragraph.links.single().end))
        assertEquals("https://www.bbc.com/arabic", paragraph.links.single().url)
    }
    @Test fun imageRenditionsDoNotDuplicateTheHero() {
        val result = extract(); val first = result.images.first()
        assertEquals(ImagePolicy.identity(first.url), ImagePolicy.identity("https://ichef.bbci.co.uk/news/1024/branded_arabic/1b0b/live/08df4010-c87a-11f1-9a39-31b474234113.jpg"))
        val a = Article(title = "BBC", body = "", url = TRACKED, source = "BBC", image = first.url, contentBlocks = ContentCodec.blocks(result.blocks))
        assertEquals(1, ContentCodec.displayBlocks(a).count { it.type == "image" })
    }
    @Test fun explicitManualBbcContentKeepsItsExistingReadingBehavior() {
        val manual = Article(title = "BBC", body = "", fullContent = extract().text, contentOrigin = "manual", source = "BBC", url = TRACKED)
        assertFalse(ContentCompleteness.needsPage(manual, false, System.currentTimeMillis()))
        assertEquals(ArticleReadingState.FULL_ARTICLE, articleReadingState(manual, false))
    }

}
