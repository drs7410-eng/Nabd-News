package com.nabd.news

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun SourcesScreen(vm: NabdViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val quality by vm.feedQuality.collectAsStateWithLifecycle()
    val checking by vm.checkingFeeds.collectAsStateWithLifecycle()
    var sourceTab by rememberSaveable { mutableStateOf("الدليل") }
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var catalogCategory by rememberSaveable { mutableStateOf("الكل") }
    var activating by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("عام") }
    var type by rememberSaveable { mutableStateOf("RSS") }
    var articleTitle by rememberSaveable { mutableStateOf("") }
    var articleBody by rememberSaveable { mutableStateOf("") }
    var articleUrl by rememberSaveable { mutableStateOf("") }
    var articleSource by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("خبر") }
    var deleting by remember { mutableStateOf<Source?>(null) }
    var configuring by remember { mutableStateOf<Source?>(null) }
    if (configuring != null) SourceExtractionDialog(vm, configuring!!) { configuring = null }
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف المصدر؟") }, text = { Text("ستبقى الأخبار المحفوظة منه على الجهاز.") }, confirmButton = { TextButton(onClick = { val id = deleting!!.id; deleting = null; vm.task { vm.dao.deleteSource(id) } }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ChoiceRow(listOf("الدليل", "المفعّلة", "إضافة يدوية"), sourceTab) { sourceTab = it } }
        if (sourceTab == "الدليل") {
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { SectionTitle("دليل المصادر العربية", "اختيار واحد يضبط الرابط والتصنيف ويفحص RSS ويحمّل الأخبار. لا يُضاف مصدر معطّل، ولا تعني الخلاصة ضمان المقال الكامل.")
            SearchField(catalogQuery, { catalogQuery = it }, "ابحث باسم الصحيفة أو القناة")
            ChoiceRow(listOf("الكل") + SourceCatalog.entries.map { it.category }.distinct(), catalogCategory) { catalogCategory = it }
        } }
        items(SourceCatalog.entries.filter { it.name.contains(catalogQuery, true) && (catalogCategory == "الكل" || it.category == catalogCategory) }, key = { "catalog-${it.id}" }) { entry -> BoxCard {
            Text(entry.name); Caption("العربية • ${entry.category}", Mint); Caption(entry.status)
            if (entry.feed != null) {
                val exists = sources.any { it.url.trimEnd('/') == entry.feed.trimEnd('/') || it.id == entry.id }
                val isActivating = entry.id in activating
                Button(onClick = {
                    activating = activating + entry.id
                    vm.task {
                        try { vm.news.activate(entry); vm.notices.emit("تم ضبط ${entry.name} وفحص الخلاصة وتحميل الأخبار") }
                        finally { activating = activating - entry.id }
                    }
                }, enabled = !exists && !isActivating) { Text(if (exists) "مفعّل" else if (isActivating) "جارٍ الفحص والتفعيل…" else "اختيار وتفعيل المصدر") }
                LinkButton(entry.feed, "رابط RSS الرسمي أو المرشح")
            } else Caption("لا توجد خلاصة عربية عامة مؤكدة؛ لا تفعيل برابط وهمي", Accent)
            LinkButton(entry.reference, "مرجع المصدر")
        } }
        }
        if (sourceTab == "إضافة يدوية") {
        item { BoxCard {
            SectionTitle("مصادرك، باختيارك", "يُحدّث RSS عند الضغط فقط. الحسابات على X تفتح في تطبيق المنصة أو المتصفح.")
            ChoiceRow(listOf("RSS", "X"), type) { type = it }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المصدر") })
            OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), label = { Text(if (type == "RSS") "رابط RSS / Atom عبر HTTPS" else "رابط الحساب على X") })
            OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("التصنيف") })
            Button(onClick = { vm.task {
                val parsed = url.toHttpUrlOrNull()
                require(name.isNotBlank() && parsed != null && parsed.isHttps && parsed.username.isBlank() && parsed.password.isBlank()) { "أدخل اسمًا ورابط HTTPS صالحًا دون بيانات اعتماد" }
                if (type == "X") require(parsed.host in listOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")) { "أدخل رابط حساب رسمي على X" }
                vm.dao.saveSource(Source(name = name.trim(), url = parsed.toString(), category = category.ifBlank { "عام" }, type = type)); name = ""; url = ""; vm.notices.emit("حُفظ المصدر")
            } }) { Text("إضافة المصدر") }
            if (type == "X") Caption("تظهر الحسابات والمنشورات العامة في قسم «شاهد وتابع ← متابعة X» عبر التضمين الرسمي داخل نبض. توفر العرض يخضع لقيود X، ولا يُدّعى أنها RSS أو أرشيف محلي.", Accent)
            Action("تحديث خلاصات RSS", Icons.Outlined.Refresh) { vm.refresh() }
        } }
        }
        if (sourceTab == "المفعّلة") {
        item { BoxCard {
            SectionTitle("قراءة المقال", "عند فتح الخبر يُحمّل النص المسموح به من صفحة الصحيفة ويُعرض كفقرات وصور داخل تنسيق نبض، دون فتح الموقع تلقائيًا. إذا تعذر الاستخراج يبقى المقتطف مع بيان السبب.")
        } }
        items(sources, key = { it.id }) { s -> BoxCard {
            Text(s.name); Caption("${s.type} • ${s.category}", Mint); LinkButton(s.url, s.url)
            if (s.type == "RSS") {
                val audit = quality.firstOrNull { it.sourceId == s.id && it.feedUrl == s.url }
                SectionTitle("جودة RSS")
                if (audit == null) Caption("لم تُفحص بعد؛ لا نفترض توفر المقال الكامل أو الصور") else {
                    Caption(audit.contentLabel, if (audit.status == "ok") Mint else Accent)
                    if (audit.status == "ok") {
                        Caption("الصور في الخلاصة: ${if (audit.imageCount > 0) "نعم" else "لم توجد في العينة"} • ${audit.imageCount}/${audit.sampleSize} مادة")
                        Caption("أحدث خبر منشور: ${if (audit.latestPublished > 0) dateText(audit.latestPublished) else "لا يوجد تاريخ صالح في الخلاصة"}")
                        if (audit.latestPublished > 0 && audit.latestPublished < System.currentTimeMillis() - 7 * 86_400_000L) Caption("قد تكون الخلاصة متوقفة: أحدث مادة أقدم من أسبوع", Accent)
                        Caption("حجم العينة: ${audit.sampleSize} • رمز HTTP: ${audit.httpCode}")
                    } else Caption(audit.error, Accent)
                    Caption("آخر فحص: ${dateText(audit.checkedAt)}")
                }
                TextButton(onClick = { vm.checkFeed(s) }, enabled = s.id !in checking) { Text(if (s.id in checking) "جارٍ فحص الخلاصة…" else "فحص جودة RSS") }
                Caption(if (s.htmlAllowed) "استخراج الصفحة مفعّل وفق الإذن المسجل" else "استخراج الصفحة يحتاج ضبط الإذن؛ قراءة الصفحة الأصلية متاحة")
                TextButton(onClick = { configuring = s }) { Text("إعداد استخراج المقال") }
            }
            TextButton(onClick = { deleting = s }) { Text("حذف المصدر") }
        } }
        }
        if (sourceTab == "إضافة يدوية") {
        item { BoxCard {
            SectionTitle("إضافة مادة يدويًا", "لخبر أو منشور تملك حق استخدامه. يُحفظ محليًا ولا يُرسل إلى AI تلقائيًا.")
            OutlinedTextField(articleTitle, { articleTitle = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("العنوان") })
            OutlinedTextField(articleBody, { articleBody = it.take(50000) }, Modifier.fillMaxWidth(), label = { Text("النص المتاح لك") }, minLines = 5)
            OutlinedTextField(articleUrl, { articleUrl = it.trim() }, Modifier.fillMaxWidth(), label = { Text("رابط المصدر الأصلي HTTPS") })
            OutlinedTextField(articleSource, { articleSource = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("اسم المصدر / الحساب") })
            ChoiceRow(listOf("خبر", "رأي", "تحليل", "منشور X"), kind) { kind = it }
            Button(onClick = { vm.task {
                val parsed = articleUrl.toHttpUrlOrNull()
                require(articleTitle.isNotBlank() && articleBody.isNotBlank() && articleSource.isNotBlank() && parsed != null && parsed.isHttps && parsed.username.isBlank() && parsed.password.isBlank()) { "أكمل العنوان والنص والمصدر وأدخل رابط HTTPS صالحًا" }
                val canonical = parsed.toString().substringBefore('#')
                val content = ArticleHtmlExtractor().fragment(articleBody, canonical)
                vm.dao.addArticles(listOf(Article(id = AiPolicy.hash(canonical), title = articleTitle, body = content.text, fullContent = content.text,
                    contentBlocks = ContentCodec.blocks(content.blocks), images = ContentCodec.images(content.images), image = content.images.firstOrNull()?.url.orEmpty(),
                    contentOrigin = "manual", detailState = "ready", url = canonical, source = articleSource, category = category.ifBlank { "عام" }, kind = kind, saved = true)))
                articleTitle = ""; articleBody = ""; articleUrl = ""; vm.notices.emit("حُفظت المادة في الأخبار والمحـفوظات")
            } }) { Text("حفظ المادة") }
        } }
        }
    }
}

