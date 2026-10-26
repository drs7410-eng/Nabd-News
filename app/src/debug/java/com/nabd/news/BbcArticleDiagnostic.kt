package com.nabd.news

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Instant

/** Debug-only probe. It never updates NewsRepository, Room, source settings, or reader navigation. */
enum class BbcDiagnosticState { FETCHING_ARTICLE, FULL_ARTICLE, PARTIAL_ARTICLE, EXTRACTION_FAILED, ACCESS_RESTRICTED }
data class BbcHttpObservation(val requestedUrl: String, val httpStatus: Int? = null, val finalUrl: String? = null,
    val contentType: String? = null, val receivedBytes: Int? = null, val redirectChain: List<String> = emptyList(),
    val proxyConnectStatus: Int? = null, val error: String? = null)
data class BbcRegionObservation(val element: String, val id: String, val classes: String, val attributes: Map<String, String>,
    val paragraphs: Int, val paragraphCharacters: Int, val headings: Int, val images: Int, val containsArticleHeading: Boolean)
data class BbcScriptObservation(val id: String, val type: String, val characters: Int, val jsonTopLevelKeys: List<String>, val parsesAsJson: Boolean)
data class BbcMethodObservation(val method: String, val state: String, val paragraphs: Int, val characters: Int,
    val bodyImages: Int, val note: String)
data class BbcDiagnosticReport(val testedAt: String = Instant.now().toString(), val target: String = BbcArticleDiagnostic.URL,
    val state: BbcDiagnosticState = BbcDiagnosticState.FETCHING_ARTICLE, val article: BbcHttpObservation? = null,
    val robots: BbcHttpObservation? = null, val rss: BbcHttpObservation? = null, val articleHtmlBytes: Int? = null,
    val htmlSha256: String? = null, val title: String? = null, val htmlTitle: String? = null, val canonical: String? = null,
    val headingCount: Int? = null, val identityConfirmed: Boolean? = null, val currentReaderTrackingIdentityMatches: Boolean = ArticleIdentity.same(BbcArticleDiagnostic.URL + "?at_medium=RSS&at_campaign=rss", BbcArticleDiagnostic.URL), val rssTitleMatches: Boolean? = null,
    val candidateParagraphs: Int? = null, val candidateBodyCharacters: Int? = null, val verifiedBodyParagraphs: Int? = null,
    val verifiedBodyCharacters: Int? = null, val rssDescriptionCharacters: Int? = null, val rssDescriptionSource: String? = null,
    val differentFromRss: Boolean? = null, val additionalCharactersBeyondRss: Int? = null, val rssContainedInBody: Boolean? = null,
    val candidateBodyImages: Int? = null, val metadataImages: Int? = null, val extractionMethod: String? = null,
    val javascriptRequired: Boolean? = null, val regions: List<BbcRegionObservation> = emptyList(),
    val scripts: List<BbcScriptObservation> = emptyList(), val methods: List<BbcMethodObservation> = emptyList(),
    val nativePreview: String = "NOT_RUN", val productionReader: String = "NOT_TESTED_NOT_MODIFIED",
    val reason: String = "الاختبار لم يكتمل") {
    fun json(): String = GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create().toJson(this)
    fun markdown(): String = buildString {
        append("# اختبار رابط BBC المحدد\n\nالحالة: $state\n\nالسبب: $reason\n\n")
        append("المعيار | النتيجة\n--- | ---\n")
        fun value(v: Any?) = v?.toString()?.replace('|', '/')?.replace('\n', ' ') ?: "غير متاح / لم يُثبت"
        fun row(k: String, v: Any?) { append("$k | ${value(v)}\n") }
        row("الرابط المطلوب", target); row("HTTP Status من BBC", article?.httpStatus)
        row("رمز رفض CONNECT من الوكيل", article?.proxyConnectStatus); row("الرابط النهائي المثبت باستجابة", article?.finalUrl)
        row("Content-Type", article?.contentType); row("بايتات الاستجابة المستلمة", article?.receivedBytes); row("HTML المقال بالبايت", articleHtmlBytes)
        row("عنوان المقال H1", title); row("عنوان HTML", htmlTitle); row("تطابق هوية الرابط", identityConfirmed); row("القارئ الحالي يعتبر رابط RSS والرابط الأساسي متطابقين", currentReaderTrackingIdentityMatches)
        row("فقرات مرشح المتن", candidateParagraphs); row("أحرف مرشح المتن", candidateBodyCharacters)
        row("فقرات المتن المؤكدة", verifiedBodyParagraphs); row("أحرف المتن المؤكدة", verifiedBodyCharacters)
        row("أحرف وصف RSS", rssDescriptionCharacters); row("مصدر الوصف", rssDescriptionSource)
        row("يختلف عن وصف RSS", differentFromRss); row("أحرف إضافية فعلية", additionalCharactersBeyondRss)
        row("الوصف موجود في المتن", rssContainedInBody); row("صور داخل مرشح المتن", candidateBodyImages)
        row("صور البيانات الوصفية فقط", metadataImages); row("طريقة الاستخراج", extractionMethod)
        row("ثبت احتياج JavaScript", javascriptRequired); row("معاينة Compose التشخيصية", nativePreview)
        row("شاشة الخبر الإنتاجية / Room", productionReader)
        append("\nوجود نص طويل أو script لا يثبت اكتمال المقال أو احتياجه إلى JavaScript. PARTIAL_ARTICLE تعني أن المرشح لم يجتز جميع معايير القبول.\n")
    }
}
data class BbcDiagnosticResult(val report: BbcDiagnosticReport, val previewBlocks: List<ArticleBlock> = emptyList(), val htmlSnapshot: ByteArray? = null)

