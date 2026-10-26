package com.nabd.news

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class SceneAiIntegrationTest {
    private lateinit var db:NabdDatabase
    private lateinit var settings:SecureSettings
    private lateinit var service:TopicAnalysisService
    private var calls=0
    private var input=""
    @Before fun setup(){
        val context:Application=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(context,NabdDatabase::class.java).build()
        settings=SecureSettings(context)
        // Test marker only: FakeProvider never reads or decrypts a credential, and never uses network.
        context.getSharedPreferences("nabd_vault",0).edit().putString("OpenAI","FAKE_TEST_MARKER").commit()
        settings.set("model_OpenAI","mock-model");settings.set("enabled_OpenAI","true")
        settings.set("daily","0");settings.set("monthly","0")
        val mock=object:AiProvider {
            override val id="OpenAI"
            override suspend fun models()=listOf("mock-model")
            override suspend fun generate(request:AiRequest):AiResult {calls++;input=request.input;return AiResult("نتيجة اختبار برمجي",inputTokens=100,outputTokens=20)}
        }
        service=TopicAnalysisService(db,AiRepository(db.dao(),settings,mapOf("OpenAI" to mock)),NanoEngine(context))
    }
    @After fun close(){db.close()}
    private suspend fun seed():String {
        val topic=TopicRepository(db).create("موضوع اختبار","PRIVATE_DESCRIPTION")
        val article=Article(id="news",title="عنوان المصدر",body="EXCERPT_ONLY",url="https://bbc.com/arabic/articles/test",source="BBC",category="عام",published=100,fullContent="FULL_ARTICLE_TEXT_NOT_EXCERPT",contentOrigin="article_page",detailState="FULL_ARTICLE")
        db.dao().addArticles(listOf(article));TopicRepository(db).link(topic.id,article.id,true)
        TopicRepository(db).note(topic.id,"ملاحظة","PRIVATE_NOTE_BODY")
        SharedPostRepository(db).save(SharedPostDraft("https://x.com/BBCArabic/status/123456789","BBCArabic"),"عنوان شخصي","MANUAL_POST_TEXT","PRIVATE_POST_NOTE",topic.id)
        return topic.id
    }
    @Test fun fullArticleIsSentWhilePersonalDataAndPostTextRequireExplicitSelection()=runBlocking {
        val id=seed();val result=service.run(id,"تحليل موضوع","OpenAI","",false,false,false)
        assertTrue(input.contains("FULL_ARTICLE_TEXT_NOT_EXCERPT"));assertFalse(input.contains("EXCERPT_ONLY"))
        listOf("PRIVATE_DESCRIPTION","PRIVATE_NOTE_BODY","PRIVATE_POST_NOTE","MANUAL_POST_TEXT").forEach{assertFalse(it,input.contains(it))}
        assertEquals(result.id,db.dao().allTopicAnalyses().single().analysisId)
        assertEquals(result.id,service.run(id,"تحليل موضوع","OpenAI","",false,false,false).id)
        assertEquals(1,calls)
        service.run(id,"تحليل موضوع","OpenAI","",true,true,false)
        assertTrue(input.contains("MANUAL_POST_TEXT"));assertTrue(input.contains("PRIVATE_NOTE_BODY"));assertFalse(input.contains("PRIVATE_POST_NOTE"))
    }
    @Test fun unavailableProviderDoesNotInventAResultOrBreakLocalTopics()=runBlocking {
        val id=seed();settings.deleteKey("OpenAI")
        assertTrue(runCatching{service.run(id,"تحليل موضوع","OpenAI","",false,false,false)}.isFailure)
        assertEquals(0,calls);assertTrue(db.dao().allAnalyses().isEmpty());assertNotNull(db.dao().sceneTopic(id))
    }
    @Test fun topicChatIsSavedWithSeparateOwnerAndRestoredContext()=runBlocking {
        val id=seed();service.run(id,"سؤال مخصص","OpenAI","سؤال أول",false,false,false,true)
        service.run(id,"سؤال مخصص","OpenAI","سؤال ثان",false,false,false,true)
        assertEquals(4,db.dao().allMessages().size)
        assertEquals("topic:$id",db.dao().allConversations().single().articleId)
        assertEquals(2,db.dao().allTopicAnalyses().size)
    }
}

