package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs verify parsing and algorithms, never evidence of live provider access. */
class SceneEngineTest {
    private val now=1800000000000L
    private fun article(id:String,title:String,time:Long=now,url:String="https://$id.test/a")=Article(id=id,title=title,body="مقتطف",url=url,source=id,category="عام",published=time)
    @Test fun arabicNormalizationPreservesMeaningfulNumbers(){
        assertEquals("السعوديه 2030",ArabicTopicText.normalize("السُّعوديـة ٢٠٣٠"))
        assertNotEquals(ArabicTopicText.normalize("2025"),ArabicTopicText.normalize("2026"))
    }
    @Test fun clustersSameEventButKeepsDifferentPlacesAndDatesSeparate(){
        val a=article("a","السعودية تعلن إطلاق برنامج الطاقة المتجددة الوطني")
        val b=article("b","السعودية تعلن إطلاق برنامج الطاقة المتجددة الوطني الجديد")
        assertEquals(1,NewsClusteringEngine().cluster(listOf(a,b),now).size)
        val c=b.copy(id="c",url="https://c.test/a",title=b.title.replace("السعودية","روسيا"))
        assertEquals(2,NewsClusteringEngine().cluster(listOf(a,c),now).size)
        assertEquals(2,NewsClusteringEngine().cluster(listOf(a.copy(title=a.title+" 2026/10/15"),b.copy(title=b.title+" 2026/10/16")),now).size)
        assertEquals(2,NewsClusteringEngine().cluster(listOf(a,b.copy(published=now-40*3600000L)),now).size)
        assertEquals(2,NewsClusteringEngine().cluster(listOf(a.copy(title=a.title+" ٢٠٢٦/١٠/١٥"),b.copy(title=b.title+" ٢٠٢٦/١٠/١٦")),now).size)
    }
    @Test fun widelyRepeatedEventIsNotRejectedBecauseItsWordsAreCommonInCorpus(){
        val news=(1..10).map{article("publisher$it","السعودية تعلن إطلاق برنامج الطاقة المتجددة الوطني")}
        assertEquals(1,NewsClusteringEngine().cluster(news,now).size)
    }
    @Test fun removesOnlyCanonicalDuplicatesNotConflictingReports(){
        val a=article("a","قرار جديد في السعودية بشأن برنامج الطاقة المتجددة بقيمة 10 مليارات")
        val b=a.copy(id="b",url="https://b.test/a",title=a.title.replace("10","20"))
        assertEquals(2,NewsClusteringEngine().cluster(listOf(a,b),now).sumOf{it.articles.size})
        assertEquals(1,NewsClusteringEngine().cluster(listOf(a,a.copy(id="duplicate")),now).sumOf{it.articles.size})
    }
    @Test fun trendSignalsUseRealSourceCountsAndTimeWindows(){
        val s=TrendEngine.coverage("t",listOf(article("a","أ",now-3600000),article("b","ب",now-7*3600000),article("c","ج",now-3600000,"https://a.test/other")),now)
        assertEquals(2,s.sourceCount);assertEquals(2,s.recentCount);assertEquals(1,s.previousCount)
        assertEquals("يتكرر في عدة مصادر",TrendEngine.label(s))
        assertEquals("رائج في البحث",TrendEngine.label(s.copy(source="google_trends",traffic="100K+")))
    }
    @Test fun parsesRssDateTrafficReferencesAndDeduplicates(){
        val item="""<item><title>موضوع عربي</title><link>https://trends.google.com/trending?geo=SA</link><pubDate>Thu, 15 Oct 2026 12:00:00 GMT</pubDate><ht:approx_traffic>20K+</ht:approx_traffic><ht:news_item><ht:news_item_title>تغطية</ht:news_item_title><ht:news_item_url>https://bbc.com/arabic/articles/test</ht:news_item_url><ht:news_item_source>BBC</ht:news_item_source></ht:news_item></item>"""
        val (topics,signals)=GoogleTrendsDataSource.parse("<rss xmlns:ht='https://trends.google.com/trending/rss'><channel>$item$item</channel></rss>".toByteArray(),"SA",now)
        assertEquals(1,topics.size);assertEquals(1,signals.size);assertEquals("20K+",signals.single().traffic)
        assertEquals(java.time.Instant.parse("2026-10-15T12:00:00Z").toEpochMilli(),signals.single().published)
        assertTrue(signals.single().newsLinks.contains("bbc.com"))
    }
    @Test fun rejectsHtmlAndLeavesEmptyRssEmpty(){
        assertTrue(runCatching{GoogleTrendsDataSource.parse("<html>blocked</html>".toByteArray(),"SA",now)}.isFailure)
        assertTrue(GoogleTrendsDataSource.parse("<rss><channel/></rss>".toByteArray(),"SA",now).first.isEmpty())
    }
    @Test fun validatesSharedXPostsWithoutApiOrLogin(){
        assertEquals("https://x.com/BBCArabic/status/123456789",SharedPostParser.post("خبر https://twitter.com/BBCArabic/status/123456789?s=20")?.first)
        assertNull(SharedPostParser.post("https://x.com.evil.test/name/status/123"))
        assertNull(SharedPostParser.post("https://x.com/name"))
        assertNull(SharedPostParser.post("javascript:alert(1)"))
        assertEquals("BBCArabic",SharedPostParser.account("@BBCArabic"))
        assertNull(SharedPostParser.account("https://x.com/name/status/123"))
    }
    @Test fun claimsNeverAcquireTruthFromModelAlone(){
        val claims=FactCheckCoordinator.parseClaims("{\"claims\":[{\"claim\":\"ادعاء قابل للمراجعة\",\"verifiable\":true}]}","topic")
        assertEquals(1,claims.size);assertEquals("أدلة غير كافية",claims.single().status)
    }
}

