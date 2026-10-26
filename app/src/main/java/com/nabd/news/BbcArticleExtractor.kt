package com.nabd.news

import com.google.gson.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Simorgh schema observed in the SHA-verified Galaxy capture, independently checked against visible DOM. */
class BbcArticleExtractor : SourceArticleExtractor {
    companion object { const val VERSION = "bbc-simorgh-v1" }
    override val id = VERSION
    override fun matches(url: String): Boolean = url.toHttpUrlOrNull()?.let {
        ArticleIdentity.isBbcHost(it.host) && Regex("/arabic/articles/[a-zA-Z0-9]+/?").matches(it.encodedPath)
    } == true
    private fun normal(s: String) = s.replace('\u00a0', ' ').replace(Regex("[\\s\\p{Z}]+"), " ").trim()
    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject?.string(key: String): String? = this?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject?.array(key: String): List<JsonObject> = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { p -> p.isJsonObject }?.asJsonObject }.orEmpty()
    private fun model(node: JsonObject?) = node.obj("model")
    private fun children(node: JsonObject?) = model(node).array("blocks")
    private fun fail(state: String, reason: String) = ExtractedContent(state = state, reason = reason, extractorId = id)
    override fun extract(document: Document, url: String): ExtractedContent = try { parse(document, url) }
        catch (_: Exception) { fail("bbc_structure_changed", "تعذر قراءة بنية BBC بأمان؛ لم يُستخدم مقتطف RSS كمقال كامل.") }

    private fun parse(doc: Document, url: String): ExtractedContent {
        val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty()
        if (canonical.isBlank() || !ArticleIdentity.same(canonical, url)) return fail("identity_mismatch", "هوية صفحة BBC لا تطابق رابط الخبر.")
        val mains = doc.select("main[role=main]")
        val main = mains.singleOrNull() ?: return fail("bbc_structure_changed", "لم توجد منطقة القراءة الوحيدة المتوقعة في BBC.")
        val heading = main.select("h1").singleOrNull()?.text()?.let(::normal).orEmpty()
        if (heading.isBlank()) return fail("bbc_structure_changed", "عنوان المقال غير متاح للتحقق من هوية متن BBC.")
        val dom = domBlocks(main, url)
        if (dom.blocks.none { it.type == "paragraph" }) return fail("bbc_structure_changed", "لم توجد فقرات موثوقة داخل منطقة المقال.")
        val script = doc.select("script#__NEXT_DATA__[type=application/json]").singleOrNull()
            ?: return dom.copy(state = "partial", reason = "عُرض النص الظاهر في BBC، لكن بيانات المقال المنظمة غير متاحة لتأكيد اكتماله.")
        if (script.data().length > 1_500_000) return fail("bbc_structure_changed", "بيانات BBC المنظمة تجاوزت حد المعالجة.")
        val json = runCatching { JsonParser.parseString(script.data()).asJsonObject }.getOrNull()
            ?: return dom.copy(state = "partial", reason = "بيانات BBC المنظمة غير صالحة؛ المتاح نص DOM فقط وغير مؤكد الاكتمال.")
        val props = json.obj("props").obj("pageProps")
        val page = props.obj("pageData")
        val metadata = page.obj("metadata")
        val path = url.toHttpUrlOrNull()!!.encodedPath.trimEnd('/')
        if (props.string("id")?.trimEnd('/') != path || props.string("pathname")?.trimEnd('/') != path ||
            props.string("service") != "arabic" || props.string("pageType") != "article" ||
            metadata.string("type") != "article" ||
            metadata.obj("locators").string("canonicalUrl")?.let { ArticleIdentity.same(it, url) } != true)
            return fail("identity_mismatch", "بيانات BBC المضمنة لا تخص المقال نفسه؛ لم يُستخدم نصها أو صورها.")
        val groups = page.obj("content").obj("model").array("blocks")
        if (groups.isEmpty() || groups.size > 3000) return dom.copy(state = "partial", reason = "كتل BBC المنظمة ناقصة؛ عُرض النص الظاهر فقط.")
        val headlines = groups.filter { it.string("type") == "headline" }.flatMap { children(it) }.flatMap { children(it) }
            .filter { it.string("type") == "paragraph" }.mapNotNull { model(it).string("text")?.let(::normal) }
        if (headlines.singleOrNull() != heading) return fail("identity_mismatch", "عنوان بيانات BBC لا يطابق عنوان المقال الظاهر.")
        val output = mutableListOf<ArticleBlock>(); val images = mutableListOf<ArticleImage>()
        val issues = mutableListOf<String>()
        val domParagraphs = dom.blocks.filter { it.type == "paragraph" }
        var paragraphIndex = 0
        fun paragraph(n: JsonObject, type: String = "paragraph") {
            val text = model(n).string("text")?.let(::normal)
            if (text.isNullOrBlank()) { issues += "فقرة ناقصة"; return }
            val visible = if (type == "paragraph") domParagraphs.getOrNull(paragraphIndex++) else null
            val links = visible?.takeIf { it.text == text }?.links ?: inlineLinks(n, text, url)
            output += ArticleBlock(type, text = text, level = if (type == "heading") 2 else 0, links = links)
        }
        fun list(n: JsonObject) {
            val items = children(n)
            // Only link-only references to other BBC stories are excluded, never all unordered lists.
            val referencesOnly = items.isNotEmpty() && items.all { item ->
                val p = children(item).singleOrNull()
                val link = p?.let(::children)?.singleOrNull()
                link?.string("type") == "urlLink" && model(link).string("locator")?.let { target ->
                    target.toHttpUrlOrNull()?.let { ArticleIdentity.isBbcHost(it.host) && it.encodedPath != path } == true
                } == true && normal(model(p).string("text").orEmpty()) == normal(model(link).string("text").orEmpty())
            }
            if (!referencesOnly) items.forEach { item -> children(item).filter { it.string("type") == "paragraph" }.forEach { paragraph(it, "list_item") } }
        }
        groups.forEach { group -> when (group.string("type")) {
            "headline", "timestamp", "disclaimer", "continueReading", "mpu", "wsoj", "podcastPromo" -> Unit
            "text" -> {
                if (children(group).isEmpty()) issues += "مجموعة نص ناقصة"
                children(group).forEach { n -> when(n.string("type")) {
                    "paragraph" -> paragraph(n)
                    "unorderedList", "orderedList" -> list(n)
                    else -> issues += "كتلة نص جديدة"
                } }
            }
            "subheadline" -> children(group).forEach { n -> children(n).filter { it.string("type") == "paragraph" }.forEach { paragraph(it, "heading") } }
            "image" -> {
                val raw = children(group).singleOrNull { it.string("type") == "rawImage" }?.let(::model)
                val locator = raw.string("locator")
                val matched = if (locator == null) null else dom.images.firstOrNull { it.url.substringBefore('?').contains(locator) }
                if (matched == null) issues += "صورة لم تطابق DOM" else {
                    images += matched; output += ArticleBlock("image", url = matched.url, caption = matched.caption)
                }
            }
            else -> issues += "نوع محتوى جديد"
        } }
        val textMatches = output.filter { it.type != "image" }.map { it.type to it.text } == dom.blocks.filter { it.type != "image" }.map { it.type to it.text }
        val imageMatches = output.filter { it.type == "image" }.map { ImagePolicy.identity(it.url) } == dom.blocks.filter { it.type == "image" }.map { ImagePolicy.identity(it.url) }
        val orderMatches = output.map { if (it.type == "image") it.type to ImagePolicy.identity(it.url) else it.type to it.text } ==
            dom.blocks.map { if (it.type == "image") it.type to ImagePolicy.identity(it.url) else it.type to it.text }
        if (!textMatches || !imageMatches || !orderMatches || issues.isNotEmpty())
            return dom.copy(state = "partial", reason = "لم تتطابق جميع كتل BBC المنظمة مع النص الظاهر أو تغيرت البنية؛ المحتوى المعروض جزئي وغير مؤكد الاكتمال.")
        return ExtractedContent(output, images.distinctBy { ImagePolicy.identity(it.url) }, "ready", "تطابق متن BBC وصوره وترتيبها بين DOM والبيانات المنظمة.", id)
    }

    private fun domBlocks(main: Element, url: String): ExtractedContent {
        val blocks = mutableListOf<ArticleBlock>(); val images = mutableListOf<ArticleImage>()
        main.children().forEach { wrapper ->
            if (wrapper.tagName() == "div" && wrapper.attr("dir") == "rtl" && !wrapper.hasAttr("data-e2e")) {
                wrapper.children().forEach { p ->
                    val type = when { p.tagName() == "p" -> "paragraph"; Regex("h[2-6]").matches(p.tagName()) -> "heading"; else -> null }
                    if (p.tagName() in setOf("ul", "ol")) {
                        val items = p.children().filter { it.tagName() == "li" }
                        val referenceList = items.isNotEmpty() && items.all { item ->
                            val anchors = item.select("a[href]")
                            val a = anchors.singleOrNull()
                            val target = a?.let { url.toHttpUrlOrNull()?.resolve(it.attr("href")) }
                            a != null && target != null && ArticleIdentity.isBbcHost(target.host) &&
                                target.encodedPath != url.toHttpUrlOrNull()?.encodedPath && normal(item.text()) == normal(a.text())
                        }
                        if (!referenceList) items.forEach { item ->
                            val text = normal(item.text())
                            if (text.isNotBlank()) blocks += ArticleBlock("list_item", text = text, links = elementLinks(item, text, url))
                        }
                    }
                    if (type != null && !p.hasAttr("hidden") && p.attr("aria-hidden") != "true") {
                        val text = normal(p.text())
                        if (text.isNotBlank()) blocks += ArticleBlock(type, text = text, level = if (type == "heading") 2 else 0, links = elementLinks(p, text, url))
                    }
                }
            } else if (wrapper.tagName() == "figure") {
                val img = wrapper.selectFirst("img")
                val candidate = img?.let { ImagePolicy.fromElement(it, url, "article-body") }
                if (candidate != null) {
                    val caption = wrapper.selectFirst("figcaption")?.text()?.let(::normal).orEmpty()
                    val credit = wrapper.select("p[role=text]").firstOrNull()?.text()?.let(::normal).orEmpty()
                    val image = candidate.copy(caption = listOf(caption, credit).filter { it.isNotBlank() }.joinToString(" • "))
                    images += image; blocks += ArticleBlock("image", url = image.url, caption = image.caption)
                }
            }
        }
        return ExtractedContent(blocks, images, "partial", extractorId = id)
    }
    private fun safeLink(target: String, base: String) = base.toHttpUrlOrNull()?.resolve(target)?.takeIf { it.username.isBlank() && it.password.isBlank() && it.scheme in setOf("http", "https") }?.toString()
    private fun elementLinks(p: Element, text: String, base: String): List<ArticleLink> {
        var cursor = 0
        return p.select("a[href]").mapNotNull { a ->
            val label = normal(a.text()); val start = if (label.isBlank()) -1 else text.indexOf(label, cursor)
            val link = safeLink(a.attr("href"), base)
            if (start < 0 || link == null) null else { cursor = start + label.length; ArticleLink(start, cursor, link) }
        }
    }
    private fun inlineLinks(n: JsonObject, text: String, base: String): List<ArticleLink> {
        val result = mutableListOf<ArticleLink>(); var cursor = 0
        fun walk(node: JsonObject, depth: Int) {
            if (depth > 12) return
            if (node.string("type") == "urlLink") {
                val label = model(node).string("text")?.let(::normal).orEmpty(); val target = model(node).string("locator")
                val start = if (label.isBlank()) -1 else text.indexOf(label, cursor)
                val link = target?.let { safeLink(it, base) }
                if (start >= 0 && link != null) { cursor = start + label.length; result += ArticleLink(start, cursor, link) }
            } else children(node).forEach { walk(it, depth + 1) }
        }
        walk(n, 0); return result
    }
}
