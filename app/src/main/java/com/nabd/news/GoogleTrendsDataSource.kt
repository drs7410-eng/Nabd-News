package com.nabd.news

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GoogleTrendsDataSource(private val dao:NabdDao,client:OkHttpClient) {
    private val client=client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val locks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
    companion object {
        val regions=linkedMapOf("SA" to "السعودية","US" to "الولايات المتحدة","GB" to "المملكة المتحدة","AE" to "الإمارات","EG" to "مصر")
        fun url(region:String):String { require(region in regions);return "https://trends.google.com/trending/rss?geo=$region" }
        fun parse(xml:ByteArray,region:String,observed:Long):Pair<List<SceneTopic>,List<TrendSignal>> {
            require(region in regions && xml.size<=4000000)
            val doc=Jsoup.parse(xml.inputStream(),null,url(region),Parser.xmlParser())
            require(doc.selectFirst("rss > channel")!=null){"الاستجابة ليست خلاصة Google Trends RSS؛ ربما حُجب الوصول"}
            val topics=mutableListOf<SceneTopic>();val signals=mutableListOf<TrendSignal>()
            doc.getElementsByTag("item").take(200).forEach { item ->
                fun field(name:String)=item.getElementsByTag(name).firstOrNull()?.text().orEmpty()
                val title=field("title").trim().take(500)
                if(title.isBlank())return@forEach
                val published=runCatching { java.time.ZonedDateTime.parse(field("pubDate"),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)
                val topicId=AiPolicy.hash("google|$region|${ArabicTopicText.normalize(title)}")
                val link=field("link").takeIf{ReaderNavigation.allowed(it)}?:url(region)
                val references=item.getElementsByTag("ht:news_item").mapNotNull { news ->
                    val u=news.getElementsByTag("ht:news_item_url").text()
                    if(!ReaderNavigation.allowed(u))null else SignalNewsReference(news.getElementsByTag("ht:news_item_title").text().take(1000),u,news.getElementsByTag("ht:news_item_source").text().take(200))
                }.take(20)
                val traffic=field("ht:approx_traffic").take(100)
                topics+=SceneTopic(topicId,title,region=region,origin="google",created=observed,updated=observed)
                signals+=TrendSignal(AiPolicy.hash("$topicId|$published|$traffic|${observed/3600000}"),topicId,"google_trends",region,observed,published,traffic,link,Gson().toJson(references))
            }
            return topics.distinctBy{it.id} to signals.distinctBy{it.id}
        }
    }
    suspend fun refresh(region:String,force:Boolean=false):SceneFetchState = withContext(Dispatchers.IO) {
        locks.getOrPut(region){Mutex()}.withLock {
            val key="google-$region";val now=System.currentTimeMillis();val old=dao.sceneFetchState(key)
            if(!force && old!=null && old.attempted>now-15*60000L)return@withLock old
            try {
                val request=Request.Builder().url(url(region)).header("User-Agent","NabdPersonal/2.0 RSS reader").apply {
                    old?.etag?.takeIf{it.isNotBlank()}?.let{header("If-None-Match",it)}
                    old?.modified?.takeIf{it.isNotBlank()}?.let{header("If-Modified-Since",it)}
                }.build()
                client.newCall(request).execute().use { response ->
                    require(response.request.url.host=="trends.google.com"){"تحويل إلى نطاق غير متوقع"}
                    val state=when {
                        response.code==304 && old!=null -> old.copy(attempted=now,succeeded=now,httpCode=304,error="")
                        !response.isSuccessful -> (old?:SceneFetchState(key,now)).copy(attempted=now,httpCode=response.code,error="تعذر الوصول إلى Google Trends • HTTP ${response.code}. بقيت البيانات المحفوظة.")
                        else -> {
                            val bytes=response.body?.byteStream()?.use{it.readBounded(4000001)}?:error("استجابة فارغة")
                            val (topics,signals)=parse(bytes,region,now)
                            // Empty valid feeds remain empty; never synthesize a trend.
                            dao.mergeTrendSnapshot(topics,signals,now)
                            SceneFetchState(key,now,now,response.code,"",response.header("ETag").orEmpty(),response.header("Last-Modified").orEmpty())
                        }
                    }
                    dao.saveSceneFetchState(state);state
                }
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){ val state=(old?:SceneFetchState(key,now)).copy(attempted=now,httpCode=0,error="تعذر الاتصال أو قراءة RSS من Google Trends. جرّب شبكة الجهاز؛ لا توجد نتائج مصطنعة.");dao.saveSceneFetchState(state);state }
        }
    }
}

