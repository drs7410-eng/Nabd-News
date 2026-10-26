package com.nabd.news

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient

class MediaPolicyTest {
    @Test fun youtubeWatchShortsAndShareResolveToSameOfficialPlayer() {
        val id="abcdefghijk"
        val values=listOf("https://www.youtube.com/watch?v=$id&t=30","https://youtu.be/$id","https://youtube.com/shorts/$id")
        assertEquals(1,values.map{MediaPolicy.target(it)}.distinct().size)
        assertTrue(MediaPolicy.target(values.first())!!.url.contains("/embed/$id"))
    }
    @Test fun rejectsCredentialsSpoofedDomainsAndUnsafeSchemes() {
        listOf("javascript:alert(1)","http://youtu.be/abcdefghijk","https://youtube.com.evil.test/watch?v=abcdefghijk","https://secret@youtube.com/watch?v=abcdefghijk").forEach { assertNull(MediaPolicy.target(it)) }
        assertFalse(MediaPolicy.validPlayer(MediaTarget("YouTube","https://evil.test/embed/abcdefghijk")))
    }
    @Test fun xPublicLinksAreCanonicalizedAndEmbedsCannotInjectHtml() {
        assertEquals("https://x.com/BBCArabic/status/123456",MediaPolicy.xLink("https://twitter.com/BBCArabic/status/123456?s=20"))
        assertNull(MediaPolicy.xLink("https://x.com/search?q=news"))
        assertNull(MediaPolicy.xLink("https://x.com/evil%22%3E%3Cscript"))
        val html=MediaPolicy.embedHtml(MediaTarget("X","https://x.com/BBCArabic"))
        assertFalse(MediaPolicy.embedHtml(MediaTarget("X","https://x.com/BBCArabic?text=%22onclick%3Devil")).contains("onclick"))
        assertTrue(html.contains("twitter-timeline"));assertTrue(html.contains("data-dnt=\"true\""));assertTrue(html.contains("https://platform.twitter.com/widgets.js"))
    }
    @Test fun supportedSocialPlayersDoNotReturnSourceWatchPages() {
        assertTrue(MediaPolicy.target("https://www.tiktok.com/@tester/video/6718335390845095173")!!.url.contains("/player/v1/"))
        assertEquals("https://player.vimeo.com/video/12345",MediaPolicy.target("https://vimeo.com/12345")!!.url)
        assertTrue(MediaPolicy.target("https://www.instagram.com/reel/ABC123/")!!.url.endsWith("/embed/"))
    }
    private fun clip(id:String,topic:String,time:Long=100) = MediaClip(id,id,"https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube","s","source",topic,time)
    @Test fun localRankingHonorsInterestsHidingAndChronologicalOptOut() {
        val tech=clip("tech","تقنية",90);val news=clip("news","أخبار",100)
        assertEquals("tech",MediaPolicy.rank(listOf(news,tech),setOf("تقنية"),"العالم","",true,100).first().id)
        assertEquals("news",MediaPolicy.rank(listOf(news,tech),emptySet(),"العالم","",false,100).first().id)
        assertEquals(1,MediaPolicy.rank(listOf(news,tech.copy(hidden=true)),emptySet(),"العالم","",true,100).size)
    }
    @Test fun youtubeAtomAndPublisherVideoEnclosuresAreRecognizedWithoutTextArticles() {
        val xml="""<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/"><entry><title>تقنية جديدة</title><link rel="alternate" href="https://www.youtube.com/watch?v=abcdefghijk"/><published>2026-10-16T10:00:00Z</published><media:group><media:thumbnail url="https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"/></media:group></entry></feed>"""
        val source=Source(id="s",name="قناة اختبار نقل",url="https://www.youtube.com/feeds/videos.xml",type="VIDEO")
        val parser=MediaRepository(dummyDao(),OkHttpClient())
        val result=parser.parse(xml.toByteArray(),source).single()
        assertEquals("YouTube",result.provider);assertEquals("تقنية",result.topic);assertTrue(result.published>0)
        val rss="""<rss><channel><item><title>مقطع من الناشر</title><link>https://publisher.test/a</link><enclosure type="video/mp4" url="https://publisher.test/a.mp4"/></item><item><title>خبر نصي</title><link>https://publisher.test/text</link></item></channel></rss>"""
        assertEquals("فيديو مباشر",parser.parse(rss.toByteArray(),source).single().provider)
    }
    private fun dummyDao() = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(NabdDao::class.java)) { _,_,_ -> throw AssertionError("Parser must not access Room") } as NabdDao
}
