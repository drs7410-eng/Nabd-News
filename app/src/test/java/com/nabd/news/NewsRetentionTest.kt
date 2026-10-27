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
class NewsRetentionTest {
    private lateinit var db:NabdDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()}
    @After fun close(){db.close()}
    @Test fun expiresDisposableCacheWithoutBreakingPersonalWorkOrBodyTranslations()=runBlocking {
        val dao=db.dao()
        val ids=listOf("old","saved","note","analysis","conversation","manual","topic","translated","headline","recent","unknown","excluded")
        dao.addArticles(ids.map{Article(id=it,title=it,body="",url="https://test.example/$it",source="test",published=when(it){"recent"->900;"unknown"->0;else->100},saved=it=="saved")})
        dao.saveNote(Note(id="n",articleIds="note,other"))
        dao.saveAnalysis(Analysis(id="a",articleIds="analysis",operation="test",provider="mock",model="test",result="test",sources="",status="test",cacheKey="test"))
        dao.saveConversation(Conversation("c","conversation","test"))
        dao.saveSceneTopic(SceneTopic("topic","test",saved=true))
        dao.saveTopicArticle(TopicArticle("topic","topic"))
        dao.saveTopicArticle(TopicArticle("unsaved","manual",mode=1))
        dao.saveTopicArticle(TopicArticle("unsaved","excluded",mode=-1))
        dao.saveTopicArticle(TopicArticle("automatic","old"))
        for(id in listOf("translated","headline")){
            dao.saveWorldArticle(WorldArticle(id,"p","f","en"))
            dao.saveWorldTranslation(WorldTranslation(id,id,if(id=="headline")"title" else "block-0","en",textHash="h",glossaryHash="g",engine="mock",engineVersion="1",original="test",translated="اختبار"))
        }
        assertEquals(2,dao.pruneNewsCache(500))
        assertNull(dao.article("old"));assertNull(dao.article("headline"))
        assertNull(dao.worldArticle("headline"));assertNull(dao.worldTranslation("headline"))
        assertNotNull(dao.worldTranslation("translated"));assertNotNull(dao.article("translated"))
        for(id in ids-listOf("old","headline"))assertNotNull(id,dao.article(id))
        assertFalse(dao.allTopicArticles().any{it.articleId=="old"})
        assertEquals(0,dao.pruneNewsCache(500))
    }
}
