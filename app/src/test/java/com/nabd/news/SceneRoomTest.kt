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
class SceneRoomTest {
    private lateinit var db:NabdDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()}
    @After fun close(){db.close()}
    @Test fun topicsPostsAccountsNotesAndAnalysesSurviveBackupRestore()=runBlocking {
        val topic=TopicRepository(db).create("موضوع محفوظ")
        val social=SharedPostRepository(db)
        social.saveAccount("BBCArabic","BBC","أخبار","مفضلة",true)
        social.save(SharedPostDraft("https://x.com/BBCArabic/status/123456789","BBCArabic"),"عنواني","نص أدخلته","ملاحظة خاصة",topic.id)
        val note=TopicRepository(db).note(topic.id,"تدوينة","ملاحظتي")
        db.dao().saveTopicAnalysis(TopicAnalysisLink(topic.id,"analysis"))
        db.dao().saveClaim(VerificationClaim(id="claim",topicId=topic.id,text="ادعاء"))
        db.dao().saveClaimEvidence(ClaimEvidence(claimId="claim",url="https://bbc.com/arabic"))
        val archive=BackupRepository(db).exportJson()
        db.close();db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()
        BackupRepository(db).restore(archive)
        assertTrue(db.dao().allSceneTopics().single().saved)
        assertTrue(db.dao().allSocialAccounts().single().favorite)
        assertEquals("ملاحظة خاصة",db.dao().allSocialPosts().single().personalNote)
        assertEquals(note.id,db.dao().allTopicNotes().single().noteId)
        assertEquals("analysis",db.dao().allTopicAnalyses().single().analysisId)
        assertEquals("أدلة غير كافية",db.dao().allClaims().single().status)
        assertEquals(1,db.dao().allClaimEvidence().size)
    }
    @Test fun oldBackupsImportWithoutSceneData()=runBlocking {
        val root=com.google.gson.JsonParser.parseString(BackupRepository(db).exportJson()).asJsonObject
        root.addProperty("version",5)
        listOf("sceneTopics","trendSignals","topicArticles","socialAccounts","socialPosts","topicAnalyses","claims","claimEvidence","topicNotes","topicTimeline").forEach{root.remove(it)}
        BackupRepository(db).restore(root.toString())
        assertTrue(db.dao().allSceneTopics().isEmpty())
    }
    @Test fun manualExclusionsPersistAcrossRebuildAndUpdatesAreRecorded()=runBlocking {
        val now=System.currentTimeMillis()
        val a=Article(id="a",title="السعودية تعلن إطلاق برنامج الطاقة المتجددة الوطني",body="مقتطف",url="https://a.test/a",source="أ",category="عام",published=now)
        val b=a.copy(id="b",url="https://b.test/b",source="ب")
        db.dao().addArticles(listOf(a,b));val repo=TopicRepository(db);repo.rebuild(now)
        val topic=db.dao().allSceneTopics().single()
        repo.link(topic.id,a.id,false);repo.rebuild(now+1000)
        assertEquals(-1,db.dao().allTopicArticles().first{it.articleId==a.id}.mode)
        db.dao().updateArticle(b.copy(title=b.title+" الجديد"));repo.rebuild(now+2000)
        assertEquals("source_update",db.dao().allTopicTimeline().single().kind)
    }
    @Test fun postsPersistAfterDatabaseRestartAndDeduplicateTrackingLinks()=runBlocking {
        val context:Application=RuntimeEnvironment.getApplication();val name="scene-restart.db"
        db.close();context.deleteDatabase(name);db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        SharedPostRepository(db).save(SharedPostDraft("https://x.com/BBCArabic/status/123456789?s=20","BBCArabic"),"حفظ","","","")
        SharedPostRepository(db).save(SharedPostDraft("https://twitter.com/BBCArabic/status/123456789","BBCArabic"),"تعديل","","","")
        db.close();db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        assertEquals("تعديل",db.dao().allSocialPosts().single().title)
        db.close();context.deleteDatabase(name);Unit
    }
    @Test fun conditionalRssRequestsCacheSnapshotsAndPreserveDataOnFailure()=runBlocking {
        var calls=0;var conditional=""
        val client=okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            calls++;conditional=chain.request().header("If-None-Match").orEmpty()
            if(calls>=4)throw java.io.IOException("mock network failure")
            val code=when(calls){1->200;2->304;else->503}
            val xml="<rss><channel><item><title>موضوع اختبار</title><pubDate>Thu, 15 Oct 2026 12:00:00 GMT</pubDate></item></channel></rss>"
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("test")
                .header("ETag","test-etag").body(okhttp3.ResponseBody.create(null,if(code==200)xml else "")).build()
        }.build()
        val source=GoogleTrendsDataSource(db.dao(),client)
        assertEquals(200,source.refresh("SA",true).httpCode)
        assertEquals(1,db.dao().allTrendSignals().size)
        source.refresh("SA");assertEquals(1,calls)
        val old=db.dao().allSceneTopics().single();db.dao().followSceneTopic(old.id,true)
        assertEquals(304,source.refresh("SA",true).httpCode);assertEquals("test-etag",conditional)
        val failed=source.refresh("SA",true)
        assertEquals(503,failed.httpCode);assertTrue(failed.succeeded>0);assertTrue(failed.error.isNotBlank())
        assertEquals(1,db.dao().allTrendSignals().size);assertTrue(db.dao().allSceneTopics().single().saved)
        val networkFailure=source.refresh("SA",true);assertEquals(0,networkFailure.httpCode);assertTrue(networkFailure.succeeded>0)
    }
    @Test fun removingClaimAlsoRemovesItsPrivateEvidence()=runBlocking {
        db.dao().saveClaim(VerificationClaim(id="claim",topicId="topic",text="ادعاء"))
        db.dao().saveClaimEvidence(ClaimEvidence(claimId="claim",url="https://bbc.com/arabic",description="ملاحظة دليلي"))
        db.dao().deleteClaim("claim")
        assertTrue(db.dao().allClaims().isEmpty());assertTrue(db.dao().allClaimEvidence().isEmpty())
    }
}

