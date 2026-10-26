package com.nabd.news

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.ln

object ArabicTopicText {
    private val marks=Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06EDـ]")
    private val stop=setOf("في","من","علي","عن","الي","مع","بعد","قبل","هذا","هذه","ذلك","التي","الذي","ان","انه","وان","ما","لا","بين","عاجل","اليوم","اخبار","كيف","هل","ماذا","يقول","قال","حسب","عبر","خلال","كل","اكثر","جديد","الجديد","و","او","ثم","لكن","حتي","به","بها","له","لها")
    fun normalize(value:String):String = marks.replace(value.lowercase(),"").replace(Regex("[أإآٱ]"),"ا").replace('ى','ي').replace('ة','ه')
        .map { c-> if(c in '٠'..'٩') ('0'.code+c.code-'٠'.code).toChar() else c }.joinToString("")
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
    fun tokens(value:String)=normalize(value).split(' ').filter{it.length>=3 && it !in stop}.toSet()
    val places=setOf("السعوديه","الرياض","جده","روسيا","امريكا","تركيا","اليابان","ايران","طهران","فرنسا","مصر","القاهره","الامارات","قطر","الكويت","الصين","بريطانيا","اوكرانيا","لبنان","سوريا","غزه","فلسطين","اسرائيل")
    fun saudi(text:String)=tokens(text).any{it in setOf("السعوديه","السعودي","السعوديه","الرياض","جده","مكه","الدمام","نيوم")}
    fun publisher(url:String):String {
        val h=url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: return "unknown"
        if(ArticleIdentity.isBbcHost(h) || h.endsWith("bbci.co.uk")) return "bbc"
        return h.removePrefix("rss.").removePrefix("feeds.")
    }
}
data class NewsCluster(val articles:List<Article>)
class NewsClusteringEngine {
    /** Conservative headline clustering, 36-hour window and place/date guards. No AI calls. */
    fun cluster(input:List<Article>,now:Long=System.currentTimeMillis()):List<NewsCluster> {
        val articles=input.filter { it.published>0 && it.published<=now+300000 && it.published>=now-7*86400000L }
            .distinctBy{ArticleIdentity.canonical(it.url)}.sortedWith(compareByDescending<Article>{it.published}.thenBy{it.id}).take(1000)
        val terms=articles.associate{it.id to ArabicTopicText.tokens(it.title)}
        val frequency=terms.values.flatMap{it}.groupingBy{it}.eachCount()
        fun similar(a:Article,b:Article):Boolean {
            if(kotlin.math.abs(a.published-b.published)>36*3600000L) return false
            val x=terms[a.id].orEmpty();val y=terms[b.id].orEmpty();val common=x intersect y
            if(common.size<3) return false
            val px=x intersect ArabicTopicText.places;val py=y intersect ArabicTopicText.places
            if(px.isNotEmpty() && py.isNotEmpty() && (px intersect py).isEmpty()) return false
            val dates=Regex("\\d{1,4}[/.-]\\d{1,2}(?:[/.-]\\d{1,4})?")
            fun dateText(value:String)=value.map{c->if(c in '٠'..'٩')('0'.code+c.code-'٠'.code).toChar() else c}.joinToString("")
            val da=dates.findAll(dateText(a.title)).map{it.value}.toSet();val db=dates.findAll(dateText(b.title)).map{it.value}.toSet()
            if(da.isNotEmpty() && db.isNotEmpty() && (da intersect db).isEmpty()) return false
            fun weight(t:String)=1.0+ln((articles.size+1.0)/((frequency[t]?:0)+1.0))
            val score=common.sumOf(::weight)/(x union y).sumOf(::weight).coerceAtLeast(1.0)
            val anchors=common.count{it.length>=4 && (frequency[it]?:0)<=maxOf(2,articles.size/3)}
            return score>=0.50 && (anchors>=2 || (score>=0.72 && common.size>=5))
        }
        val groups=mutableListOf<MutableList<Article>>()
        articles.forEach { a ->
            val group=groups.firstOrNull{g->g.take(3).all{similar(a,it)}}
            if(group==null)groups+=mutableListOf(a) else group+=a
        }
        return groups.map{NewsCluster(it.toList())}
    }
}
object TrendEngine {
    const val METHOD="إشارات منفصلة: رائج في البحث من Google، وتغطية محلية بعدد نطاقات النشر والأخبار. التسارع يقارن آخر 6 ساعات بالـ6 السابقة، ولا يحسب مشاهدات أو نموًا عالميًا. اختلاف النطاق لا يثبت الاستقلال التحريري. التجميع آلي تقريبي ويمكن تصحيحه."
    fun coverage(topicId:String,articles:List<Article>,now:Long):TrendSignal {
        val known=articles.distinctBy{ArticleIdentity.canonical(it.url)}.filter{it.published in 1..now+300000}
        val recent=known.count{it.published>=now-6*3600000L}
        val previous=known.count{it.published in (now-12*3600000L) until (now-6*3600000L)}
        return TrendSignal(AiPolicy.hash("local|$topicId|${now/3600000}"),topicId,"local_coverage","LOCAL",now,known.maxOfOrNull{it.published}?:0,
            newsCount=known.size,sourceCount=known.map{ArabicTopicText.publisher(it.url)}.distinct().size,recentCount=recent,previousCount=previous)
    }
    fun label(s:TrendSignal)=when {
        s.source=="google_trends" -> "رائج في البحث"
        s.previousCount>0 && s.recentCount>=3 && s.recentCount>s.previousCount -> "متسارع التغطية المحلية"
        s.sourceCount>=3 -> "واسع التغطية في مصادرك"
        s.sourceCount>=2 -> "يتكرر في عدة مصادر"
        else -> "تغطية محلية محدودة"
    }
}

