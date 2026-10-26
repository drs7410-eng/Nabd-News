package com.nabd.news

import com.google.gson.Gson
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Real, dated publisher snapshots from Mozilla Readability. Not a live-network or licensing test. */
@RunWith(Parameterized::class)
class ReferencePublisherTest(private val id: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "archived-{0}") fun sources() = listOf(arrayOf("bbc-1"), arrayOf("guardian-1"), arrayOf("cnn"), arrayOf("nytimes-1")) }
    @Test fun extractsOnlyTheAvailableOriginalArticleOrHonestRestriction() {
        val stream = javaClass.classLoader!!.getResourceAsStream("reference/$id/source.html")
        assumeNotNull("Run python3 scripts/fetch_reference_fixtures.py before source audit", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(html)
        val url = doc.selectFirst("link[rel=canonical]")!!.attr("href")
        val result = ArticleHtmlExtractor().extract(html, url, SourceAdapters.forUrl(url).articleSelectors)
        val report = mapOf("source" to id, "articleUrl" to url, "mode" to "archived-real-snapshot", "state" to result.state,
            "characters" to result.text.length, "paragraphs" to result.blocks.count { it.type == "paragraph" },
            "imageCount" to result.images.size, "mainImage" to result.images.firstOrNull()?.url,
            "inlineImages" to result.blocks.count { it.type == "image" })
        File("build/reports/source-audit").apply { mkdirs() }.resolve("$id.json").writeText(Gson().toJson(report))
        assertTrue("$id must give real text or an explicit access restriction, not fabricated content: ${result.state}", result.state in setOf("ready", "restricted", "blocked"))
        if (result.state == "ready") {
            assertTrue("$id article body must be substantial", result.text.length > 500)
            assertFalse(result.text.contains("<script"))
        } else assertTrue(result.text.isBlank())
        assertTrue(result.images.all { it.url.startsWith("https://") })
        val expected = javaClass.classLoader!!.getResourceAsStream("reference/$id/expected.html")!!
            .bufferedReader().use { Jsoup.parse(it.readText(), url) }
        fun key(value: String) = ImagePolicy.identity(value).substringBefore('?')
        val expectedAssets = expected.select("img").mapNotNull { ImagePolicy.resolve(it.attr("src"), url) }.map(::key).toSet()
        result.blocks.filter { it.type == "image" }.forEach { block ->
            assertTrue("$id inline image must belong to the independently curated article body: ${block.url}", key(block.url) in expectedAssets)
        }
    }
}
