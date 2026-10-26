package com.nabd.news

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Isolated test records only; no network/AI/news placeholders are shipped to the user feed. */
class ArticleUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun excerptAndPublisherContentHaveDistinctPresentationAndOriginalLinks() {
        lateinit var vm: NabdViewModel
        compose.activityRule.scenario.onActivity { vm = ViewModelProvider(it)[NabdViewModel::class.java] }
        val excerpt = Article(id = "ui-excerpt", title = "اختبار واجهة المقتطف", body = "الوصف المتاح فقط", summary = "الوصف المتاح فقط", url = "https://publisher.test/excerpt", source = "اختبار واجهة محلي", contentOrigin = "excerpt")
        vm.settings.set("autoOriginalFallback", "true") // Legacy preference must never open a website automatically.
        val full = Article(id = "ui-full", title = "اختبار واجهة المحتوى الأصلي", body = "وصف", summary = "وصف", url = "https://publisher.test/full", source = "اختبار واجهة محلي",
            fullContent = "فقرة أولى أصلية\n\nفقرة ثانية أصلية", contentOrigin = "article_page", detailState = "ready", detailFetchedAt = System.currentTimeMillis(), contentBlocks = ContentCodec.blocks(listOf(ArticleBlock(text = "فقرة أولى أصلية"), ArticleBlock("heading", "عنوان فرعي أصلي"), ArticleBlock(text = "فقرة ثانية أصلية"))))
        runBlocking { vm.dao.addArticles(listOf(excerpt, full)) }
        compose.waitUntil(30000) { vm.articles.value.any { it.id == excerpt.id } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(excerpt.title))
        compose.onNodeWithText(excerpt.title).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("هذا مقتطف من الخبر. يمكنك قراءة التفاصيل الكاملة من المصدر الأصلي"))
        compose.onNodeWithText("هذا مقتطف من الخبر. يمكنك قراءة التفاصيل الكاملة من المصدر الأصلي").assertIsDisplayed()
        compose.waitUntil(30000) { vm.articles.value.firstOrNull { it.id == excerpt.id }?.detailState == "permission_required" }
        compose.onNodeWithText("فتح صفحة المصدر يدويًا").assertExists()
        compose.onNodeWithText("العودة إلى نبض").assertDoesNotExist()
        compose.onNodeWithContentDescription("حفظ الخبر").performClick()
        compose.waitUntil(30000) { vm.articles.value.firstOrNull { it.id == excerpt.id }?.saved == true }
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(full.title))
        compose.onNodeWithText(full.title).performClick()
        compose.onNodeWithText("هذا مقتطف من الخبر. يمكنك قراءة التفاصيل الكاملة من المصدر الأصلي").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("عنوان فرعي أصلي"))
        compose.onNodeWithText("عنوان فرعي أصلي").assertExists()
        compose.onNodeWithText("فقرة ثانية أصلية").assertExists()
    }
}
