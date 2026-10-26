package com.nabd.news

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Fake inference explicitly tests UI/cache orchestration, NOT ML Kit quality or live publisher access. */
class WorldReadingFlowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun translatesBodySwitchesOriginalAndReopensWithoutAnotherInference() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(app.packageName.endsWith(".qa")){"Use isolated -PqaBuild=true"}
        val calls=AtomicInteger();val fake=object:WorldLocalTranslator{
            override val version="QA_UI_FAKE_ONLY"
            override suspend fun ready(language:String)=true
            override suspend fun download(language:String,wifi:Boolean){}
            override suspend fun translate(text:String,language:String):String {calls.incrementAndGet();return when(text){"Quality article"->"مقال للاختبار";"Available summary"->"مقتطف متاح";"Body paragraph with"->"فقرة المتن بنسبة";else->text}}
        }
        val store=ViewModelStore();lateinit var model:NabdViewModel
        compose.runOnUiThread{model=ViewModelProvider(store,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(clazz:Class<T>):T=NabdViewModel(app,fake) as T
        })[NabdViewModel::class.java]}
        val p=WorldPublisher("qa-p","QA publisher","مصدر اختبار","https://publisher.test/","اختبار","en",permission="recorded",termsUrl="https://publisher.test/terms",allowCache=true,allowLocal=true)
        val a=Article(id="qa-ui-"+java.util.UUID.randomUUID(),title="Quality article 2026",body="Available summary",summary="Available summary",fullContent="Body paragraph with 25%.",contentOrigin="manual",detailState="ready",url="https://publisher.test/ui",source=p.name,contentBlocks=ContentCodec.blocks(listOf(ArticleBlock(text="Body paragraph with 25%."))))
        val meta=WorldArticle(a.id,p.id,"qa-feed","en")
        runBlocking{model.dao.saveWorldPublisher(p);model.dao.addArticles(listOf(a));model.dao.saveWorldArticle(meta)}
        model.settings.set("worldAutoLocal","true")
        var opened by mutableStateOf(true)
        try {
            compose.setContent {if(opened)WorldReaderScreen(model,a,meta,p,{},{},{},{})}
            compose.waitUntil(20000){compose.onAllNodesWithText("مقال للاختبار 2026").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("فقرة المتن بنسبة 25%."))
            compose.onNodeWithText("فقرة المتن بنسبة 25%.").assertIsDisplayed()
            compose.onNodeWithText("الأصل",useUnmergedTree=true).performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Body paragraph with 25%."))
            compose.onNodeWithText("Body paragraph with 25%.").assertIsDisplayed()
            compose.onNodeWithText("العربية",useUnmergedTree=true).performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("فقرة المتن بنسبة 25%."))
            compose.onNodeWithText("فقرة المتن بنسبة 25%.").assertIsDisplayed()
            val firstCalls=calls.get();assertTrue(firstCalls>0)
            compose.runOnUiThread{opened=false};compose.waitForIdle()
            compose.runOnUiThread{opened=true}
            compose.waitUntil(10000){compose.onAllNodesWithText("مقال للاختبار 2026").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("فقرة المتن بنسبة 25%."))
            compose.onNodeWithText("فقرة المتن بنسبة 25%.").assertIsDisplayed()
            assertEquals(firstCalls,calls.get())
            runBlocking{assertEquals(a.fullContent,model.dao.article(a.id)!!.fullContent);assertTrue(model.dao.allWorldTranslations().filter{it.articleId==a.id}.all{it.state=="ready"})}
        } finally {compose.runOnUiThread{opened=false};compose.waitForIdle();compose.runOnUiThread{store.clear()}}
    }
}
