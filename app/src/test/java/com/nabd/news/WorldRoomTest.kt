package com.nabd.news

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class WorldRoomTest {
    private lateinit var db:NabdDatabase
    private var calls=0
    private val fake=object:WorldLocalTranslator {
        override val version="UNIT_TEST_ONLY"
        override suspend fun ready(language:String)=true
        override suspend fun download(language:String,wifi:Boolean){}
        override suspend fun translate(text:String,language:String):String{calls++;delay(5);return "ترجمة اختبارية $text"}
    }
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()}
    @After fun close(){db.close()}
    private suspend fun seed():Article {
        val p=WorldPublisher("p","Publisher","صحيفة","https://publisher.test/","بلد","en",termsUrl="https://publisher.test/terms",permission="recorded",allowCache=true,allowLocal=true,allowCloud=true,allowHtml=true)
        db.dao().saveWorldPublisher(p)
        val a=Article(id="a",title="Title 2026",body="Summary",summary="Summary",url="https://publisher.test/a",source="Publisher",fullContent="Original article text",contentOrigin="article_page",saved=true)
        db.dao().addArticles(listOf(a));db.dao().saveWorldArticle(WorldArticle(a.id,p.id,"f","en"));return a
    }
    @Test fun cachesTranslationAndAvoidsDuplicateConcurrentInference()=runBlocking {
        val a=seed();val engine=WorldTranslationEngine(db.dao(),fake)
        coroutineScope {(1..5).map{async{engine.translate(a.id,"title",a.title)}}.awaitAll()}
        assertEquals(1,calls);assertEquals(1,db.dao().allWorldTranslations().size)
        assertEquals(a.title,db.dao().article(a.id)!!.title)
        assertEquals("Original article text",db.dao().article(a.id)!!.fullContent)
    }
    @Test fun glossaryAndSourceEditsCreateNewCacheVersionsWithoutDeletingOldOnes()=runBlocking {
        val a=seed();val engine=WorldTranslationEngine(db.dao(),fake)
        engine.translate(a.id,"title",a.title)
        db.dao().saveWorldGlossary(WorldGlossary(original="Title",arabic="عنوان"))
        val second=engine.translate(a.id,"title",a.title)
        assertTrue(second.translated.contains("عنوان"));assertEquals(1,calls);assertEquals(2,db.dao().allWorldTranslations().size)
        engine.translate(a.id,"title",a.title+" updated");assertEquals(3,db.dao().allWorldTranslations().size)
    }
    @Test fun failedFactGuardStoresFailureAndLeavesOriginalReadable()=runBlocking {
        val a=seed();val broken=object:WorldLocalTranslator by fake{override suspend fun translate(text:String,language:String)="نص أضاف رقم 2027"}
        assertTrue(runCatching{WorldTranslationEngine(db.dao(),broken).translate(a.id,"title",a.title)}.isFailure)
        assertEquals("failed",db.dao().allWorldTranslations().single().state)
        assertEquals(a.title,db.dao().article(a.id)!!.title)
    }
    @Test fun rightsAreCheckedBeforeTranslationAndCannotBeOverriddenByCachedInput()=runBlocking {
        val a=seed();db.dao().saveWorldPublisher(db.dao().worldPublisher("p")!!.copy(allowLocal=false))
        assertTrue(runCatching{WorldTranslationEngine(db.dao(),fake).translate(a.id,"title",a.title)}.isFailure)
        assertEquals(0,calls);assertTrue(db.dao().allWorldTranslations().isEmpty())
    }
    @Test fun translationBackupRemapsArticleIdsAndRestoresCacheAndNotebookLinks()=runBlocking {
        val a=seed();val engine=WorldTranslationEngine(db.dao(),fake);val result=engine.translate(a.id,"title",a.title)
        db.dao().saveNote(Note(id="note",title="ملاحظة",body="نصي الخاص",articleIds=a.id))
        val archive=BackupRepository(db).exportJson()
        db.close();db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()
        db.dao().addArticles(listOf(a.copy(id="existing")))
        BackupRepository(db).restore(archive)
        assertEquals("existing",db.dao().allWorldArticles().single().articleId)
        assertEquals("existing",db.dao().allNotes().single().articleIds)
        val restored=WorldTranslationEngine(db.dao(),fake).translate("existing","title",a.title)
        assertEquals(result.translated,restored.translated);assertEquals(1,calls)
    }
    @Test fun translationAndOriginalPersistAcrossDatabaseRestart()=runBlocking {
        val context:Application=RuntimeEnvironment.getApplication();val name="world-restart.db"
        db.close();context.deleteDatabase(name);db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        val a=seed();WorldTranslationEngine(db.dao(),fake).translate(a.id,"title",a.title)
        db.close();db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        val cached=WorldTranslationEngine(db.dao(),fake).translate(a.id,"title",a.title)
        assertTrue(cached.translated.isNotBlank());assertEquals(1,calls);assertTrue(db.dao().article(a.id)!!.saved)
        db.close();context.deleteDatabase(name);Unit
    }
    @Test fun olderBackupVersionHasNoWorldTablesAndStillRestoresSafely()=runBlocking {
        val root=com.google.gson.JsonParser.parseString(BackupRepository(db).exportJson()).asJsonObject
        root.addProperty("version",6);listOf("worldPublishers","worldFeeds","worldArticles","worldTranslations","worldGlossary").forEach{root.remove(it)}
        BackupRepository(db).restore(root.toString());assertTrue(db.dao().allWorldArticles().isEmpty())
    }
    @Test fun changingArticleDomainInvalidatesOnlyRelevantGlossaryTranslation()=runBlocking {
        val a=seed();val engine=WorldTranslationEngine(db.dao(),fake)
        db.dao().saveWorldGlossary(WorldGlossary(original="Title",arabic="عنوان",domain="عام"))
        db.dao().saveWorldGlossary(WorldGlossary(original="Title",arabic="عنوان علمي",domain="العلوم"))
        val first=engine.translate(a.id,"title",a.title)
        db.dao().updateArticle(a.copy(category="العلوم"))
        val second=engine.translate(a.id,"title",a.title)
        assertNotEquals(first.glossaryHash,second.glossaryHash);assertTrue(second.translated.contains("عنوان علمي"))
        assertEquals(0,calls);assertEquals(2,db.dao().allWorldTranslations().size)
    }
    @Test fun savedTranslationWorksWithoutModelOrNetwork()=runBlocking {
        val a=seed();val first=WorldTranslationEngine(db.dao(),fake).translate(a.id,"title",a.title)
        val offline=object:WorldLocalTranslator by fake {
            override suspend fun ready(language:String)=false
            override suspend fun translate(text:String,language:String):String=error("No model and no network")
        }
        val cached=WorldTranslationEngine(db.dao(),offline).translate(a.id,"title",a.title)
        assertEquals(first.id,cached.id);assertEquals(1,calls)
    }
    @Test fun cancellingInferenceNeverPublishesPartialTranslation()=runBlocking {
        val a=seed();val entered=CompletableDeferred<Unit>()
        val slow=object:WorldLocalTranslator by fake {
            override suspend fun translate(text:String,language:String):String {entered.complete(Unit);awaitCancellation()}
        }
        val job=launch{WorldTranslationEngine(db.dao(),slow).translate(a.id,"title",a.title)}
        withTimeout(10000){entered.await()};job.cancelAndJoin()
        assertEquals("cancelled",db.dao().allWorldTranslations().single().state)
        assertEquals(a.title,db.dao().article(a.id)!!.title)
    }
}
