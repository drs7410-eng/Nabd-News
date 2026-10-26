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

/** FakeProvider only. No real keys, paid requests, or claims of editorial language quality. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class WorldCloudTest {
    private lateinit var db:NabdDatabase
    private lateinit var settings:SecureSettings
    private lateinit var ai:AiRepository
    private var calls=0;private var last:AiRequest?=null
    private val local=object:WorldLocalTranslator{
        override val version="TEST_LOCAL";override suspend fun ready(language:String)=true
        override suspend fun download(language:String,wifi:Boolean){}
        override suspend fun translate(text:String,language:String)="ترجمة محلية اختبارية $text"
    }
    @Before fun setup() {
        val context:Application=RuntimeEnvironment.getApplication();db=Room.inMemoryDatabaseBuilder(context,NabdDatabase::class.java).build();settings=SecureSettings(context)
        context.getSharedPreferences("nabd_vault",0).edit().putString("OpenAI","FAKE_TEST_MARKER").commit()
        settings.set("model_OpenAI","mock-world");settings.set("enabled_OpenAI","true");settings.set("daily","0");settings.set("monthly","0")
        val fake=object:AiProvider {
            override val id="OpenAI";override suspend fun models()=listOf("mock-world")
            override suspend fun generate(request:AiRequest):AiResult {calls++;last=request
                val part=request.input.substringAfter("<SOURCE").substringAfter('>').substringBefore("</SOURCE>")
                return AiResult(com.google.gson.Gson().toJson(mapOf("text" to "ترجمة اختبارية $part")),inputTokens=100,outputTokens=50)
            }
        }
        ai=AiRepository(db.dao(),settings,mapOf("OpenAI" to fake))
    }
    @After fun close(){db.close()}
    private suspend fun seed() {
        db.dao().saveWorldPublisher(WorldPublisher("p","Publisher","صحيفة","https://publisher.test/","country","en",termsUrl="https://publisher.test/terms",permission="recorded",allowCache=true,allowCloud=true,allowLocal=true))
        db.dao().addArticles(listOf(Article(id="a",title="Title 2026",body="Excerpt",url="https://publisher.test/a",source="Publisher",fullContent="Full original",contentOrigin="article_page")))
        db.dao().saveWorldArticle(WorldArticle("a","p","f","en"))
        db.dao().saveNote(Note(body="PRIVATE_NOTE_MUST_NOT_BE_SENT",articleIds="a"))
    }
    @Test fun cloudIsExplicitUsesExistingAiBudgetLayerAndSavesSeparateTranslationAndAnalysis()=runBlocking {
        seed();val engine=WorldTranslationEngine(db.dao(),local,ai,settings)
        engine.translate("a","title","Title 2026");assertEquals(0,calls)
        val translated=engine.translate("a","title","Title 2026","OpenAI")
        assertEquals(1,calls);assertEquals(EDITORIAL_TRANSLATION_INSTRUCTIONS,last!!.instructions)
        assertFalse(last!!.input.contains("PRIVATE_NOTE_MUST_NOT_BE_SENT"))
        assertEquals("Title 2026",db.dao().article("a")!!.title)
        assertEquals("a",db.dao().allAnalyses().single().articleIds)
        assertEquals("ترجمة تحريرية",db.dao().allAnalyses().single().operation)
        assertEquals(translated.id,engine.translate("a","title","Title 2026","OpenAI").id);assertEquals(1,calls)
    }
    @Test fun cloudRightsAndMissingKeyStopRequestsWithoutTouchingArticle()=runBlocking {
        seed();val engine=WorldTranslationEngine(db.dao(),local,ai,settings)
        db.dao().saveWorldPublisher(db.dao().worldPublisher("p")!!.copy(allowCloud=false))
        assertTrue(runCatching{engine.translate("a","title","Title 2026","OpenAI")}.isFailure)
        assertEquals(0,calls)
        db.dao().saveWorldPublisher(db.dao().worldPublisher("p")!!.copy(allowCloud=true));settings.deleteKey("OpenAI")
        assertTrue(runCatching{engine.translate("a","title","Title 2026","OpenAI")}.isFailure)
        assertEquals(0,calls);assertEquals("Full original",db.dao().article("a")!!.fullContent)
    }
}
