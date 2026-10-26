package com.nabd.news

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Mock HTTP integration proves code behavior, not availability or licensing of a real publisher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class WorldNetworkTest {
    private lateinit var db:NabdDatabase
    private val p=WorldPublisher("p","Publisher","صحيفة","https://publisher.test/","بلد","en",termsUrl="https://publisher.test/terms",permission="recorded",allowCache=true,allowLocal=true,allowCloud=true,allowHtml=true)
    private val feed=WorldFeed("f","p","https://publisher.test/feed",language="en")
    private val xml="""<rss xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><language>en-US</language><item><guid>stable</guid><title>Original title 2026</title><link>https://publisher.test/a?utm_medium=rss</link><description>Available RSS excerpt only.</description><pubDate>Thu, 15 Oct 2026 12:00:00 GMT</pubDate></item></channel></rss>"""
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()}
    @After fun close(){db.close()}
    private fun client(response:(Request)->Pair<Int,String>)=OkHttpClient.Builder().addInterceptor{chain->
        val (code,body)=response(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("unit-test-only").header("ETag","etag-1").header("Content-Type",if(body.trimStart().startsWith("<html"))"text/html"else"application/xml")
            .body(body.toResponseBody((if(body.trimStart().startsWith("<html"))"text/html" else "application/xml").toMediaType())).build()
    }.build()
    @Test fun verifiesRssWithoutStoringUnlicensedContentThenInstallsWithConditionalRefresh()=runBlocking {
        db.dao().saveWorldPublisher(p.copy(permission="unreviewed",allowCache=false,allowLocal=false,allowCloud=false,allowHtml=false))
        var requests=0;var conditional=""
        val repo=WorldRepository(db,client{request->requests++;conditional=request.header("If-None-Match").orEmpty();if(conditional.isNotBlank())304 to ""else 200 to xml})
        assertEquals("verified",repo.check(feed).status);assertTrue(db.dao().allArticles().isEmpty())
        db.dao().saveWorldPublisher(p)
        val installed=repo.check(feed,true)
        assertEquals("verified",installed.status);assertEquals(3,requests)
        assertEquals("https://publisher.test/a",db.dao().allArticles().single().url)
        assertEquals("feed_declared",db.dao().allWorldArticles().single().languageBasis)
        assertEquals(304,repo.check(feed,true).httpCode);assertEquals("etag-1",conditional)
        assertEquals(1,db.dao().allArticles().size)
    }
    @Test fun reusesExistingFeedAndArticleIdsWithoutLosingBookmarksOrNotes()=runBlocking {
        db.dao().saveWorldPublisher(p)
        db.dao().saveSource(Source(id="legacy-source",name="My publisher",url=feed.url))
        val existing=Article(id="kept",title="Old",body="Old excerpt",url="https://publisher.test/a",source="My publisher",sourceId="legacy-source",sourceItemId="stable",saved=true)
        db.dao().addArticles(listOf(existing));db.dao().saveNote(Note(id="note",body="private",articleIds=existing.id))
        WorldRepository(db,client{200 to xml}).check(feed,true)
        assertEquals("kept",db.dao().allArticles().single().id)
        assertEquals("legacy-source",db.dao().allArticles().single().sourceId)
        assertTrue(db.dao().allArticles().single().saved);assertEquals("kept",db.dao().allWorldArticles().single().articleId)
        assertEquals("kept",db.dao().allNotes().single().articleIds)
    }
    @Test fun refusesHtmlPosingAsRssAndLanguageMismatchAndPreservesCachedNews()=runBlocking {
        db.dao().saveWorldPublisher(p)
        val repo=WorldRepository(db,client{200 to xml});repo.check(feed,true)
        val bad=WorldRepository(db,client{200 to "<html><body>Login required</body></html>"}).check(feed,true)
        assertEquals("stale",bad.status);assertTrue(bad.error.isNotBlank());assertEquals(1,db.dao().allArticles().size)
        val mismatch=WorldRepository(db,client{200 to xml.replace("en-US","fr-FR")}).check(feed,true)
        assertTrue(mismatch.error.contains("لغة"));assertEquals(1,db.dao().allArticles().size)
    }
    @Test fun loadsPermittedArticleThroughExistingNewsRepositoryAndCachesStructuredOriginal()=runBlocking {
        db.dao().saveWorldPublisher(p)
        val paragraphs=(1..12).joinToString(""){i->"<p>Paragraph $i describes the research project and its documented results. The report includes detailed findings, dates and attributed statements from the team without advertising or recommendations.</p>"}
        val html="""<html><head><title>Original title 2026</title><link rel="canonical" href="https://publisher.test/a"><meta name="author" content="Staff Reporter"></head><body><article><h1>Original title 2026</h1>$paragraphs</article></body></html>"""
        val c=client{r->when(r.url.encodedPath){"/feed"->200 to xml;"/robots.txt"->200 to "User-agent: *\nAllow: /";else->200 to html}}
        WorldRepository(db,c).check(feed,true)
        val id=db.dao().allArticles().single().id
        NewsRepository(db.dao(),c).loadDetails(id)
        val a=db.dao().article(id)!!
        assertTrue(a.detailReason,a.fullContent.contains("Paragraph 12"));assertFalse(a.isExcerpt)
        assertTrue(ContentCodec.readBlocks(a.contentBlocks).count{it.type=="paragraph"}>=12)
        assertEquals("Staff Reporter",db.dao().worldArticle(id)!!.author)
        assertEquals("article_page",a.contentOrigin)
    }
    @Test fun accessRestrictionRemainsExplicitAndCannotBecomeAFullArticle()=runBlocking {
        db.dao().saveWorldPublisher(p)
        val c=client{r->when(r.url.encodedPath){"/feed"->200 to xml;"/robots.txt"->200 to "User-agent: *\nAllow: /";else->403 to "Forbidden"}}
        WorldRepository(db,c).check(feed,true)
        val id=db.dao().allArticles().single().id;NewsRepository(db.dao(),c).loadDetails(id)
        val a=db.dao().article(id)!!
        assertEquals("restricted",a.detailState);assertTrue(a.isExcerpt);assertTrue(a.fullContent.isBlank())
        assertTrue(a.summary.contains("excerpt"))
    }
    @Test fun userCategoryChangeIsImmediateEvenWhenFeedWouldReturnNotModified()=runBlocking {
        db.dao().saveWorldPublisher(p)
        val repo=WorldRepository(db,client{200 to xml});repo.check(feed,true)
        val a=db.dao().allArticles().single();db.dao().setSaved(a.id,true)
        repo.changeCategory(db.dao().allWorldFeeds().single(),"العلوم")
        assertEquals("العلوم",db.dao().allArticles().single().category)
        assertTrue(db.dao().allArticles().single().saved)
        assertEquals(a.id,db.dao().allWorldArticles().single().articleId)
    }
    @Test fun selectingPublisherDiscoversValidatesInstallsAndReenablesIt()=runBlocking {
        db.dao().saveWorldPublisher(p.copy(enabled=false))
        var requests=0
        val repo=WorldRepository(db,client{request->requests++
            if(request.url.encodedPath=="/")200 to "<html><head><link rel='alternate' type='application/rss+xml' href='/feed'/></head></html>"else 200 to xml
        })
        val result=repo.activate(p.id)
        assertEquals("ready",result.state);assertTrue(requests>=3)
        assertTrue(db.dao().worldPublisher(p.id)!!.enabled);assertEquals(1,db.dao().allArticles().size)
        assertEquals("https://publisher.test/feed",result.feed!!.url)
        db.dao().saveWorldPublisher(p.copy(enabled=false))
        assertEquals("ready",repo.activate(p.id).state);assertTrue(db.dao().worldPublisher(p.id)!!.enabled)
        assertEquals(1,db.dao().allArticles().size)
    }
    @Test fun selectionPreparesRssButDoesNotSilentlyInventTranslationRights()=runBlocking {
        db.dao().saveWorldPublisher(p.copy(permission="unreviewed",allowCache=false,allowLocal=false))
        db.dao().saveWorldFeed(feed)
        val repo=WorldRepository(db,client{200 to xml})
        val result=repo.activate(p.id)
        assertEquals("permission_required",result.state);assertEquals("verified",result.feed!!.status)
        assertTrue(db.dao().allArticles().isEmpty());assertFalse(db.dao().worldPublisher(p.id)!!.enabled)
        assertFalse(db.dao().worldPublisher(p.id)!!.allowLocal)
        db.dao().saveWorldPublisher(p)
        assertEquals("ready",repo.activate(p.id).state);assertEquals(1,db.dao().allArticles().size)
    }
    @Test fun selectionWithBlockedOrFakeRssNeverMarksPublisherAsReady()=runBlocking {
        db.dao().saveWorldPublisher(p);db.dao().saveWorldFeed(feed)
        val blocked=WorldRepository(db,client{403 to "Denied"}).activate(p.id)
        assertEquals("failed",blocked.state);assertFalse(db.dao().worldPublisher(p.id)!!.enabled)
        assertTrue(db.dao().allArticles().isEmpty())
        val fake=WorldRepository(db,client{200 to "<html><body>Not a feed</body></html>"}).activate(p.id)
        assertEquals("unavailable",fake.state);assertFalse(db.dao().worldPublisher(p.id)!!.enabled)
    }
}
