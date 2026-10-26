package com.nabd.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class NewsRepository(private val dao: NabdDao, client: OkHttpClient) {
    private val http = NewsHttp(client.newBuilder().followRedirects(false).followSslRedirects(false).build())
    private val parser = FeedParser()
    private val extractor = ArticleHtmlExtractor()
    private val detailsLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val lastFeedFetch = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val robots = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String?>>()
    suspend fun seed() {
        if (dao.allSources().isEmpty()) {
            dao.saveSource(Source(id = "bbc-ar", name = "BBC عربي", url = "https://feeds.bbci.co.uk/arabic/rss.xml", category = "العالم"))
            dao.saveSource(Source(id = "aj-ar", name = "الجزيرة", url = "https://www.aljazeera.net/aljazeerarss/arhome", category = "العالم"))
        }
    }
    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        var count = 0; var failures = 0
        dao.allSources().filter { it.type == "RSS" }.forEach { source ->
            try {
                val (articles, quality) = fetchFeed(source)
                dao.saveFeedQuality(quality)
                dao.mergeArticles(articles); lastFeedFetch[source.id] = System.currentTimeMillis(); count += articles.size; if (quality.status != "ok") failures++
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failures++; dao.saveFeedQuality(FeedQuality.failure(source)) }
        }
        "تمت قراءة $count مادة من الخلاصات" + if (failures > 0) "؛ تعذر تحديث $failures مصدر. تبقى الأخبار المحفوظة متاحة." else ""
    }
    private fun fetchFeed(source: Source): Pair<List<Article>, FeedQuality> {
        val document = http.get(source.url)
        if (document.code !in 200..299) return emptyList<Article>() to FeedQuality.failure(source, document.code)
        val articles = parser.parse(document.bytes, source)
        return articles to FeedQuality.assess(source, articles, document.code)
    }
    suspend fun activate(entry: CatalogSource): FeedQuality = withContext(Dispatchers.IO) {
        val feed = requireNotNull(entry.feed) { "لا توجد خلاصة مؤكدة لهذا المصدر" }
        val existing = dao.allSources().firstOrNull { it.url.trimEnd('/') == feed.trimEnd('/') || it.id == entry.id }
        val source = existing ?: Source(id = entry.id, name = entry.name, url = feed, category = entry.category)
        val (articles, quality) = fetchFeed(source)
        require(quality.status == "ok" && articles.isNotEmpty()) { "تعذر تفعيل ${entry.name}: لم تصل خلاصة صالحة تحتوي أخبارًا. لم يُضف رابط معطّل." }
        dao.installSource(source, articles, quality)
        quality
    }
    suspend fun audit(source: Source): FeedQuality = withContext(Dispatchers.IO) {
        val quality = try { fetchFeed(source).second }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { FeedQuality.failure(source) }
        dao.saveFeedQuality(quality)
        quality
    }
    fun parse(bytes: ByteArray, source: Source) = parser.parse(bytes, source)
    suspend fun loadDetails(id: String, force: Boolean = false): String = withContext(Dispatchers.IO) {
        detailsLocks.getOrPut(id) { Mutex() }.withLock {
            var article = dao.article(id) ?: return@withLock "unavailable"
            if (!ContentCompleteness.needsPage(article, force, System.currentTimeMillis())) return@withLock "ready"
            val sources = dao.allSources().filter { it.type in setOf("RSS", "WORLD") }
            val source = sources.firstOrNull { it.id == article.sourceId } ?: sources.singleOrNull { it.name == article.source }
            // v1 discarded markup/media. Backfill its original feed once per source on opening,
            // preserving IDs/bookmarks and never substituting generated or invented article text.
            if (article.detailState == "legacy" && source != null &&
                (lastFeedFetch[source.id] ?: 0) < System.currentTimeMillis() - 10 * 60_000) {
                lastFeedFetch[source.id] = System.currentTimeMillis()
                try {
                    val feed = http.get(source.url)
                    if (feed.code in 200..299) dao.mergeArticles(parser.parse(feed.bytes, source))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Original excerpt remains available offline. */ }
                article = dao.article(id) ?: article
                if (article.contentOrigin == "manual") return@withLock "ready"
            }
            val worldMeta=dao.worldArticle(id)
            val worldPublisher=worldMeta?.let{dao.worldPublisher(it.publisherId)}
            if(worldMeta!=null && (worldPublisher?.allowCache!=true || worldMeta.restriction.isNotBlank() || !worldPublisher.allowHtml || WorldRights.knownRestricted(worldPublisher.website)))
                return@withLock store(article,ExtractedContent(state="permission_required",reason="لم يُسجل إذن استخراج صفحة هذا الناشر في نبض العالم؛ النص المتاح لا يثبت اكتمال المقال."))
            val target = article.url.toHttpUrlOrNull()
            if (source == null) return@withLock store(article, ExtractedContent(state = "permission_required", reason = "مصدر هذا الخبر غير موجود في قائمة المصادر الحالية؛ أضفه من الدليل وأعد المحاولة."))
            if (!(worldPublisher?.allowHtml?:source.htmlAllowed) || (worldPublisher?.termsUrl?:source.termsUrl).toHttpUrlOrNull()?.isHttps != true)
                return@withLock store(article, ExtractedContent(state = "permission_required", reason = "استخراج HTML لهذا المصدر غير مفعّل أو لا يتوفر إذن مسجل. فعّله من إعداد المصدر فقط عندما تسمح شروطه بذلك."))
            if (target?.isHttps != true) return@withLock store(article, ExtractedContent(state = "unavailable", reason = "رابط المقال ليس رابط HTTPS صالحًا."))
            if (!(if(worldMeta!=null)WorldUrls.samePublisher(source.url,article.url)else PublisherScope.samePublisher(source.url, article.url))) return@withLock store(article, ExtractedContent(state = "identity_mismatch", reason = "نطاق المقال مختلف عن ناشر الخلاصة؛ لم يُحمّل محتوى من مصدر آخر."))
            if (!force && !(ArticleExtractors.forUrl(article.url) != null && article.extractorId != ArticleExtractors.forUrl(article.url)?.id) && article.detailState !in setOf("permission_required", "ready", "not_requested", "legacy") && article.detailFetchedAt > System.currentTimeMillis() - 30 * 60_000) return@withLock article.detailState
            try {
                // Rights must be explicitly recorded per source; robots.txt alone is not a license.
                val origin = target.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
                val cached = robots[origin]?.takeIf { it.first > System.currentTimeMillis() - 24 * 60 * 60_000 }
                val ruleText = if (cached != null) cached.second else {
                    val fetched = http.get(origin + "robots.txt", 512_000)
                    val text = when { fetched.code == 404 -> ""; fetched.code in 200..299 && !fetched.body.trimStart().startsWith("<") -> fetched.body; else -> null }
                    robots[origin] = System.currentTimeMillis() to text
                    text
                }
                if (ruleText == null || !RobotsRules.allows(ruleText, target)) return@withLock store(article, ExtractedContent(state = "robots_denied", reason = if (ruleText == null) "تعذر التحقق من robots.txt؛ لم يُستخرج المقال." else "قواعد المصدر تمنع الاستخراج الآلي لهذا المسار."))
                val doc = http.get(article.url)
                val contentType = doc.headers["Content-Type"].orEmpty()
                val snippetRestricted = doc.headers["X-Robots-Tag"].orEmpty().lowercase().let { it.contains("nosnippet") || it.contains("none") }
                val result = when {
                    doc.code in listOf(401, 402, 403, 429, 451) || snippetRestricted -> ExtractedContent(state = "restricted", reason = if (snippetRestricted) "قيّد المصدر استخراج النص في رؤوس الاستجابة." else "قيّد المصدر الوصول إلى المقال • HTTP ${doc.code}")
                    doc.code !in 200..299 || !contentType.contains("html", true) -> ExtractedContent(state = "unavailable", reason = "تعذر قراءة صفحة المقال • HTTP ${doc.code} • نوع الاستجابة: ${contentType.take(80)}")
                    else -> {
                        if(worldMeta!=null && worldMeta.author.isBlank()) {
                            val author=WorldMetadata.author(doc.body)
                            if(author.isNotBlank())dao.saveWorldArticle(worldMeta.copy(author=author))
                        }
                        extractor.extract(doc.body, article.url, SourceAdapters.forUrl(article.url).articleSelectors)
                    }
                }
                store(article, result)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: java.net.SocketTimeoutException) { store(article, ExtractedContent(state = "unavailable", reason = "انتهت مهلة اتصال المصدر. أعد المحاولة عبر اتصال الجهاز.")) }
            catch (e: javax.net.ssl.SSLException) { store(article, ExtractedContent(state = "unavailable", reason = "تعذر التحقق من اتصال TLS بالمصدر؛ لم تُعطّل حماية الشهادة.")) }
            catch (_: Exception) { store(article, ExtractedContent(state = "unavailable", reason = "تعذر الاتصال أو تحليل صفحة المصدر. أعد المحاولة؛ لم يُستبدل المتن المحفوظ بمقتطف.")) }
        }
    }
    private suspend fun store(article: Article, result: ExtractedContent): String {
        val world=dao.worldArticle(article.id)?.let{dao.worldPublisher(it.publisherId)}
        val extracted=if(world!=null && !world.allowImages)result.copy(blocks=result.blocks.filter{it.type!="image"},images=emptyList())else result
        val specialized = ArticleExtractors.forUrl(article.url) != null
        val keepFull = specialized && extracted.state == "partial" && article.detailState == "ready" &&
            article.extractorId == BbcArticleExtractor.VERSION && article.fullContent.isNotBlank()
        val success = !keepFull && extracted.state in setOf("ready", "partial") && extracted.text.isNotBlank()
        val oldImages = ContentCodec.readImages(article.images)
        val retained = if (extracted.images.isNotEmpty()) oldImages.filter { it.origin in setOf("media:content", "media:thumbnail", "enclosure", "rss-content") } else oldImages
        val images = if (keepFull) oldImages else if (specialized && success) extracted.images.distinctBy { ImagePolicy.identity(it.url) } else ImagePolicy.ordered(extracted.images + retained)
        dao.saveDetails(article.id, if (success) extracted.text.take(200000) else article.fullContent,
            if (success) ContentCodec.blocks(extracted.blocks) else article.contentBlocks, ContentCodec.images(images),
            if (specialized && success) images.firstOrNull()?.url.orEmpty() else images.firstOrNull()?.url ?: article.image,
            if (success) "article_page" else article.contentOrigin, extracted.state, System.currentTimeMillis(), if (keepFull) "تعذر تأكيد التحديث؛ احتُفظ بآخر متن كامل محفوظ. ${extracted.reason}" else extracted.reason,
            if (success) extracted.extractorId else article.extractorId)
        return extracted.state
    }
}