class BbcArticleDiagnostic(private val client: OkHttpClient) {
    companion object {
        const val URL = "https://www.bbc.com/arabic/articles/c6ly0gdxd5jlo"
        const val RSS = "https://feeds.bbci.co.uk/arabic/rss.xml"
        fun sameReference(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.host !in setOf("www.bbc.com", "bbc.com") || !parsed.isHttps) return false
            val cleaned = parsed.newBuilder().removeAllQueryParameters("at_medium").removeAllQueryParameters("at_campaign").build().toString()
            return ArticleIdentity.same(cleaned, URL)
        }
        fun normalized(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ").trim()
        fun transportFailure(url: String, message: String): BbcHttpObservation {
            val proxyCode = Regex("(?i)(?:CONNECT[^\\n]*?(?:code[^0-9]*)?|CONNECT: )([45][0-9]{2})").find(message)?.groupValues?.get(1)?.toIntOrNull()
            return BbcHttpObservation(url, proxyConnectStatus = proxyCode, error = message.take(500))
        }
    }
    private data class Fetched(val observation: BbcHttpObservation, val bytes: ByteArray = byteArrayOf())
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private fun fetch(url: String, limit: Int = 4_000_000): Fetched {
        var current = url.toHttpUrlOrNull() ?: return Fetched(BbcHttpObservation(url, error = "INVALID_URL"))
        val chain = mutableListOf<String>()
        try {
            repeat(4) { hop ->
                require(current.isHttps && current.username.isBlank() && current.password.isBlank())
                http.newCall(Request.Builder().url(current).header("User-Agent", "NabdPersonal/1.2 BBC reference diagnostic").build()).execute().use { response ->
                    chain += current.toString()
                    if (response.code in 300..399) {
                        val next = response.header("Location")?.let { current.resolve(it) }
                        if (hop == 3 || next == null || !next.isHttps || next.username.isNotBlank() || next.password.isNotBlank() || next.host.removePrefix("www.") != current.host.removePrefix("www."))
                            return Fetched(BbcHttpObservation(url, response.code, current.toString(), response.header("Content-Type"), redirectChain = chain.toList(), error = "REDIRECT_NOT_FOLLOWED"))
                        current = next
                    } else {
                        val bytes = response.body?.byteStream()?.use { it.readBounded(limit + 1) } ?: byteArrayOf()
                        if (bytes.size > limit) return Fetched(BbcHttpObservation(url, response.code, current.toString(), response.header("Content-Type"), receivedBytes = bytes.size, redirectChain = chain.toList(), error = "RESPONSE_TOO_LARGE"))
                        return Fetched(BbcHttpObservation(url, response.code, current.toString(), response.header("Content-Type"), bytes.size, chain.toList()), bytes)
                    }
                }
            }
        } catch (e: Exception) {
            return Fetched(transportFailure(url, "${e.javaClass.simpleName}: ${e.message.orEmpty()}").copy(redirectChain = chain.toList()))
        }
        return Fetched(BbcHttpObservation(url, error = "REDIRECT_LIMIT"))
    }
    fun run(savedRssDescription: String = "", progress: (String) -> Unit = {}): BbcDiagnosticResult {
        var report = BbcDiagnosticReport()
        progress("طلب الصفحة الأصلية المحددة عبر HTTPS…")
        // A single explicit diagnostic document request. Analysis never bypasses access/robots restrictions.
        val page = fetch(URL)
        progress("طلب RSS ومطابقة الخبر بالرابط نفسه…")
        val feed = fetch(RSS)
        val rssArticle = if (feed.observation.httpStatus in 200..299 && feed.observation.error == null)
            runCatching { FeedParser().parse(feed.bytes, Source(id = "bbc-diagnostic", name = "BBC", url = RSS)).firstOrNull { sameReference(it.url) } }.getOrNull() else null
        val description = rssArticle?.summary?.takeIf { it.isNotBlank() } ?: savedRssDescription.takeIf { it.isNotBlank() }
        val descriptionSource = when { rssArticle?.summary?.isNotBlank() == true -> "LIVE_RSS_MATCHED_BY_CANONICAL_URL"; description != null -> "USER_PASTED_SAVED_RSS_DESCRIPTION_NOT_INDEPENDENTLY_VERIFIED"; else -> null }
        report = report.copy(article = page.observation, rss = feed.observation,
            rssDescriptionCharacters = description?.length, rssDescriptionSource = descriptionSource)
        if (page.observation.error != null || page.observation.httpStatus == null)
            return BbcDiagnosticResult(report.copy(state = BbcDiagnosticState.EXTRACTION_FAILED,
                reason = "لم تصل استجابة من BBC. فشل النقل: ${page.observation.error}. لا يمكن الحكم على محتوى الصفحة أو JavaScript."))
        val status = page.observation.httpStatus
        if (status !in 200..299) return BbcDiagnosticResult(report.copy(state = if (status in listOf(401, 402, 403, 429, 451)) BbcDiagnosticState.ACCESS_RESTRICTED else BbcDiagnosticState.EXTRACTION_FAILED,
            reason = "أعاد المصدر HTTP $status؛ لا تُعامل استجابة الحجب أو الخطأ كمقال."))
        if (page.observation.contentType?.contains("html", true) != true)
            return BbcDiagnosticResult(report.copy(state = BbcDiagnosticState.EXTRACTION_FAILED, reason = "استجابة المصدر ليست HTML؛ لم تُحلل كمقال."))
        report = report.copy(articleHtmlBytes = page.bytes.size, htmlSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(page.bytes).joinToString("") { "%02x".format(it) })
        progress("فحص قواعد المصدر قبل تحليل متن الصفحة…")
        val robots = fetch("https://www.bbc.com/robots.txt", 512_000)
        report = report.copy(robots = robots.observation)
        val rules = when {
            robots.observation.httpStatus == 404 -> ""
            robots.observation.httpStatus in 200..299 && !robots.bytes.toString(Charsets.UTF_8).trimStart().startsWith("<") -> robots.bytes.toString(Charsets.UTF_8)
            else -> null
        }
        if (rules == null || !RobotsRules.allows(rules, URL.toHttpUrlOrNull()!!))
            return BbcDiagnosticResult(report.copy(state = BbcDiagnosticState.ACCESS_RESTRICTED,
                reason = if (rules == null) "تعذر التحقق من robots.txt؛ أُوقف تحليل المتن احترازيًا، ولا يعني ذلك أن HTML فارغ." else "قواعد robots.txt لا تسمح بالاستخراج باستخدام هوية NabdPersonal؛ لم يُحلل المتن."))
        progress("فحص DOM وJSON-LD والبيانات المضمنة ومحرك القراءة…")
        return analyze(page, report, description, descriptionSource, rssArticle?.title).let {
            if (it.report.state == BbcDiagnosticState.ACCESS_RESTRICTED) it else it.copy(htmlSnapshot = page.bytes)
        }
    }
    private fun analyze(page: Fetched, report: BbcDiagnosticReport, description: String?, descriptionSource: String?, rssTitle: String?): BbcDiagnosticResult {
        val charset = page.observation.contentType?.toMediaTypeOrNullSafe()?.charset()?.name()
        val doc = Jsoup.parse(page.bytes.inputStream(), charset, URL)
        val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() }
        val ogUrl = doc.selectFirst("meta[property=og:url]")?.attr("content")?.takeIf { it.isNotBlank() }
        val headings = doc.select("h1")
        val title = headings.singleOrNull()?.text()
        val jsonLd = ArticleJsonLd.articles(doc.select("script[type=application/ld+json]").map { it.data() }, URL)
        val identity = canonical?.let(::sameReference) == true && (ogUrl == null || sameReference(ogUrl)) && page.observation.finalUrl?.let(::sameReference) == true
        // Semantic HTML is observed, never guessed Simorgh CSS/data-testid names.
        val regions = doc.select("article, main, [role=main], [itemprop=articleBody]").distinct().take(20).map { original ->
            val region = original.clone().also(::cleanRegion)
            BbcRegionObservation(original.tagName(), original.id(), original.className().take(300),
                original.attributes().filter { it.key.startsWith("data-") || it.key in listOf("role", "itemprop") }.take(15).associate { it.key to it.value.take(200) },
                region.select("p").count { it.text().isNotBlank() }, region.select("p").sumOf { it.text().length }, region.select("h1,h2,h3,h4").size,
                region.select("img").size, title != null && region.select("h1").any { normalized(it.text()) == normalized(title) })
        }
        val scripts = doc.select("script").take(100).map { script ->
            val data = script.data()
            val json = if (data.length <= 1_000_000) runCatching { JsonParser.parseString(data) }.getOrNull() else null
            BbcScriptObservation(script.id(), script.attr("type"), data.length,
                if (json?.isJsonObject == true) json.asJsonObject.keySet().take(30) else emptyList(), json != null)
        }
        val extractor = ArticleHtmlExtractor()
        val methods = mutableListOf<BbcMethodObservation>()
        val existing = extractor.extract(doc.outerHtml(), URL, SourceAdapters.forUrl(URL).articleSelectors)
        methods += observe("CURRENT_PRODUCTION_EXTRACTOR", existing, "Existing engine measured without modifying it; not proof of completeness")
        if (existing.state in setOf("restricted", "blocked", "identity_mismatch")) return BbcDiagnosticResult(report.copy(
            state = if (existing.state == "identity_mismatch") BbcDiagnosticState.EXTRACTION_FAILED else BbcDiagnosticState.ACCESS_RESTRICTED,
            title = title, htmlTitle = doc.title(), canonical = canonical, headingCount = headings.size, identityConfirmed = identity,
            regions = regions, scripts = scripts, methods = methods, reason = "رفض المحلل الصفحة بسبب ${existing.state}؛ لم يُعتمد متن أو صور."))
        val semanticCandidates = doc.select("article, [itemprop=articleBody]").map { root ->
            val clean = root.clone().also(::cleanRegion)
            clean.select("h1").remove()
            val content = extractor.fragment(clean.html(), URL, "article-body")
            val hasHeading = title != null && (root.select("h1").any { normalized(it.text()) == normalized(title) } || root.attr("itemprop") == "articleBody")
            val note = "Observed ${root.tagName()} id=${root.id()}; H1/articleBody scope=$hasHeading; requires review of actual DOM for related content"
            methods += observe("DOM_SEMANTIC_CONTAINER", content, note)
            content to hasHeading
        }
        val explicitlyScopedArticles = jsonLd.filter { obj ->
            val mainEntity = obj.get("mainEntityOfPage")
            val mainUrl = when { mainEntity?.isJsonPrimitive == true -> mainEntity.asString; mainEntity?.isJsonObject == true -> mainEntity.asJsonObject.get("@id")?.takeIf { it.isJsonPrimitive }?.asString; else -> null }
            listOfNotNull(obj.get("url")?.takeIf { it.isJsonPrimitive }?.asString,
                obj.get("@id")?.takeIf { it.isJsonPrimitive }?.asString, mainUrl).any(::sameReference)
        }
        val structured = explicitlyScopedArticles.mapNotNull { obj -> obj.get("articleBody")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }?.let { extractor.fragment(it, URL, "article-body") } }
        structured.forEach { methods += observe("IDENTITY_SCOPED_JSON_LD_ARTICLE_BODY", it, "articleBody belongs to the matching Article identity; no unrelated JSON traversed") }
        val readable = if (existing.state !in setOf("restricted", "blocked", "identity_mismatch")) runCatching {
            val copy = doc.clone(); cleanRegion(copy.body())
            net.dankito.readability4j.Readability4J(URL, copy, net.dankito.readability4j.model.ReadabilityOptions(maxElemsToParse = 25000)).parse().content
                ?.let { extractor.fragment(it, URL, "article-body") }
        }.getOrNull() else null
        readable?.let { methods += observe("READABILITY_COMPARISON_ONLY", it, "Text length is not an acceptance criterion on its own") }
        val candidate = semanticCandidates.filter { it.second }.map { it.first }.maxByOrNull { it.text.length }
            ?: structured.firstOrNull() ?: existing.takeIf { it.state == "ready" } ?: readable
        val nonEmpty = candidate?.takeIf { it.text.isNotBlank() }
        val different = if (nonEmpty != null && description != null) normalized(nonEmpty.text) != normalized(description) else null
        val normBody = nonEmpty?.let { normalized(it.text) }
        val normDescription = description?.let(::normalized)
        val structuredAgrees = nonEmpty != null && structured.any { normalized(it.text) == normBody }
        val scopedDomAgrees = nonEmpty != null && semanticCandidates.any { it.second && normalized(it.first.text) == normBody }
        val titleMatch = if (title != null && rssTitle != null) normalized(title) == normalized(rssTitle) else null
        val truncated = normBody?.let { it.endsWith("…") || it.endsWith("...") || it.endsWith("اقرأ المزيد") } ?: false
        val verified = descriptionSource == "LIVE_RSS_MATCHED_BY_CANONICAL_URL" && identity && title != null && structuredAgrees && scopedDomAgrees && different == true && titleMatch == true && !truncated
        val candidateMethod = when {
            nonEmpty == null -> null
            scopedDomAgrees && structuredAgrees -> "DOM_AND_MATCHING_JSON_LD_ARTICLE_BODY"
            scopedDomAgrees -> "DOM_SEMANTIC_CONTAINER_REQUIRES_REVIEW"
            structuredAgrees -> "MATCHING_JSON_LD_ARTICLE_BODY_REQUIRES_REVIEW"
            nonEmpty === existing -> "CURRENT_PRODUCTION_EXTRACTOR_REQUIRES_REVIEW"
            else -> "READABILITY_REQUIRES_REVIEW"
        }
        val state = when { verified -> BbcDiagnosticState.FULL_ARTICLE; nonEmpty != null -> BbcDiagnosticState.PARTIAL_ARTICLE; else -> BbcDiagnosticState.EXTRACTION_FAILED }
        val reason = when {
            verified -> "تطابقت هوية الرابط والعنوان مع RSS، واتفق متن DOM مع articleBody المرتبط بالمقال، واختلف النص عن المقتطف. يبقى فحص العرض الإنتاجي وRoom غير منفذ حتى اعتماد النتيجة."
            nonEmpty == null -> "لم يُعثر على متن موثوق في HTML. وجود scripts وحده لا يثبت احتياج JavaScript؛ لم يُفعّل WebView أو المتصفح تلقائيًا."
            !identity -> "يوجد نص مرشح لكن لم تتأكد هوية الصفحة؛ لا يُعتمد كمقال BBC المطلوب."
            description == null -> "يوجد نص مرشح، لكن وصف RSS لهذا الرابط غير متاح للمقارنة. قد خرج الخبر من الخلاصة الحالية؛ لم يُستبدل الوصف بـmeta description."
            different == false -> "النص المستخرج يطابق مقتطف RSS؛ لم يُعتبر مقالًا كاملًا."
            else -> "يوجد متن مرشح يحتاج مراجعة بنية الصفحة الفعلية؛ لم يثبت اتفاق DOM وarticleBody وعنوان RSS. الطول وحده لا يثبت النجاح."
        }
        val bodyImages = nonEmpty?.blocks?.filter { it.type == "image" }?.map { ImagePolicy.identity(it.url) }?.distinct()?.size
        return BbcDiagnosticResult(report.copy(state = state, title = title, htmlTitle = doc.title(), canonical = canonical,
            headingCount = headings.size, identityConfirmed = identity, rssTitleMatches = titleMatch,
            candidateParagraphs = nonEmpty?.blocks?.count { it.type == "paragraph" }, candidateBodyCharacters = nonEmpty?.text?.length,
            verifiedBodyParagraphs = if (verified) nonEmpty?.blocks?.count { it.type == "paragraph" } else null,
            verifiedBodyCharacters = if (verified) nonEmpty?.text?.length else null,
            rssDescriptionCharacters = description?.length, rssDescriptionSource = descriptionSource, differentFromRss = different,
            additionalCharactersBeyondRss = if (normBody != null && normDescription != null) normBody.length - normDescription.length else null,
            rssContainedInBody = if (normBody != null && normDescription != null) normBody.contains(normDescription) else null,
            candidateBodyImages = bodyImages, metadataImages = existing.images.count { it.origin in setOf("og:image", "twitter:image", "json-ld") },
            extractionMethod = candidateMethod, regions = regions, scripts = scripts, methods = methods, reason = reason), if (identity && (scopedDomAgrees || structuredAgrees)) nonEmpty?.blocks.orEmpty() else emptyList())
    }
    private fun cleanRegion(root: Element) {
        root.select("nav,aside,footer,header,script,style,form,button,iframe,noscript,[hidden],[aria-hidden=true],[data-nosnippet]").remove()
        root.select("*").filter { e -> Regex("(?i)(^|[ _-])(related|recommended|comments?|advert(?:isement)?|social|share|promo|cookie)([ _-]|$)").containsMatchIn(e.className() + " " + e.id()) }.forEach { it.remove() }
    }
    private fun observe(method: String, c: ExtractedContent, note: String) = BbcMethodObservation(method, c.state,
        c.blocks.count { it.type == "paragraph" }, c.text.length, c.blocks.count { it.type == "image" }, note)
    private fun String.toMediaTypeOrNullSafe(): MediaType? = runCatching { this.toMediaTypeOrNull() }.getOrNull()
}
