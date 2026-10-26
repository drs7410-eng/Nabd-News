package com.nabd.news

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

fun openOriginalArticle(context: Context, url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    try {
        CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build().launchUrl(context, Uri.parse(url))
    } catch (_: android.content.ActivityNotFoundException) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "ثبّت متصفحًا لفتح المصدر الأصلي", Toast.LENGTH_LONG).show() }
    }
}
@Composable fun NewsImage(url: String, description: String, caption: String = "") {
    val context = LocalContext.current
    var ratio by remember(url) { mutableFloatStateOf(16f / 9f) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubcomposeAsyncImage(model = ImageRequest.Builder(context).data(url).crossfade(true).build(), contentDescription = description,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(18.dp)),
            onSuccess = { success ->
                val drawable = success.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) ratio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
            },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(25.dp), color = Accent) } },
            error = { Surface(color = Panel, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BrokenImage, null, tint = Muted); Caption("الصورة غير متاحة من المصدر حاليًا")
            } } })
        if (caption.isNotBlank()) Caption(caption)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun ArticleScreen(vm: NabdViewModel, a: Article, assistant: () -> Unit, note: () -> Unit, chat: () -> Unit) {
    val history by vm.analyses.collectAsStateWithLifecycle()
    val relatedNotes by vm.notes.collectAsStateWithLifecycle()
    val loading by vm.loadingDetails.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val readingState = articleReadingState(a, a.id in loading)
    val savedSummary = history.filter { a.id in it.articleIds.split(',') && it.operation == "تلخيص قصير" }.maxByOrNull { it.created }
    var summaryExpanded by rememberSaveable(a.id) { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<Source?>(null) }
    configuring?.let { source -> SourceExtractionDialog(vm, source) { configuring = null; vm.loadDetails(a.id, true) } }
    var fontSize by rememberSaveable { mutableFloatStateOf(vm.settings.get("articleFontSize", "18").toFloatOrNull()?.coerceIn(16f, 26f) ?: 18f) }
    var fontDialog by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    var justified by rememberSaveable { mutableStateOf(vm.settings.get("articleJustified", "true").toBoolean()) }
    var leading by rememberSaveable { mutableFloatStateOf(vm.settings.get("articleLeading", "1.85").toFloatOrNull()?.coerceIn(1.5f, 2.2f) ?: 1.85f) }
    val readingMinutes = remember(a.contentBlocks, a.fullContent) {
        ((ContentCodec.displayBlocks(a).sumOf { it.text.split(Regex("\\s+")).count { word -> word.isNotBlank() } } + 179) / 180).coerceAtLeast(1)
    }
    val context = LocalContext.current
    var showOriginal by rememberSaveable(a.id) { mutableStateOf(false) }
    if (showOriginal) OriginalArticleReader(a.url, a.source) { showOriginal = false }
    val main = remember(a.images, a.image) { ContentCodec.readImages(a.images).firstOrNull { it.url == a.image } }
    val blocks = remember(a.contentBlocks, a.fullContent, a.summary, a.body, a.image) { ContentCodec.displayBlocks(a) }
    LaunchedEffect(a.id) { vm.loadDetails(a.id) }
    if (fontDialog) AlertDialog(onDismissRequest = { fontDialog = false }, title = { Text("راحة القراءة") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("نص واضح وفقرات مريحة لقراءة المقال بهدوء.", fontSize = fontSize.sp, lineHeight = (fontSize * leading).sp)
            Text("حجم الخط: ${fontSize.toInt()}")
            Slider(fontSize, { fontSize = it; vm.settings.set("articleFontSize", it.toString()) }, valueRange = 16f..26f, steps = 9)
            Text("تباعد السطور")
            Slider(leading, { leading = it; vm.settings.set("articleLeading", it.toString()) }, valueRange = 1.5f..2.2f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ضبط النص على الجانبين")
                Switch(justified, { justified = it; vm.settings.set("articleJustified", it.toString()) })
            }
        }
    }, confirmButton = { TextButton(onClick = { fontDialog = false }) { Text("تم") } })
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item(key = "title") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption("${a.category} • ${a.source}", Mint); Spacer(Modifier.height(10.dp))
            SelectionContainer { Text(a.title, fontSize = 29.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, style = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrRtl)) }
            Spacer(Modifier.height(12.dp))
            Caption("${dateText(a.published)} • $readingMinutes دقائق قراءة تقديريًا")
            }
        }
        item(key = "hero") {
            if (a.image.isNotBlank()) NewsImage(a.image, "صورة أصلية للخبر: ${a.title}", main?.caption.orEmpty())
            else Surface(shape = RoundedCornerShape(20.dp), color = Panel) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Article, null, tint = Accent, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Caption("${a.category} • لا تتوفر صورة أصلية مناسبة")
                }
            }
        }
        stickyHeader(key = "actions") {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 3.dp, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.toggleSaved(a) }) { Icon(if (a.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (a.saved) "إلغاء حفظ الخبر" else "حفظ الخبر", tint = Accent) }
                    IconButton(onClick = { fontDialog = true }) { Icon(Icons.Outlined.TextFields, "تنسيق وراحة القراءة") }
                    IconButton(onClick = assistant) { Icon(Icons.Outlined.AutoAwesome, "مساعد نبض الذكي", tint = Mint) }
                    IconButton(onClick = note) { Icon(Icons.Outlined.EditNote, "إضافة تدوينة") }
                    Box {
                        IconButton(onClick = { moreActions = true }) { Icon(Icons.Outlined.MoreVert, "خيارات المقال") }
                        DropdownMenu(expanded = moreActions, onDismissRequest = { moreActions = false }) {
                            DropdownMenuItem(text = { Text("مشاركة الخبر") }, onClick = {
                                moreActions = false
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${a.title}\n${a.url}") }, "مشاركة الخبر"))
                            })
                            DropdownMenuItem(text = { Text("فتح المصدر الأصلي") }, onClick = { moreActions = false; showOriginal = true })
                            DropdownMenuItem(text = { Text("تحديث نص المقال") }, enabled = a.id !in loading, onClick = { moreActions = false; vm.loadDetails(a.id, true) })
                        }
                    }
                }
            }
        }
        if (a.summary.isNotBlank() || savedSummary != null) item(key = "source-summary") {
            BoxCard {
                Caption(if (savedSummary != null) "الخلاصة • ملخص ذكي محفوظ" else "في سطور • وصف المصدر", Mint)
                SelectionContainer {
                    Text(savedSummary?.result ?: a.summary, maxLines = if (summaryExpanded) Int.MAX_VALUE else 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = (fontSize - 1).sp, lineHeight = (fontSize * 1.7f).sp,
                        textAlign = if (justified) TextAlign.Justify else TextAlign.Start,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrRtl))
                }
                Caption(if (savedSummary != null) "${savedSummary.provider} • قد يخطئ الملخص؛ النص الأصلي أدناه هو المرجع." else "وصف مختصر من المصدر؛ ليس خلاصة مولّدة لكامل المقال.")
                TextButton(onClick = { summaryExpanded = !summaryExpanded }) { Text(if (summaryExpanded) "طي الخلاصة" else "عرض الخلاصة كاملة") }
                TextButton(onClick = assistant) { Text("إنشاء خلاصة ذكية باختياري") }
            }
        }
        item(key = "availability") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (a.id in loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Caption("جارٍ تحميل نص المقال من المصدر لعرضه داخل نبض…") }
            Caption(when(readingState) {
                ArticleReadingState.FETCHING_ARTICLE -> "جارٍ تحميل المقال"
                ArticleReadingState.FULL_ARTICLE -> "متن المقال محفوظ داخل نبض"
                ArticleReadingState.PARTIAL_ARTICLE -> "المحتوى المتاح جزئي أو غير مؤكد الاكتمال"
                ArticleReadingState.EXTRACTION_FAILED -> "فشل استخراج متن المقال"
                ArticleReadingState.ACCESS_RESTRICTED -> "الاستخراج غير مفعّل أو الوصول مقيّد"
            }, if (readingState == ArticleReadingState.FULL_ARTICLE) Mint else Accent)
            if (a.isExcerpt || readingState !in setOf(ArticleReadingState.FULL_ARTICLE, ArticleReadingState.FETCHING_ARTICLE)) BoxCard {
                Text(if (a.detailState == "partial") "هذا جزء من متن المصدر؛ تعذر تأكيد اكتماله."
                    else if (!a.isExcerpt) "تُعرض نسخة سابقة محفوظة؛ تعذر تأكيد تحديثها بمحرك القراءة الحالي."
                    else "هذا مقتطف من الخبر. يمكنك قراءة التفاصيل الكاملة من المصدر الأصلي", color = Accent, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                Caption(a.detailReason.ifBlank { when (a.detailState) {
                    "permission_required" -> "استخراج صفحة هذا المصدر غير مفعّل؛ يمكن إعداده من إدارة المصادر عندما تسمح شروطه بذلك."
                    "robots_denied" -> "لم يسمح فحص قواعد المصدر باستخراج الصفحة."
                    "restricted", "blocked" -> "المصدر يحجب الاستخراج أو يقيّد الوصول. يمكنك زيارته مباشرة."
                    "javascript_or_unavailable" -> "قد تتطلب الصفحة JavaScript، أو لا يتوفر فيها نص مقال قابل للاستخراج."
                    "identity_mismatch" -> "تعذر التأكد من تطابق الصفحة مع هذا الخبر؛ لم يُستخدم محتواها."
                    "unavailable" -> "تعذر استخراج تفاصيل إضافية. بقي النص الأصلي المحفوظ متاحًا."
                    else -> "المتاح حاليًا هو نص الخلاصة؛ وجود حقل content لا يثبت اكتمال المقال."
                } })
                if (a.detailState == "permission_required") sources.firstOrNull { it.id == a.sourceId || it.name == a.source }?.let { source ->
                    TextButton(onClick = { configuring = source }) { Text("إعداد استخراج المقال من المصدر") }
                }
            } else Caption(when(a.contentOrigin) {
                "rss_content" -> "محتوى المصدر المنشور عبر RSS • ليس نصًا مولدًا بالذكاء الاصطناعي"
                "manual" -> "نص أصلي أضفته يدويًا"
                else -> "محتوى مستخرج من صفحة المصدر • محفوظ محليًا"
            }, Mint)
            if (readingState !in setOf(ArticleReadingState.FULL_ARTICLE, ArticleReadingState.FETCHING_ARTICLE)) {
                TextButton(onClick = { vm.loadDetails(a.id, true) }, enabled = a.id !in loading) { Text("إعادة محاولة تحميل متن المقال") }
            }
            if (readingState != ArticleReadingState.FULL_ARTICLE) OutlinedButton(onClick = { showOriginal = true }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(9.dp)); Text("فتح صفحة المصدر يدويًا")
            }
            }
        }
        item(key = "body-label") { Caption("نص المقال الأصلي", Mint) }
        itemsIndexed(blocks, key = { index, _ -> "block-$index" }) { _, block ->
            if (block.type == "image") {
                if (ImagePolicy.resolve(block.url, a.url) != null) NewsImage(block.url, block.caption.ifBlank { "صورة داخل المقال" }, block.caption)
            } else SelectionContainer {
                Text(linkedArticleText(block) { openOriginalArticle(context, it) },
                    modifier = Modifier.fillMaxWidth().padding(start = if (block.type == "list_item") (block.level.coerceIn(0, 4) * 12).dp else 0.dp),
                    fontSize = (fontSize + if (block.type == "heading") 4 else 0).sp, lineHeight = (fontSize * leading).sp,
                    textAlign = if (justified && block.type !in setOf("heading", "list_item", "quote")) TextAlign.Justify else TextAlign.Start,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrRtl),
                    fontWeight = if (block.type == "heading") FontWeight.Bold else FontWeight.Normal,
                    color = if (block.type == "quote") Mint else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (blocks.isEmpty()) item { Caption("لا يتوفر نص في الخلاصة. يمكنك قراءة الخبر من المصدر الأصلي.") }
        item(key = "source") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkButton(a.url, "${a.source} • المصدر الأصلي")
            TextButton(onClick = { vm.loadDetails(a.id, true) }, enabled = a.id !in loading) { Text("إعادة التحقق من تفاصيل الخبر") }
            if (a.detailFetchedAt > 0) Caption("آخر محاولة جلب: ${dateText(a.detailFetchedAt)}")
            }
        }
        item(key = "ai") {
            Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(); Spacer(Modifier.height(16.dp)); BoxCard {
                SectionTitle("مساحة مساعد نبض", "قسم مستقل للمحتوى المولّد؛ لا يغيّر النص الأصلي ولا يكمل مقتطف الخبر.")
                Button(onClick = assistant, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(9.dp)); Text("التلخيص والتحليل وفحص الادعاءات") }
                Action("محادثة حول هذا الخبر", Icons.Outlined.ChatBubbleOutline, chat)
            }
            }
        }
        history.filter { a.id in it.articleIds.split(',') }.forEach { h -> item(key = "analysis-${h.id}") {
            BoxCard { Caption("${h.provider} • ${dateText(h.created)}"); Text(h.operation, fontWeight = FontWeight.Bold); Caption(h.status); TextButton(onClick = { vm.result.value = h }) { Text("فتح التحليل المحفوظ") } }
        } }
        if (relatedNotes.any { a.id in it.articleIds.split(',') }) item(key = "notes") { SectionTitle("ملاحظاتك على الخبر"); relatedNotes.filter { a.id in it.articleIds.split(',') }.forEach { Caption(it.title.ifBlank { it.body.take(90) }) } }
    }
}


/** Only an explicit tap on a safe source link can open a browser. Original text stays selectable. */
@Composable internal fun linkedArticleText(block: ArticleBlock, open: (String) -> Unit) = buildAnnotatedString {
    val prefix = if (block.type == "list_item") "• " else ""
    append(prefix)
    var cursor = 0
    block.links.orEmpty().sortedBy { it.start }.forEachIndexed { index, link ->
        val parsed = link.url.toHttpUrlOrNull()
        if (link.start >= cursor && link.end > link.start && link.end <= block.text.length &&
            parsed != null && parsed.username.isBlank() && parsed.password.isBlank() && parsed.scheme in setOf("http", "https")) {
            append(block.text.substring(cursor, link.start))
            withLink(LinkAnnotation.Clickable("article-link-$index", TextLinkStyles(SpanStyle(color = Mint, textDecoration = TextDecoration.Underline))) { open(parsed.toString()) }) {
                append(block.text.substring(link.start, link.end))
            }
            cursor = link.end
        }
    }
    append(block.text.substring(cursor))
}