@Composable internal fun SourceExtractionDialog(vm: NabdViewModel, source: Source, close: () -> Unit) {
    var allowed by remember(source.id) { mutableStateOf(source.htmlAllowed) }
    var terms by remember(source.id) { mutableStateOf(source.termsUrl) }
    AlertDialog(onDismissRequest = close, title = { Text("استخراج المقال • ${source.name}") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("فعّل هذا الخيار فقط إذا سمحت شروط المصدر أو كان لديك إذن باستخدام محتواه. عدم المنع في robots.txt لا يمنح ترخيصًا.")
            Row { Checkbox(allowed, { allowed = it }); Text("راجعت الشروط ولدي إذن باستخراج النص والصور المتاحة علنًا") }
            OutlinedTextField(terms, { terms = it.trim() }, label = { Text("رابط الشروط أو الترخيص HTTPS") }, modifier = Modifier.fillMaxWidth())
            LinkButton(source.url, "زيارة المصدر ومراجعة شروطه")
            Caption("عند فتح خبر يُفحص robots.txt قبل الجلب. لا تُتجاوز الاشتراكات أو تسجيل الدخول أو حجب الموقع، ولا تُنفذ JavaScript للاستخراج. عند عدم توفر النص يبقى الخبر داخل واجهة نبض مع سبب التعذر. فتح صفحة المصدر خيار يدوي فقط.")
        }
    }, confirmButton = { TextButton(onClick = { vm.task {
        require(!allowed || terms.toHttpUrlOrNull()?.isHttps == true) { "أضف رابط الشروط أو الترخيص أولًا" }
        vm.dao.updateSource(source.copy(htmlAllowed = allowed, termsUrl = terms)); close()
        vm.notices.emit("حُفظ إعداد المصدر؛ افتح الخبر أو أعد التحقق من تفاصيله")
    } }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } })
}
