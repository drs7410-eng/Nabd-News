package com.nabd.news

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Safe, inert native rendering instructions. Raw publisher HTML is never rendered in a WebView. */
data class ArticleLink(val start: Int, val end: Int, val url: String)
data class ArticleBlock(val type: String = "paragraph", val text: String = "", val url: String = "", val caption: String = "", val level: Int = 0, val links: List<ArticleLink>? = emptyList())
data class ArticleImage(val url: String, val origin: String, val width: Int = 0, val height: Int = 0, val caption: String = "")
data class ExtractedContent(val blocks: List<ArticleBlock> = emptyList(), val images: List<ArticleImage> = emptyList(), val state: String = "unavailable", val reason: String = "", val extractorId: String = "") {
    val text: String get() = blocks.filter { it.type != "image" }.joinToString("\n\n") { it.text }
}
object ContentCodec {
    private val gson = Gson()
    fun blocks(value: List<ArticleBlock>): String = gson.toJson(value)
    fun images(value: List<ArticleImage>): String = gson.toJson(value)
    fun displayBlocks(article: Article): List<ArticleBlock> = readBlocks(article.contentBlocks).ifEmpty {
        article.availableText.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.map { ArticleBlock(text = it) }
    }.filterNot { it.type == "image" && ImagePolicy.identity(it.url) == ImagePolicy.identity(article.image) }
    fun readBlocks(json: String): List<ArticleBlock> = runCatching {
        gson.fromJson<List<ArticleBlock>>(json, object : TypeToken<List<ArticleBlock>>() {}.type).orEmpty()
            .filter { it.type in setOf("paragraph", "heading", "list_item", "quote", "image") }.take(3000)
    }.getOrDefault(emptyList())
    fun readImages(json: String): List<ArticleImage> = runCatching {
        gson.fromJson<List<ArticleImage>>(json, object : TypeToken<List<ArticleImage>>() {}.type).orEmpty()
            .filter { ImagePolicy.resolve(it.url, it.url) != null }.take(60)
    }.getOrDefault(emptyList())
}
object ImagePolicy {
    private val unwanted = Regex("(?i)(?:^|[/_.-])(?:logo|favicon|sprite|avatar|placeholder|advert|advertisement|banner|tracking|pixel|spacer)(?:[/_.-]|$)")
    fun resolve(raw: String, base: String): String? {
        val value = raw.trim().replace("&amp;", "&")
        if (value.isBlank() || value.startsWith("data:") || value.startsWith("javascript:") || value.startsWith("blob:")) return null
        val resolved = base.toHttpUrlOrNull()?.resolve(value) ?: value.toHttpUrlOrNull() ?: return null
        val parsed = if (!resolved.isHttps) resolved.newBuilder().scheme("https").port(if (resolved.port == 80) 443 else resolved.port).build() else resolved
        if (!parsed.isHttps || parsed.username.isNotBlank() || parsed.password.isNotBlank()) return null
        if (unwanted.containsMatchIn(parsed.encodedPath) || parsed.encodedPath.endsWith(".svg", true)) return null
        return parsed.toString()
    }
    fun candidate(raw: String, base: String, origin: String, width: Int = 0, height: Int = 0, caption: String = ""): ArticleImage? {
        val url = resolve(raw, base) ?: return null
        if ((width in 1..119) || (height in 1..79)) return null
        if (caption.contains("شعار") || caption.contains("logo", true) || caption.contains("advertisement", true)) return null
        return ArticleImage(url, origin, width.coerceAtLeast(0), height.coerceAtLeast(0), caption.take(1000))
    }
    fun identity(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.host == "ichef.bbci.co.uk" || Regex("ichef-\\d+\\.bbci\\.co\\.uk").matches(parsed.host)) {
            Regex("([a-zA-Z0-9]{4}/live/[^/]+?\\.(?:jpe?g|png))(?:\\.(?:webp|avif))?$").find(parsed.encodedPath)?.let { return "bbc-photo:" + it.groupValues[1] }
        }
        val builder = parsed.newBuilder().fragment(null)
        listOf("w", "h", "width", "height", "quality", "q", "fit", "crop").forEach { builder.removeAllQueryParameters(it) }
        var identity = builder.build().toString().replace(Regex("-\\d{2,4}x\\d{2,4}(?=\\.[a-zA-Z]{3,4}(?:\\?|$))"), "")
        if (parsed.host.endsWith("bbci.co.uk")) identity = identity.replace(Regex("/(news|images)/\\d+(?:x\\d+)?/"), "/$1/")
            .replace(Regex("://ichef-\\d+\\."), "://ichef.")
        if (parsed.host.endsWith("nyt.com")) identity = identity.replace(Regex("-(facebookJumbo|master[0-9]+|superJumbo|jumbo|articleLarge|videoSixteenByNine[0-9]+)(?=\\.)", RegexOption.IGNORE_CASE), "")
        return identity
    }
    fun ordered(images: List<ArticleImage>): List<ArticleImage> {
        val order = listOf("media:content", "media:thumbnail", "enclosure", "rss-content", "og:image", "twitter:image", "json-ld", "article-body")
        return images.sortedWith(compareBy<ArticleImage> { order.indexOf(it.origin).takeIf { n -> n >= 0 } ?: 99 }
            .thenByDescending { it.width }).distinctBy { identity(it.url) }.take(60)
    }
    fun fromElement(img: Element, base: String, origin: String): ArticleImage? {
        val label = img.attr("alt")
        if (unwanted.containsMatchIn(img.className()) || unwanted.containsMatchIn(img.id())) return null
        val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }.ifBlank {
            img.parent()?.takeIf { it.normalName() == "picture" }?.selectFirst("source[srcset]")?.attr("srcset").orEmpty()
        }
        val best = srcset.split(',').map { it.trim().split(Regex("\\s+")) }.filter { it.firstOrNull()?.isNotBlank() == true }
            .maxByOrNull { it.getOrNull(1)?.removeSuffix("w")?.removeSuffix("x")?.toDoubleOrNull() ?: 0.0 }?.firstOrNull().orEmpty()
        val candidates = listOf(best, img.attr("data-src"), img.attr("data-original"), img.attr("src"))
        return candidates.firstNotNullOfOrNull { candidate(it, base, origin, img.attr("width").toIntOrNull() ?: 0, img.attr("height").toIntOrNull() ?: 0, label) }
    }
}
object ArticleIdentity {
    fun isBbcHost(host: String) = listOf("bbc.com", "bbc.co.uk").any { host == it || host.endsWith(".$it") }
    fun canonical(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val b = parsed.newBuilder().fragment(null).scheme("https").port(if (parsed.port == 80) 443 else parsed.port)
        parsed.queryParameterNames.filter { it.startsWith("utm_") || it in setOf("fbclid", "gclid") }.forEach { b.removeAllQueryParameters(it) }
        if (isBbcHost(parsed.host)) listOf("at_medium", "at_campaign").forEach { b.removeAllQueryParameters(it) }
        return b.build().toString().removeSuffix("/").replace("://www.", "://")
    }
    fun same(a: String, b: String): Boolean {
        val resolved = b.toHttpUrlOrNull()?.resolve(a)?.toString() ?: a
        return canonical(resolved) == canonical(b)
    }
}
class ArticleHtmlExtractor {
    private val noise = Regex("(?i)(?:^|[ _-])(?:ads?|advert(?:isement)?s?|sponsored|related(?:-stories|-posts|-articles)?|recommend(?:ed|ations)?|comments?|newsletter|share(?:-tools|-buttons)?|social|cookie(?:-banner)?|breadcrumb|navigation|promo)(?:$|[ _-])")
    fun fragment(html: String, base: String, imageOrigin: String = "rss-content"): ExtractedContent {
        val normalizedHtml = html.replace(Regex("<(/?)[A-Za-z][A-Za-z0-9_.-]*:"), "<$1")
        val root = Jsoup.parseBodyFragment(normalizedHtml, base).body()
        clean(root)
        return blocks(root, base, imageOrigin).copy(state = "ready")
    }
    fun extract(html: String, url: String, selectors: List<String> = emptyList()): ExtractedContent {
        val doc = Jsoup.parse(html, url)
        val imageBase = doc.baseUri().ifBlank { url }
        val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty()
        val ogUrl = doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty()
        if (canonical.isNotBlank() && !ArticleIdentity.same(canonical, url)) return ExtractedContent(state = "identity_mismatch")
        if (ogUrl.isNotBlank() && !ArticleIdentity.same(ogUrl, url)) return ExtractedContent(state = "identity_mismatch")
        val json = ArticleJsonLd.articles(doc.select("script[type=application/ld+json]").map { it.data() }, url)
        val robotDirectives = doc.select("meta[name=robots], meta[name=NabdPersonal]").joinToString(",") { it.attr("content") }.lowercase()
        if (json.any { it.has("isAccessibleForFree") && it.get("isAccessibleForFree").asString.equals("false", true) } ||
            doc.select("[data-paywall], .paywall, #paywall, .subscription-wall, .login-wall, [data-testid=paywall]").isNotEmpty() ||
            robotDirectives.contains("nosnippet") || robotDirectives.contains("none")) return ExtractedContent(state = "restricted")
        val title = doc.title().lowercase()
        if (title.contains("just a moment") || title.contains("access denied") || doc.select("#challenge-form, .g-recaptcha, [name=cf-turnstile-response]").isNotEmpty()) return ExtractedContent(state = "blocked")
        ArticleExtractors.forUrl(url)?.let { return it.extract(doc, url) }
        val roots = selectors + listOf("[itemprop=articleBody]", "[property=articleBody]", ".article-body", ".story-body__inner", ".story-body", ".article__body", "main .wysiwyg", "article")
        val semanticRoot = roots.flatMap { selector -> doc.select(selector).map { it.clone().also(::clean) } }
            .filter { it.text().length >= 160 && it.select("p").isNotEmpty() }
            .maxByOrNull { e -> e.select("p").sumOf { it.text().length } - 2 * e.select("a").sumOf { it.text().length } }
        // Readability works only on this fetched public document, after access checks above.
        // It never loads other pages, executes scripts, or expands a paywall preview.
        val readableRoot = if (semanticRoot == null || semanticRoot.text().length < 1200) runCatching {
            val candidateDoc = doc.clone().also { clean(it.body()) }
            val candidate = net.dankito.readability4j.Readability4J(url, candidateDoc,
                net.dankito.readability4j.model.ReadabilityOptions(maxElemsToParse = 25000)).parse().articleContent
            candidate?.also(::clean)?.takeIf { e ->
                e.text().length >= 500 && e.select("p").size >= 2 &&
                    e.select("a").sumOf { it.text().length } < e.text().length / 4
            }
        }.getOrNull() else null
        val root = if (readableRoot != null && readableRoot.text().length > (semanticRoot?.text()?.length ?: 0)) readableRoot else semanticRoot
        val extracted = if (root != null) blocks(root, imageBase, "article-body") else {
            val text = json.firstNotNullOfOrNull { it.get("articleBody")?.takeIf { p -> p.isJsonPrimitive }?.asString }
            if (text != null && text.length >= 160) fragment(text, url, "article-body") else ExtractedContent(state = "unavailable")
        }
        val hasArticleIdentity = root != null || json.isNotEmpty() || doc.selectFirst("meta[property=og:type]")?.attr("content") == "article"
        val metadataImages = if (hasArticleIdentity) buildList {
            listOf("og:image", "twitter:image", "twitter:image:src").forEach { property ->
                doc.select("meta[property=\"$property\"], meta[name=\"$property\"]").forEach { meta ->
                    ImagePolicy.candidate(meta.attr("content"), imageBase, if (property.startsWith("og:")) "og:image" else "twitter:image")?.let { add(it) }
                }
            }
            json.forEach { article -> ArticleJsonLd.images(article).forEach { raw -> ImagePolicy.candidate(raw, imageBase, "json-ld")?.let { add(it) } } }
        } else emptyList()
        val images = ImagePolicy.ordered(metadataImages + extracted.images)
        return extracted.copy(images = images, state = if (extracted.text.length >= 160) "ready" else if (doc.select("script").isNotEmpty()) "javascript_or_unavailable" else "unavailable")
    }
    private fun clean(root: Element) {
        root.select("script, style, iframe, object, embed, form, input, button, nav, aside, footer, header, noscript, template, [hidden], [aria-hidden=true], [data-nosnippet], [data-component*=related], [data-testid*=recommend]").remove()
        root.select("*").filter { e -> noise.containsMatchIn(e.className()) || noise.containsMatchIn(e.id()) ||
            e.attr("style").replace(" ", "").lowercase().let { it.contains("display:none") || it.contains("visibility:hidden") } }.forEach { it.remove() }
    }
    private fun blocks(root: Element, base: String, origin: String): ExtractedContent {
        val result = mutableListOf<ArticleBlock>(); val images = mutableListOf<ArticleImage>()
        fun image(element: Element) {
            val candidate = ImagePolicy.fromElement(element, base, origin) ?: return
            val caption = element.closest("figure")?.selectFirst("figcaption")?.text().orEmpty().ifBlank { candidate.caption }
            val value = candidate.copy(caption = caption)
            if (images.none { ImagePolicy.identity(it.url) == ImagePolicy.identity(value.url) }) {
                images += value; result += ArticleBlock("image", url = value.url, caption = caption)
            }
        }
        fun normalized(value: String) = value.replace('\u00a0', ' ').replace(Regex("[\\t\\x0B\\f\\r ]+"), " ").trim()
        fun inline(element: Element, type: String, level: Int = 0) {
            val text = StringBuilder()
            fun flush() { normalized(text.toString()).takeIf { it.isNotBlank() }?.let { result += ArticleBlock(type, text = it, level = level) }; text.clear() }
            fun visit(node: org.jsoup.nodes.Node) {
                when (node) {
                    is TextNode -> text.append(node.wholeText)
                    is Element -> when (node.normalName()) {
                        "img" -> { flush(); image(node) }
                        "br" -> text.append('\n')
                        "ul", "ol" -> Unit
                        else -> node.childNodes().forEach(::visit)
                    }
                }
            }
            element.childNodes().forEach(::visit); flush()
        }
        fun walk(element: Element, depth: Int = 0) {
            when (element.normalName()) {
                "p" -> inline(element, "paragraph")
                "h1", "h2", "h3", "h4", "h5", "h6" -> inline(element, "heading", element.normalName().last().digitToInt())
                "blockquote" -> inline(element, "quote")
                "li" -> { inline(element, "list_item", depth); element.children().filter { it.normalName() in setOf("ul", "ol") }.forEach { walk(it, depth + 1) } }
                "img" -> image(element)
                "figcaption" -> Unit
                else -> {
                    val direct = StringBuilder()
                    fun flush() { normalized(direct.toString()).takeIf { it.isNotBlank() }?.let { result += ArticleBlock(text = it) }; direct.clear() }
                    element.childNodes().forEach { node -> if (node is TextNode) direct.append(node.wholeText) else if (node is Element) { flush(); walk(node, depth) } }
                    flush()
                }
            }
        }
        walk(root)
        return ExtractedContent(result.take(3000), images.take(60), "ready")
    }
}
