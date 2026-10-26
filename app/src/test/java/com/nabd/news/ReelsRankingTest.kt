package com.nabd.news
import org.junit.Assert.*
import org.junit.Test
class ReelsRankingTest {
    private fun clip(id:String,source:String="s",topic:String="تقنية")=MediaClip(id,id,"https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube",source,source,topic,100)
    @Test fun repeatedSourceIsSeparatedWhenAnAlternativeExists() {
        val result=MediaPolicy.rank(listOf(clip("a"),clip("b"),clip("c","other")),setOf("تقنية"),"العالم","",true,100)
        assertEquals(listOf("s","other","s"),result.map{it.sourceId})
    }
    @Test fun skipsReduceRepeatedExposureWithoutErasingExplicitPreferences() {
        val skipped=clip("a").copy(skips=4);val fresh=clip("b")
        assertEquals("b",MediaPolicy.rank(listOf(skipped,fresh),setOf("تقنية"),"العالم","",true,100).first().id)
    }
    @Test fun sixthSlotExploresAnUnseenTopicFromAnotherSource() {
        val preferred=(1..7).map{clip("a$it","s$it")}
        val discovery=clip("z","discovery","أخبار")
        val result=MediaPolicy.rank(preferred+discovery,setOf("تقنية"),"العالم","",true,100)
        assertEquals("z",result[5].id);assertEquals(8,result.map{it.id}.distinct().size)
    }
    @Test fun favoriteIsIndependentOfRecommendationFeedback() {
        val a=clip("a");val b=clip("b")
        val plain=MediaPolicy.rank(listOf(a,b),emptySet(),"العالم","",true,100).map{it.id}
        assertEquals(plain,MediaPolicy.rank(listOf(a,b.copy(favorite=true)),emptySet(),"العالم","",true,100).map{it.id})
    }
}
