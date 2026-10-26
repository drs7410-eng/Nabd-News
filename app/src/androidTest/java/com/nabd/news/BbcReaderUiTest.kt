package com.nabd.news

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual reader navigation with generated Room DAO and real Galaxy HTML; HTTP is isolated from live publisher availability. */
class BbcReaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realBbcArticleOpensInTheProductionComposeReaderAndReopensFromRoom() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("bbc-galaxy/c6ly0gdxd5jlo.html").use { it.readBytes() }
        val html = bytes.toString(Charsets.UTF_8)
        val url = "https://www.bbc.com/arabic/articles/c6ly0gdxd5jlo?at_medium=RSS&at_campaign=rss"
        val extracted = ArticleHtmlExtractor().extract(html, url)
        assertEquals("ready", extracted.state)
        val title = org.jsoup.Jsoup.parse(html).selectFirst("h1")!!.text()
        val lastParagraph = extracted.blocks.last { it.type == "paragraph" }.text
        lateinit var vm: NabdViewModel
        compose.activityRule.scenario.onActivity { vm = ViewModelProvider(it)[NabdViewModel::class.java] }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val robots = chain.request().url.encodedPath == "/robots.txt"
            val type = if (robots) "text/plain" else "text/html; charset=utf-8"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Isolated real HTML transport")
                .header("Content-Type", type).body((if (robots) "User-agent: *\nAllow: /" else html).toResponseBody(type.toMediaType())).build()
        }.build()
        lateinit var id: String
        runBlocking {
            val source = vm.dao.allSources().firstOrNull { it.url == "https://feeds.bbci.co.uk/arabic/rss.xml" }
                ?: Source(id = "bbc-ar", name = "BBC عربي", url = "https://feeds.bbci.co.uk/arabic/rss.xml")
            vm.dao.updateSource(source.copy(htmlAllowed = true, termsUrl = "https://www.bbc.com/usingthebbc/terms/"))
            val old = vm.dao.articleByUrl(url)
            val article = Article(id = old?.id ?: "bbc-ui-reference", title = title, body = "", url = url, source = source.name, sourceId = source.id)
            if (old == null) vm.dao.addArticles(listOf(article)) else vm.dao.updateArticle(article)
            id = article.id
            assertEquals("ready", NewsRepository(vm.dao, client).loadDetails(id, true))
            assertEquals(36, ContentCodec.readBlocks(vm.dao.article(id)!!.contentBlocks).count { it.type == "paragraph" })
        }
        compose.waitUntil(30000) { vm.articles.value.any { it.id == id } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("متن المقال محفوظ داخل نبض"))
        compose.onNodeWithText("متن المقال محفوظ داخل نبض").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ما هي أم القنابل؟"))
        compose.onNodeWithText("ما هي أم القنابل؟").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastParagraph))
        compose.onNodeWithText(lastParagraph).assertIsDisplayed()
        compose.onNodeWithText("العودة إلى نبض").assertDoesNotExist() // No WebView dialog opened automatically.
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText(title).assertIsDisplayed() // Return to the saved feed position, without scrolling again.
        compose.onNodeWithText(title).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastParagraph))
        compose.onNodeWithText(lastParagraph).assertIsDisplayed()
    }
}
