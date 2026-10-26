package com.nabd.news

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class LiveFeedAuditTest(private val id: String, private val name: String, private val url: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "live-{0}") fun sources() = listOf(
        arrayOf("bbc-ar", "BBC عربي", "https://feeds.bbci.co.uk/arabic/rss.xml"),
        arrayOf("aljazeera", "الجزيرة", "https://www.aljazeera.net/aljazeerarss/arhome"),
        arrayOf("guardian", "The Guardian", "https://www.theguardian.com/world/rss"),
        arrayOf("aawsat-ar", "الشرق الأوسط", "https://aawsat.com/feed/news"),
        arrayOf("rt-ar", "RT Arabic", "https://arabic.rt.com/rss/"),
        arrayOf("dw-ar", "DW عربية", "https://rss.dw.com/rdf/rss-ar-all"),
        arrayOf("cnn-ar", "CNN بالعربية", "https://arabic.cnn.com/api/v1/rss/rss.xml")) }
    @Test fun actualDownloadedFeedUsesProductionParserWithoutSyntheticFallback() {
        assumeTrue("Live audit is opt-in: fetch_live_feeds.py then -PliveFeeds=true", System.getProperty("nabd.liveFeeds") == "true")
        val input = File("build/live-feeds/$id.xml")
        assertTrue("Live source $id unavailable; see fetch-results.json", input.exists())
        val articles = FeedParser().parse(input.readBytes(), Source(name = name, url = url))
        val report = mapOf("source" to id, "url" to url, "mode" to "live-rss", "articleCount" to articles.size,
            "excerpts" to articles.count { it.isExcerpt }, "rssContent" to articles.count { !it.isExcerpt },
            "withImages" to articles.count { it.image.isNotBlank() }, "originalLinks" to articles.count { it.url.startsWith("http") }, "quality" to FeedQuality.assess(Source(id = id, name = name, url = url), articles))
        File("build/reports/source-audit").apply { mkdirs() }.resolve("live-$id.json").writeText(Gson().toJson(report))
        assertTrue(articles.isNotEmpty())
        assertEquals(articles.size, articles.map { it.url }.distinct().size)
        assertTrue(articles.all { !it.isExcerpt || it.fullContent.isBlank() || ContentCompleteness.isShort(it.fullContent) })
    }
}
