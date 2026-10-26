package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

class FeedParserTest {
    private val source = Source(name = "اختبار المحلل فقط", url = "https://publisher.test/feed", category = "تقنية")
    private fun rss(inner: String) = """<rss xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><item><title>عنوان عربي أصلي</title><link>https://publisher.test/news/one</link>$inner</item></channel></rss>"""
    private fun parse(xml: String) = FeedParser().parse(xml.toByteArray(), source).single()
    @Test fun summaryIsNeverClassifiedAsFullArticle() {
        val a = parse(rss("<description><![CDATA[<p>هذا وصف الخبر فقط.</p>]]></description>"))
        assertEquals("هذا وصف الخبر فقط.", a.summary)
        assertTrue(a.fullContent.isEmpty()); assertTrue(a.isExcerpt)
        assertEquals("excerpt", a.contentOrigin)
    }
    @Test fun encodedContentIsIndependentOfLongerDescription() {
        val a = parse(rss("""<description>وصف مختصر أطول من الفقرة الأولى لكنه ليس المقال الكامل.</description><content:encoded><![CDATA[<h2>عنوان فرعي</h2><p>الفقرة الأولى.</p><p>الفقرة الثانية.</p><ul><li>تفصيل مهم</li></ul>]]></content:encoded>"""))
        assertTrue(a.isExcerpt)
        assertEquals("rss_content", a.contentOrigin)
        assertFalse(a.fullContent.contains("<p>"))
        assertTrue(a.fullContent.contains("الفقرة الثانية"))
        assertEquals(listOf("heading", "paragraph", "paragraph", "list_item"), ContentCodec.readBlocks(a.contentBlocks).map { it.type })
        assertTrue(a.summary.startsWith("وصف مختصر"))
    }
    @Test fun shortPublisherContentIsPreservedButNotCertifiedAsFull() {
        val a = parse(rss("<description>نص كامل قصير</description><content:encoded><![CDATA[<p>نص كامل قصير</p>]]></content:encoded>"))
        assertTrue(a.isExcerpt)
        assertEquals("نص كامل قصير", a.summary)
        assertEquals("نص كامل قصير", a.fullContent)
        assertEquals("paragraph", ContentCodec.readBlocks(a.contentBlocks).single().type)
    }
    @Test fun imagePriorityIsMediaThenThumbnailThenEnclosureThenContent() {
        val a = parse(rss("""<media:thumbnail url="/thumb.jpg" width="640"/><enclosure url="/enclosed.jpg" type="image/jpeg"/><media:content url="/main.jpg" medium="image" width="1600"/><content:encoded><![CDATA[<p>نص المقال الأصلي</p><img src="/inline.jpg"/>]]></content:encoded>"""))
        assertEquals("https://publisher.test/main.jpg", a.image)
        assertEquals(listOf("media:content", "media:thumbnail", "enclosure", "rss-content"), ContentCodec.readImages(a.images).map { it.origin })
    }
    @Test fun videoAndLogoAndTrackingPixelAreRejected() {
        val a = parse(rss("""<media:content url="/movie.mp4" medium="video"/><media:thumbnail url="/site-logo.png"/><enclosure url="/sound.mp3" type="audio/mpeg"/><media:content url="/tiny.jpg" width="1" height="1"/><description>خبر دون صورة</description>"""))
        assertEquals("", a.image); assertTrue(ContentCodec.readImages(a.images).isEmpty())
    }
    @Test fun relativeAndProtocolRelativeImageLinksResolveAgainstArticle() {
        val a = parse(rss("""<media:content url="../images/a.jpg"/><media:thumbnail url="//cdn.publisher.test/b.jpg"/>"""))
        assertEquals("https://publisher.test/images/a.jpg", a.image)
        assertTrue(ContentCodec.readImages(a.images).any { it.url == "https://cdn.publisher.test/b.jpg" })
    }
    @Test fun atomUsesContentAndAlternativeLinkAndOriginalIdentifier() {
        val a = parse("""<a:feed xmlns:a="http://www.w3.org/2005/Atom"><a:entry><a:id>urn:article:one</a:id><a:title>عنوان عربي</a:title><a:link rel="self" href="/feed/one"/><a:link rel="alternate" href="/news/one"/><a:summary>وصف</a:summary><a:content type="html">&lt;p&gt;النص الأصلي الأول&lt;/p&gt;&lt;p&gt;النص الأصلي الثاني&lt;/p&gt;</a:content></a:entry></a:feed>""")
        assertEquals("urn:article:one", a.sourceItemId)
        assertEquals("https://publisher.test/news/one", a.url)
        assertTrue(a.fullContent.contains("الثاني"))
    }
    @Test fun atomXhtmlPreservesStructureAndImages() {
        val a = parse("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>عنوان</title><link href="/news/one"/><content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><h2>سياق الخبر</h2><p>تفاصيل منشورة</p><img src="/photo.jpg"/></div></content></entry></feed>""")
        assertEquals("https://publisher.test/photo.jpg", a.image)
        assertTrue(ContentCodec.readBlocks(a.contentBlocks).any { it.type == "heading" })
    }
    @Test fun customNamespacePrefixesPreserveShortContentAndMedia() {
        val a = parse("""<rss xmlns:m="http://search.yahoo.com/mrss/" xmlns:c="http://purl.org/rss/1.0/modules/content/"><channel><item><title>خبر</title><link>https://publisher.test/news/one</link><m:content url="/actual.jpg"/><c:encoded><![CDATA[<p>تفاصيل كاملة منشورة في حقل المحتوى</p>]]></c:encoded></item></channel></rss>""")
        assertEquals("https://publisher.test/actual.jpg", a.image); assertTrue(a.isExcerpt)
    }
    @Test fun explicitTruncationIsStillAnExcerpt() {
        val a = parse(rss("<content:encoded>هذا جزء مبتور من خبر…</content:encoded>"))
        assertTrue(a.isExcerpt); assertTrue(a.availableText.endsWith("…"))
    }
    @Test fun duplicateUrlsWithFragmentsDoNotCreateDuplicates() {
        val xml = "<rss><channel>" + listOf("#one", "#two").joinToString("") { "<item><title>خبر</title><link>https://publisher.test/a$it</link></item>" } + "</channel></rss>"
        assertEquals(1, FeedParser().parse(xml.toByteArray(), source).size)
    }
    @Test fun declaredNonUtf8FeedEncodingIsRespected() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><rss><channel><item><title>Actualité</title><link>https://publisher.test/a</link></item></channel></rss>"""
        assertEquals("Actualité", FeedParser().parse(xml.toByteArray(Charsets.ISO_8859_1), source).single().title)
    }
    @Test fun missingPublicationDateIsNotInvented() { assertEquals(0L, parse(rss("<description>خبر</description>")).published) }
    @Test fun unsafeEmbeddedElementsNeverReachNativeTextOrImages() {
        val a = parse(rss("""<content:encoded><![CDATA[<p>نص أصلي</p><script>attack()</script><iframe src="https://evil.test"></iframe><img src="javascript:attack()"/>]]></content:encoded>"""))
        assertEquals("نص أصلي", a.fullContent); assertEquals("", a.image)
    }
}
