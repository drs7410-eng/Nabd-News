package com.nabd.news

import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object WorldUrls {
    fun valid(value:String):Boolean {
        val u=value.toHttpUrlOrNull()?:return false
        val h=u.host.lowercase()
        return u.isHttps && u.username.isBlank() && u.password.isBlank() && h.contains('.') && !h.contains(':') &&
            h!="localhost" && !h.endsWith(".local") && !h.endsWith(".localhost") && !h.matches(Regex("[0-9.]+")) &&
            u.queryParameterNames.none{it.lowercase() in setOf("token","api_key","apikey","access_token","password","key")}
    }
    fun samePublisher(a:String,b:String):Boolean {
        val x=a.toHttpUrlOrNull();val y=b.toHttpUrlOrNull()
        return PublisherScope.samePublisher(a,b) || (x?.topPrivateDomain()!=null && x.topPrivateDomain()==y?.topPrivateDomain())
    }
    fun discovered(html:String,base:String):List<String> {
        val doc=Jsoup.parse(html,base)
        return doc.select("link[rel~=alternate][href]").filter{it.attr("type").lowercase() in setOf("application/rss+xml","application/atom+xml","application/rdf+xml")}
            .map{it.absUrl("href")}.filter{valid(it)&&samePublisher(base,it)}.distinct().take(8)
    }
}
object WorldMetadata {
    fun language(xml:ByteArray):String {
        val doc=Jsoup.parse(xml.inputStream(),null,"",Parser.xmlParser())
        val raw=doc.selectFirst("channel > language")?.text().orEmpty().ifBlank{doc.children().firstOrNull()?.attr("xml:lang").orEmpty()}
        return raw.lowercase().substringBefore('-').substringBefore('_').take(3)
    }
    fun authors(xml:ByteArray,base:String):Map<String,String> {
        val doc=Jsoup.parse(xml.inputStream(),null,base,Parser.xmlParser())
        return doc.getAllElements().filter{it.tagName().substringAfter(':') in setOf("item","entry")}.associate { e->
            val link=e.children().firstOrNull{it.tagName()=="link" && it.attr("rel").ifBlank{"alternate"}=="alternate"}?.let{it.attr("href").ifBlank{it.text()}}.orEmpty()
            val url=base.toHttpUrlOrNull()?.resolve(link)?.toString().orEmpty()
            val author=e.children().firstOrNull{it.tagName().substringAfter(':') in setOf("creator","author")}?.text().orEmpty().let{Jsoup.parseBodyFragment(it).text()}.take(500)
            ArticleIdentity.canonical(url) to author
        }
    }
    fun author(html:String)=Jsoup.parse(html).selectFirst("meta[name=author][content]")?.attr("content").orEmpty().let{Jsoup.parseBodyFragment(it).text()}.take(500)
}
class WorldRepository(private val db:NabdDatabase,client:OkHttpClient) {
    private val dao=db.dao();private val locks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
    private val http=NewsHttp(client.newBuilder().cache(null).followRedirects(false).followSslRedirects(false).callTimeout(35,java.util.concurrent.TimeUnit.SECONDS).build())
    data class SetupResult(val state:String,val message:String,val feed:WorldFeed?=null)
    private val setupLocks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
    /** Selection owns discovery, validation and installation; it never invents publisher permission. */
    suspend fun activate(publisherId:String):SetupResult=setupLocks.getOrPut(publisherId){Mutex()}.withLock {
        val p=dao.worldPublisher(publisherId)?:return@withLock SetupResult("failed","الصحيفة غير موجودة")
        var selected:WorldFeed?=null
        val existing=dao.allWorldFeeds().filter{it.publisherId==p.id}.sortedByDescending{it.succeeded}
        for(feed in existing){currentCoroutineContext().ensureActive();val checked=check(feed,false);if(checked.status=="verified"){selected=checked;break}}
        if(selected==null)try{selected=discover(p).firstOrNull()}catch(e:CancellationException){throw e}catch(e:Exception){
            return@withLock SetupResult("failed",existing.firstOrNull()?.let{dao.allWorldFeeds().firstOrNull{f->f.id==it.id}?.error}?.takeIf{it.isNotBlank()}?:safeError(e))
        }
        val feed=selected?:return@withLock SetupResult("unavailable","لم نعثر على RSS عام صالح لهذه الصحيفة؛ لم يُفعّل رابط غير مختبر")
        if(!p.allowCache || p.termsUrl.isBlank() || (!WorldRights.knownRestricted(p.website) && p.permission!="restricted" && (!p.allowLocal || p.permission!="recorded")))return@withLock SetupResult("permission_required","اكتمل إعداد RSS واللغة والقسم. يلزم استكمال إذن الحفظ والترجمة مرة واحدة؛ لا يمنح وجود الخلاصة إذنًا تلقائيًا.",feed)
        val installed=check(feed,true)
        if(installed.status!="verified")return@withLock SetupResult("failed",installed.error,installed)
        dao.saveWorldPublisher((dao.worldPublisher(p.id)?:p).copy(enabled=true))
        SetupResult("ready",if(WorldRights.knownRestricted(p.website)||!p.allowLocal)"فُعّلت الخلاصة؛ الترجمة غير مفعّلة لهذا المصدر وفق الأذونات المسجلة"else"اكتمل إعداد الصحيفة وجلب الأخبار؛ الترجمة المحلية تبدأ عند جاهزية نموذج ${p.language}",installed)
    }
    suspend fun seed() { db.withTransaction {
        val existing=dao.allWorldPublishers().map{it.id}.toSet()
        WorldCatalog.entries.forEach { e->if(e.publisher.id !in existing) {
            dao.saveWorldPublisher(e.publisher)
            if(e.feed.isNotBlank())dao.saveWorldFeed(WorldFeed(AiPolicy.hash("world-feed|${e.feed}"),e.publisher.id,e.feed,language=e.publisher.language))
        } }
    } }
    suspend fun permissions(p:WorldPublisher) {
        val prior=dao.worldPublisher(p.id)?:error("الصحيفة غير موجودة")
        require((prior.permission!="restricted" && !WorldRights.knownRestricted(p.website)) || p==prior){"لهذا المصدر قيود معالجة موثقة؛ لا يمكن تجاوزها بمفتاح إعداد عام"}
        require(!p.allowCache || WorldUrls.valid(p.termsUrl) && p.permission=="recorded"){"سجل رابط الإذن وشروط المعالجة أولًا"}
        require(!p.allowImages || !WorldRights.needsAuthor(p.website)){"لا يمكن ترخيص صور Global Voices المختلطة بإذن نص عام؛ تبقى الصور مستبعدة وتتاح من الأصل"}
        require(p.allowCache || !(p.allowLocal||p.allowCloud||p.allowHtml||p.allowImages)){"الحفظ المسموح مطلوب قبل تفعيل المعالجة"}
        db.withTransaction {dao.saveWorldPublisher(p);dao.allWorldFeeds().filter{it.publisherId==p.id}.forEach{dao.worldSourceAccess(it.id,p.allowHtml,p.termsUrl)}}
    }
    suspend fun changeCategory(feed:WorldFeed,category:String) {
        require(category in worldCategories)
        db.withTransaction {
            dao.saveWorldFeed(feed.copy(category=category))
            val ids=dao.allWorldArticles().filter{it.feedId==feed.id}.map{it.articleId}.toSet()
            dao.allArticles().filter{it.id in ids}.forEach{a->dao.updateArticle(a.copy(category=category,kind=if(a.kind.startsWith("مادة صحفية")||a.kind.startsWith("رأي / تحليل — تصنيف"))if(category=="الرأي والتحليل")"رأي / تحليل — تصنيف الخلاصة"else"مادة صحفية — نوعها غير محدد"else a.kind))}
            dao.worldPublisher(feed.publisherId)?.let{p->dao.saveWorldPublisher(p.copy(sections=com.google.gson.Gson().toJson(dao.allWorldFeeds().filter{it.publisherId==p.id&&it.succeeded>0}.map{it.category}.distinct())))}
        }
    }
    suspend fun discover(p:WorldPublisher):List<WorldFeed> = withContext(Dispatchers.IO) {
        require(WorldUrls.valid(p.website)){"أدخل موقع HTTPS عامًا دون بيانات اعتماد"}
        val page=http.get(p.website)
        require(page.code in 200..299){"تعذر فحص الموقع • HTTP ${page.code}"}
        val declared=WorldUrls.discovered(page.body,page.url)
        val candidates=if(declared.isNotEmpty())declared else listOf("feed/","rss","rss.xml","atom.xml").mapNotNull{p.website.toHttpUrlOrNull()?.resolve("/$it")?.toString()}
        candidates.take(6).mapNotNull { url ->
            val feed=WorldFeed(AiPolicy.hash("world-feed|$url"),p.id,url,language=p.language)
            val result=check(feed,false,saveFailure=false)
            result.takeIf{it.status=="verified"}
        }
    }
    suspend fun check(feed:WorldFeed,install:Boolean=false,force:Boolean=true,saveFailure:Boolean=true):WorldFeed = withContext(Dispatchers.IO) {
        locks.getOrPut(feed.id){Mutex()}.withLock {
            val p=dao.worldPublisher(feed.publisherId)?:error("الصحيفة غير موجودة")
            require(WorldUrls.valid(feed.url) && WorldUrls.samePublisher(p.website,feed.url)){"نطاق الخلاصة لا يطابق الناشر"}
            if(install)require(p.allowCache && p.termsUrl.isNotBlank()){ "راجع حقوق الحفظ والمعالجة قبل تفعيل المصدر" }
            val old=dao.allWorldFeeds().firstOrNull{it.id==feed.id}?:feed
            val now=System.currentTimeMillis()
            if(!force && old.attempted>now-15*60000L)return@withLock old
            try {
                val headers=buildMap{if(old.etag.isNotBlank())put("If-None-Match",old.etag);if(old.modified.isNotBlank())put("If-Modified-Since",old.modified)}
                val doc=http.get(feed.url,conditional=headers)
                if(doc.code==304 && old.succeeded>0) {
                    // If the earlier check was metadata-only, a 304 cannot install articles that were never stored.
                    if(install && dao.allWorldArticles().none{it.feedId==feed.id})return@withLock fetchWithoutConditions(feed,p,now,install)
                    val state=old.copy(attempted=now,succeeded=now,httpCode=304,error="",status="verified");dao.saveWorldFeed(state);return@withLock state
                }
                require(doc.code in 200..299){"HTTP ${doc.code}: لم تصل خلاصة عامة قابلة للقراءة"}
                parseAndSave(feed,p,doc,now,install)
            } catch(e:CancellationException){throw e}
            catch(e:Exception){val code=(e.message?.let{Regex("HTTP (\\d{3})").find(it)?.groupValues?.get(1)?.toIntOrNull()})?:0
                val failed=old.copy(attempted=now,httpCode=code,status=if(old.succeeded>0)"stale" else "failed",error=if(code==0&&e is IllegalArgumentException)e.message.orEmpty() else if(code>0)"تعذر فحص RSS • HTTP $code؛ لم يُعتمد مصدر جديد" else "تعذر الاتصال أو تحليل RSS. ربما تمنع شبكة Cloud الوصول؛ اختبر اتصال الجهاز. بقي المحفوظ دون تغيير.")
                if(saveFailure)dao.saveWorldFeed(failed);failed}
        }
    }
    private suspend fun fetchWithoutConditions(feed:WorldFeed,p:WorldPublisher,now:Long,install:Boolean):WorldFeed {
        val doc=http.get(feed.url);require(doc.code in 200..299){"HTTP ${doc.code}"};return parseAndSave(feed,p,doc,now,install)
    }
    private suspend fun parseAndSave(feed:WorldFeed,p:WorldPublisher,doc:NewsHttp.Document,now:Long,install:Boolean):WorldFeed {
        val previousSource=dao.allSources().firstOrNull{ArticleIdentity.canonical(it.url)==ArticleIdentity.canonical(feed.url)}
        val source=(previousSource?:Source(id=feed.id,name=p.name,url=feed.url,category=feed.category,type="WORLD",htmlAllowed=p.allowHtml,termsUrl=p.termsUrl)).copy(category=feed.category)
        val parsed=FeedParser().parse(doc.bytes,source).filter{WorldUrls.valid(it.url)&&WorldUrls.samePublisher(p.website,it.url)}
        if(install)require(!Regex("(?i)(?:nosnippet|noai|none)").containsMatchIn(doc.headers["X-Robots-Tag"].orEmpty())) { "قيّد المصدر المعالجة في رؤوس الاستجابة" }
        require(parsed.isNotEmpty()){"خلاصة فارغة؛ لم يُثبت وجود أخبار"}
        val lang=WorldMetadata.language(doc.bytes)
        require(lang.isBlank() || lang==feed.language){"لغة الخلاصة $lang تختلف عن الإعداد ${feed.language}"}
        val state=feed.copy(status="verified",attempted=now,succeeded=now,httpCode=doc.code,error="",etag=doc.headers["ETag"].orEmpty(),modified=doc.headers["Last-Modified"].orEmpty(),
            articleCount=parsed.size,fullCount=parsed.count{!it.isExcerpt},imageCount=parsed.count{it.image.isNotBlank()},latestPublished=parsed.maxOf{it.published})
        db.withTransaction {
            dao.saveWorldFeed(state)
            if(install) {
                val authors=WorldMetadata.authors(doc.bytes,feed.url)
                val oldArticles=dao.allArticles();val existing=oldArticles.associateBy{ArticleIdentity.canonical(it.url)}
                val byGuid=oldArticles.filter{it.sourceItemId.isNotBlank()}.associateBy{it.sourceId to it.sourceItemId}
                val articles=parsed.map { a->val url=ArticleIdentity.canonical(a.url);a.copy(id=(existing[url]?:byGuid[source.id to a.sourceItemId])?.id?:AiPolicy.hash(url),url=url,kind=if(feed.category=="الرأي والتحليل")"رأي / تحليل — تصنيف الخلاصة"else"مادة صحفية — نوعها غير محدد",
                    image=if(p.allowImages)a.image else "",images=if(p.allowImages)a.images else "[]",
                    contentBlocks=if(p.allowImages)a.contentBlocks else ContentCodec.blocks(ContentCodec.readBlocks(a.contentBlocks).filter{it.type!="image"})) }
                dao.saveSource(source);dao.worldSourceAccess(source.id,p.allowHtml,p.termsUrl);dao.mergeArticles(articles)
                articles.forEach { a->val author=authors[a.url].orEmpty();val prior=dao.worldArticle(a.id)
                    dao.saveWorldArticle(WorldArticle(a.id,p.id,feed.id,lang.ifBlank{feed.language},if(lang.isBlank())"publisher_declared" else "feed_declared",author.ifBlank{prior?.author.orEmpty()},"${p.name} • ${a.url} • ${p.termsUrl}")) }
                dao.saveWorldPublisher(p.copy(enabled=true,sections=com.google.gson.Gson().toJson(dao.allWorldFeeds().filter{it.publisherId==p.id&&it.succeeded>0}.map{it.category}.distinct())))
            }
        };return state
    }
    suspend fun refresh():List<WorldFeed> { val publishers=dao.allWorldPublishers().associateBy{it.id}
        return dao.allWorldFeeds().filter{publishers[it.publisherId]?.let{p->p.enabled&&p.allowCache}==true}.map{check(it,true,false)}
    }
}
