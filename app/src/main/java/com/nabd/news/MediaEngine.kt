package com.nabd.news

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "media_clips")
data class MediaClip(@PrimaryKey val id: String, val title: String, val url: String, val playUrl: String,
    val provider: String, val sourceId: String, val source: String, val topic: String, val published: Long,
    val thumbnail: String = "", val liked: Boolean = false, val hidden: Boolean = false, val opened: Long = 0,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val visibleMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val impressions: Int = 0,
    @ColumnInfo(defaultValue = "0") val skips: Int = 0)

data class MediaTarget(val provider: String, val url: String)
object MediaPolicy {
    val topics = listOf("تقنية", "أخبار", "مشاهير", "حروب", "الترند", "الخليج", "العالم")
    fun target(value: String): MediaTarget? {
        val u = value.toHttpUrlOrNull() ?: return null
        if (!u.isHttps || u.username.isNotBlank() || u.password.isNotBlank()) return null
        val h = u.host.removePrefix("www.").removePrefix("m.")
        val p = u.pathSegments
        val youtube = when {
            h == "youtu.be" -> p.firstOrNull()
            h == "youtube.com" && p.firstOrNull() == "watch" -> u.queryParameter("v")
            h == "youtube.com" && p.firstOrNull() in listOf("shorts", "embed", "live") -> p.getOrNull(1)
            else -> null
        }
        if (youtube != null && Regex("[A-Za-z0-9_-]{11}").matches(youtube)) return MediaTarget("YouTube", "https://www.youtube.com/embed/$youtube?playsinline=1&autoplay=0")
        if (h in setOf("vimeo.com", "player.vimeo.com") && p.lastOrNull()?.matches(Regex("[0-9]+")) == true) return MediaTarget("Vimeo", "https://player.vimeo.com/video/${p.last()}")
        if (h in setOf("x.com", "twitter.com") && p.size >= 3 && p[1] == "status" && p[2].matches(Regex("[0-9]+"))) return MediaTarget("X", "https://x.com/${p[0]}/status/${p[2]}")
        if (h == "instagram.com" && p.firstOrNull() in listOf("p", "reel") && p.getOrNull(1)?.matches(Regex("[A-Za-z0-9_-]+")) == true) return MediaTarget("Instagram", "https://www.instagram.com/${p[0]}/${p[1]}/embed/")
        val tikId = if(h=="tiktok.com" && p.size>=3 && p[1]=="video") p[2] else if(h=="tiktok.com" && p.size>=3 && p[0]=="player" && p[1]=="v1") p[2] else null
        if(tikId?.matches(Regex("[0-9]{10,24}"))==true) return MediaTarget("TikTok", "https://www.tiktok.com/player/v1/$tikId?autoplay=0&controls=1")
        if (u.encodedPath.lowercase().endsWith(".mp4") || u.encodedPath.lowercase().endsWith(".m3u8")) return MediaTarget("فيديو مباشر", u.toString())
        return null
    }
    fun validPlayer(t: MediaTarget): Boolean = if(t.provider=="X") xLink(t.url)!=null else target(t.url)?.provider==t.provider
    fun inferTopic(title:String, fallback:String):String = when {
        listOf("تقنية","ذكاء اصطناعي","هاتف","آيفون","ايفون","سامسونج","روبوت","OpenAI").any{title.contains(it,true)} -> "تقنية"
        listOf("فنان","ممثلة","مشاهير","مغني","ممثّل").any{title.contains(it)} -> "مشاهير"
        listOf("حرب","غارة","صاروخ","قصف","اشتباك").any{title.contains(it)} -> "حروب"
        fallback in topics -> fallback
        else -> "أخبار"
    }
    fun xLink(value: String): String? {
        val u = value.toHttpUrlOrNull() ?: return null
        if (!u.isHttps || u.username.isNotBlank() || u.password.isNotBlank() || u.host.removePrefix("www.") !in setOf("x.com", "twitter.com")) return null
        val p = u.pathSegments.filter { it.isNotBlank() }
        if (p.size == 1 && p[0].matches(Regex("[A-Za-z0-9_]{1,15}")) && p[0].lowercase() !in setOf("home","explore","search","settings","i","intent")) return "https://x.com/${p[0]}"
        return target(value)?.takeIf { it.provider == "X" }?.url
    }
    fun embedHtml(target: MediaTarget): String {
        require(target.provider == "X" && xLink(target.url) != null)
        val canonical = requireNotNull(xLink(target.url))
        val post = canonical.contains("/status/")
        return """<!doctype html><html dir="rtl"><meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:8px;background:#101419;color:white"><${if(post) "blockquote" else "div"} class="${if(post) "twitter-tweet" else "nabd-timeline"}" data-theme="dark" data-dnt="true"><a ${if(post) "" else "class=\"twitter-timeline\" data-theme=\"dark\" data-dnt=\"true\""} href="${canonical}">عرض المحتوى العام على X</a></${if(post) "blockquote" else "div"}><script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script></body></html>"""
    }
    fun rank(clips: List<MediaClip>, interests: Set<String>, region: String, keywords: String, personalized: Boolean, now: Long): List<MediaClip> {
        val words = keywords.split(',', '،').map { it.trim() }.filter { it.isNotBlank() }.take(20)
        val affinity = clips.filter { !it.hidden && it.liked }.groupingBy { it.topic }.eachCount()
        val scored = clips.filter { !it.hidden }.sortedWith(compareByDescending<MediaClip> { c ->
            if (!personalized) c.published.toDouble() else {
                val text = "${c.title} ${c.topic}"
                val interest = if (c.topic in interests || interests.any { text.contains(it, true) }) 12 else 0
                val regionScore = if (region == "الخليج" && listOf("السعودية","الإمارات","قطر","الكويت","البحرين","عمان","الخليج").any { text.contains(it) }) 6 else 0
                interest + regionScore + words.count { text.contains(it, true) } * 3 + (affinity[c.topic] ?: 0).coerceAtMost(5) * 2 +
                    (if(c.visibleMillis >= 15000 && c.impressions > 0) 2 else 0) - c.skips.coerceAtMost(4) * 3 -
                    (if (c.published > 0) ((now-c.published).coerceAtLeast(0) / 86_400_000.0).coerceAtMost(30.0) else 30.0) - if(c.opened > 0) 3 else 0
            }
        }.thenByDescending { it.published }.thenBy { it.id })
        if(!personalized) return scored
        // Diversify adjacent sources, then reserve one in every six positions for an unseen topic.
        val remaining = scored.toMutableList(); val result = mutableListOf<MediaClip>()
        while(remaining.isNotEmpty()) {
            val previous = result.lastOrNull()?.sourceId
            val exploration = result.size % 6 == 5
            val index = remaining.indexOfFirst { it.sourceId != previous && (!exploration || (it.impressions == 0 && it.topic !in interests)) }
                .takeIf { it >= 0 } ?: remaining.indexOfFirst { it.sourceId != previous }.takeIf { it >= 0 } ?: 0
            result += remaining.removeAt(index)
        }
        return result
    }
}
class MediaRepository(private val dao: NabdDao, client: okhttp3.OkHttpClient) {
    private val http = NewsHttp(client.newBuilder().followRedirects(false).followSslRedirects(false).build())
    fun parse(bytes: ByteArray, source: Source): List<MediaClip> {
        require(bytes.size <= 4_000_000)
        val doc = Jsoup.parse(bytes.inputStream(), null, source.url, Parser.xmlParser())
        require(doc.getAllElements().any { it.tagName().lowercase() in setOf("rss","feed","rdf:rdf") }) { "ليست خلاصة RSS/Atom" }
        return doc.getAllElements().filter { it.tagName().lowercase() in setOf("item","entry") }.take(100).mapNotNull { e ->
            val links = e.getElementsByTag("link")
            val link = links.firstOrNull { it.attr("rel") in setOf("alternate", "") }?.let { it.attr("href").ifBlank { it.text() } }.orEmpty()
            val candidates = e.getAllElements().filter { it.tagName().lowercase() in setOf("enclosure","media:content","media:player") }.map { it.attr("url") } + link
            val target = candidates.firstNotNullOfOrNull { MediaPolicy.target(it) } ?: return@mapNotNull null
            val title = e.getElementsByTag("title").firstOrNull()?.text().orEmpty().take(1000)
            if (title.isBlank()) return@mapNotNull null
            val published = e.getAllElements().firstOrNull { it.tagName() in setOf("published","pubDate","updated") }?.text().orEmpty()
            val time = runCatching { java.time.Instant.parse(published).toEpochMilli() }.getOrElse { runCatching { java.time.ZonedDateTime.parse(published, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L) }
            val thumb = e.getAllElements().firstOrNull { it.tagName() == "media:thumbnail" }?.attr("url").orEmpty().let { ImagePolicy.resolve(it, source.url).orEmpty() }
            MediaClip(AiPolicy.hash(target.url), title, link.takeIf { ReaderNavigation.allowed(it) } ?: target.url, target.url, target.provider, source.id, source.name, MediaPolicy.inferTopic(title,source.category), time, thumb)
        }.distinctBy { it.id }
    }
    suspend fun addSource(name: String, url: String, topic: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && topic in MediaPolicy.topics)
        var feed = url.trim()
        val parsed = feed.toHttpUrlOrNull() ?: error("أدخل رابط قناة أو RSS صالحًا")
        require(parsed.isHttps && parsed.username.isBlank() && parsed.password.isBlank())
        if (parsed.host.removePrefix("www.") == "youtube.com" && !parsed.encodedPath.startsWith("/feeds/")) {
            val id = parsed.pathSegments.takeIf { it.firstOrNull() == "channel" }?.getOrNull(1)
            feed = if (id != null && id.matches(Regex("UC[A-Za-z0-9_-]{22}"))) "https://www.youtube.com/feeds/videos.xml?channel_id=$id" else {
                val page = http.get(feed); require(page.code == 200) { "تعذر قراءة رابط القناة" }
                val discovered = Jsoup.parse(page.body, feed).select("link[rel=alternate]").firstOrNull { it.attr("type") == "application/rss+xml" }?.absUrl("href") ?: error("لم تعلن القناة خلاصة عامة؛ أضف رابط RSS المنشور منها")
                require(PublisherScope.samePublisher(feed, discovered)); discovered
            }
        }
        val existing = dao.allSources().firstOrNull { it.type == "VIDEO" && it.url == feed }
        val source = existing ?: Source(id = "video-${AiPolicy.hash(feed)}", name = name.trim(), url = feed, category = topic, type = "VIDEO")
        val response = http.get(feed); require(response.code == 200) { "تعذر تحميل خلاصة الفيديو • HTTP ${response.code}" }
        val clips = parse(response.bytes, source); require(clips.isNotEmpty()) { "لم تتوفر روابط فيديو مدعومة في الخلاصة؛ لم يُضف مصدر فارغ" }
        dao.installMediaSource(source, clips)
    }
    suspend fun addClip(value:String,title:String,topic:String) {
        val target=MediaPolicy.target(value) ?: error("هذا الرابط لا يوفّر مشغلًا مدعومًا. استخدم رابط المقطع الأصلي العام بدل الرابط المختصر.")
        require(topic in MediaPolicy.topics)
        dao.mergeClips(listOf(MediaClip(AiPolicy.hash(target.url),title.trim().ifBlank{"مقطع أضفته"}.take(1000),value,target.url,target.provider,"manual-links","روابط أضفتها",topic,System.currentTimeMillis())))
    }
    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        var count = 0; var failed = 0
        // Also discover publisher-declared videos in already enabled news RSS.
        dao.allSources().filter { it.type in setOf("VIDEO", "RSS") }.forEach { s ->
            try {
                val response = http.get(s.url); require(response.code == 200)
                val clips = parse(response.bytes, s); dao.mergeClips(clips); count += clips.size
            } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failed++ }
        }
        "تمت قراءة $count مقطعًا" + if(failed > 0) "؛ تعذر فحص $failed مصدر. بقيت المقاطع المحفوظة." else ""
    }
}
