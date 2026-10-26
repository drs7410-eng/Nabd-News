package com.nabd.news

import org.jsoup.Jsoup
import org.jsoup.nodes.CDataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Source adapters share an inert normalizer; selector overrides never grant extraction rights. */
interface FeedSourceAdapter {
    val id: String
    val articleSelectors: List<String>
    fun matches(url: String): Boolean
}
object SourceAdapters {
    private data class Adapter(override val id: String, val host: String, override val articleSelectors: List<String>) : FeedSourceAdapter {
        override fun matches(url: String): Boolean = url.toHttpUrlOrNull()?.host?.let { it == host || it.endsWith(".$host") } == true
    }
    private val adapters = listOf(
        Adapter("bbc", "bbc.com", listOf("article")), Adapter("bbc-feed", "bbci.co.uk", listOf("article")),
        Adapter("aljazeera", "aljazeera.net", listOf(".wysiwyg", ".main-content")),
        Adapter("guardian", "theguardian.com", listOf(".article-body-commercial-selector", "[data-gu-name=body]")),
        Adapter("cnn", "cnn.com", listOf("#storytext", ".article__content", ".article-content", ".article-body")),
        Adapter("aawsat", "aawsat.com", listOf(".field--name-body", ".article-body", ".article-body-content")),
        Adapter("rt", "rt.com", listOf(".article__text", ".article__body")),
        Adapter("rtarabic", "rtarabic.com", listOf(".article__text", ".article__body")),
        Adapter("dw", "dw.com", listOf("[data-tracking-name=article-body]", ".longText", ".rich-text")),
        Adapter("cnbc-ar", "cnbcarabia.com", listOf(".article-content", ".article-body")))
    private val generic = object : FeedSourceAdapter {
        override val id = "rss-atom-generic"
        override val articleSelectors = emptyList<String>()
        override fun matches(url: String) = true
    }
    fun forUrl(url: String): FeedSourceAdapter = adapters.firstOrNull { it.matches(url) } ?: generic
}
class FeedParser(private val extractor: ArticleHtmlExtractor = ArticleHtmlExtractor()) {
    fun parse(bytes: ByteArray, source: Source): List<Article> {
        require(bytes.size <= 4_000_000) { "الخلاصة أكبر من الحد المسموح" }
        val doc = Jsoup.parse(bytes.inputStream(), null, source.url, Parser.xmlParser())
        require(doc.children().any { it.tagName().substringAfter(':').lowercase() in setOf("rss", "feed", "rdf") }) { "الاستجابة ليست RSS أو Atom" }
        val entries = doc.getAllElements().filter { it.tagName().substringAfter(':').lowercase() in setOf("item", "entry") }.take(200)
        return entries.mapNotNull { entry -> parseEntry(entry, source) }.distinctBy { ArticleIdentity.canonical(it.url) }
    }
    private fun parseEntry(entry: Element, source: Source): Article? {
        val atom = entry.tagName().substringAfter(':').equals("entry", true)
        fun children(name: String) = entry.children().filter { it.tagName().equals(name, true) }.ifEmpty { entry.children().filter { it.tagName().substringAfter(':').equals(name, true) && namespace(it) == "http://www.w3.org/2005/Atom" } }
        fun value(name: String) = children(name).firstOrNull()?.text().orEmpty()
        val title = Jsoup.parseBodyFragment(value("title")).text().take(1000)
        val link = if (atom) children("link").firstOrNull { it.attr("rel").ifBlank { "alternate" } == "alternate" }?.attr("href").orEmpty() else value("link")
        val url = source.url.toHttpUrlOrNull()?.resolve(link)?.takeIf { link.isNotBlank() && it.username.isBlank() && it.password.isBlank() }?.newBuilder()?.fragment(null)?.build()?.toString() ?: return null
        if (title.isBlank()) return null
        val summaryElement = children(if (atom) "summary" else "description").firstOrNull()
        val fullElement = entry.children().firstOrNull { element ->
            val tag = element.tagName().lowercase()
            tag == "content:encoded" || (tag.substringAfter(':') == "encoded" && namespace(element).contains("purl.org/rss/1.0/modules/content"))
        } ?: if (atom) children("content").firstOrNull { it.attr("src").isBlank() } else null
        val summaryContent = extractor.fragment(markup(summaryElement), url)
        val full = extractor.fragment(markup(fullElement), url)
        val summary = summaryContent.text.take(50000)
        val explicitlyTruncated = full.text.trim().let { it.endsWith("…") || it.endsWith("...") || Regex("(?i)(اقرأ المزيد|read more|continue reading)[.\\s]*$").containsMatchIn(it) }
        val hasPublisherContent = fullElement != null && full.text.isNotBlank() && !explicitlyTruncated
        val content = if (hasPublisherContent) full else summaryContent
        val media = mutableListOf<ArticleImage>()
        entry.getAllElements().filter { it !== entry }.forEach { element ->
            val local = element.tagName().substringAfter(':').lowercase()
            val ns = namespace(element)
            val isMedia = ns.contains("search.yahoo.com/mrss") || element.tagName().startsWith("media:")
            val origin = when {
                isMedia && local == "content" && element.attr("medium").ifBlank { "image" } == "image" && element.attr("type").let { it.isBlank() || it.startsWith("image/") } -> "media:content"
                isMedia && local == "thumbnail" -> "media:thumbnail"
                (local == "enclosure" || (atom && local == "link" && element.attr("rel") == "enclosure")) &&
                    (element.attr("type").startsWith("image/") || Regex("(?i)\\.(jpe?g|png|webp|avif)(?:\\?|$)").containsMatchIn(element.attr("url").ifBlank { element.attr("href") })) -> "enclosure"
                else -> null
            }
            if (origin != null) ImagePolicy.candidate(element.attr("url").ifBlank { element.attr("href") }, url, origin,
                element.attr("width").toIntOrNull() ?: 0, element.attr("height").toIntOrNull() ?: 0)?.let { media += it }
        }
        val images = ImagePolicy.ordered(media + full.images + summaryContent.images)
        val published = sequenceOf(value("pubDate"), value("published"), value("updated"), value("dc:date")).filter { it.isNotBlank() }.map(::date).firstOrNull { it > 0 } ?: 0
        return Article(id = AiPolicy.hash(url), title = title, body = summary.ifBlank { full.text.take(50000) }, url = url,
            source = source.name, category = source.category, published = published,
            image = images.firstOrNull()?.url.orEmpty(), summary = summary.ifBlank { if (!hasPublisherContent) full.text else "" },
            fullContent = if (hasPublisherContent) full.text.take(200000) else "",
            contentBlocks = ContentCodec.blocks(if (hasPublisherContent) full.blocks else emptyList()), images = ContentCodec.images(images),
            sourceItemId = value(if (atom) "id" else "guid").take(2000), sourceId = source.id,
            contentOrigin = if (hasPublisherContent) "rss_content" else "excerpt", detailState = if (hasPublisherContent) "ready" else "not_requested")
    }
    private fun namespace(element: Element): String {
        val prefix = element.tagName().substringBefore(':', "")
        val attribute = if (prefix.isBlank()) "xmlns" else "xmlns:$prefix"
        return (listOf(element) + element.parents()).firstOrNull { it.hasAttr(attribute) }?.attr(attribute).orEmpty()
    }
    private fun markup(element: Element?): String {
        if (element == null) return ""
        return element.childNodes().joinToString("") { node -> when (node) {
            is CDataNode -> node.wholeText
            is TextNode -> node.wholeText
            else -> node.outerHtml()
        } }
    }
    private fun date(value: String): Long = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)
}
