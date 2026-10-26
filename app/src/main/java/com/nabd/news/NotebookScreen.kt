package com.nabd.news

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable fun NotebookScreen(vm: NabdViewModel, notes: List<Note>, open: (Note) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    val categories = listOf("الكل") + notes.map { it.category }.distinct()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BoxCard { Caption("أرشيفك المعرفي الخاص", Mint); Text("فكرة اليوم،\nمرجعك غدًا.", fontSize = 27.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold); Caption("تدويناتك تبقى على جهازك. لا تُرسل إلى المحركات إلا باختيار صريح."); Button(onClick = { val n = Note(); vm.saveNote(n); open(n) }) { Icon(Icons.Outlined.Add, null); Text("تدوينة جديدة") } } }
        item { SearchField(query, { query = it }, "ابحث في تدويناتك ووسومك…") }
        item { Picker("التصنيف", categories, category) { category = it }; FilterChip(pinnedOnly, { pinnedOnly = !pinnedOnly }, label = { Text("المثبّتة فقط") }) }
        if (notes.isEmpty()) item { EmptyState("مساحة لأفكارك", "أنشئ تدوينة مستقلة، أو احفظ إجابة المساعد، أو اكتب ملاحظة مرتبطة بخبر.", Icons.Outlined.EditNote) }
        items(notes.filter { (category == "الكل" || it.category == category) && (!pinnedOnly || it.pinned) && "${it.title} ${it.body} ${it.tags}".contains(query, true) }, key = { it.id }) { n ->
            BoxCard(Modifier.clickable { open(n) }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Caption(n.category, Mint); Spacer(Modifier.weight(1f)); if (n.pinned) Icon(Icons.Outlined.PushPin, "مثبتة", tint = Accent, modifier = Modifier.size(18.dp)) }
                Text(n.title.ifBlank { "مسودة بلا عنوان" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(n.body.take(180).ifBlank { "ابدأ الكتابة…" }, color = Muted, lineHeight = 25.sp)
                Caption(n.tags); Caption("آخر تعديل: ${dateText(n.updated)}")
            }
        }
    }
}
@Composable fun NoteScreen(vm: NabdViewModel, id: String, original: Note?, close: () -> Unit) {
    // Wait for Room's first emission when reopening an existing note.
    if (original == null) { Column(Modifier.padding(20.dp)) { CircularProgressIndicator(); Caption("جارٍ فتح التدوينة…") }; return }
    var title by rememberSaveable(id) { mutableStateOf(original.title) }
    var body by rememberSaveable(id) { mutableStateOf(original.body) }
    var tags by rememberSaveable(id) { mutableStateOf(original.tags) }
    var category by rememberSaveable(id) { mutableStateOf(original.category) }
    var links by rememberSaveable(id) { mutableStateOf(original.links) }
    var linked by rememberSaveable(id) { mutableStateOf(original.articleIds) }
    var linking by remember { mutableStateOf(false) }
    var linkQuery by remember { mutableStateOf("") }
    val articles by vm.articles.collectAsStateWithLifecycle()
    var pinned by rememberSaveable(id) { mutableStateOf(original.pinned) }
    var preview by rememberSaveable(id) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val draft = original.copy(title = title, body = body, tags = tags, category = category, links = links, pinned = pinned, articleIds = linked)
    val latest by rememberUpdatedState(draft)
    val dirty by rememberUpdatedState(draft != original)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(title, body, tags, category, links, pinned, linked) { delay(500); if (!deleted && draft != original) vm.saveNote(draft) }
    DisposableEffect(id, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && !deleted && dirty) vm.saveNote(latest) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); if (!deleted && dirty) vm.saveNote(latest) }
    }
    if (linking) AlertDialog(onDismissRequest = { linking = false }, title = { Text("ربط التدوينة بأخبار") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            SearchField(linkQuery, { linkQuery = it })
            articles.filter { it.title.contains(linkQuery, true) }.take(100).forEach { article ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ids = linked.split(',').filter { it.isNotBlank() }.toSet()
                    Checkbox(article.id in ids, { checked -> linked = (if (checked) ids + article.id else ids - article.id).joinToString(",") })
                    Text(article.title, fontSize = 13.sp, lineHeight = 23.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { linking = false }) { Text("تم") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("حذف التدوينة؟") }, text = { Text("هذا الحذف محلي ولا يمكن التراجع عنه دون نسخة احتياطية.") }, confirmButton = { TextButton(onClick = { deleted = true; vm.deleteNote(id); deleting = false; close() }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("إلغاء") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Caption("حفظ تلقائي محلي • أُنشئت ${dateText(original.created)}", Mint)
        Row { FilterChip(preview, { preview = !preview }, label = { Text("معاينة التنسيق") }); Spacer(Modifier.width(10.dp)); FilterChip(pinned, { pinned = !pinned }, label = { Text("تثبيت") }) }
        if (preview) { Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold); SelectionContainer { MarkdownText(body) } }
        else {
            OutlinedTextField(title, { title = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("عنوان التدوينة") }, shape = RoundedCornerShape(16.dp))
            Caption("تنسيق Markdown: # عنوان  •  - عنصر قائمة  •  **نص بارز**")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { body += "\n# " }) { Text("عنوان") }; TextButton(onClick = { body += "\n- " }) { Text("قائمة") }; TextButton(onClick = { body += " **نص بارز** " }) { Text("بارز") } }
            OutlinedTextField(body, { body = it.take(200000) }, Modifier.fillMaxWidth(), label = { Text("اكتب أفكارك…") }, minLines = 12, shape = RoundedCornerShape(16.dp))
        }
        OutlinedTextField(tags, { tags = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("وسوم مفصولة بفواصل") })
        OutlinedTextField(category, { category = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("التصنيف") })
        OutlinedTextField(links, { links = it.take(20000) }, Modifier.fillMaxWidth(), label = { Text("المصادر والروابط — كل رابط في سطر") }, minLines = 2)
        Action("ربط أخبار أو مجموعة أخبار", Icons.Outlined.Link) { linking = true }
        if (linked.isNotBlank()) Caption("مرتبطة بـ ${linked.split(',').size} خبر • ${if (original.analysisId.isNotBlank()) "ومن تحليل محفوظ" else "ملاحظة شخصية"}")
        Button(onClick = {
            saving=true
            vm.task {try {vm.persistNote(latest);close()}finally{saving=false}}
        }, enabled=!saving, modifier = Modifier.fillMaxWidth()) { Text(if(saving)"جارٍ الحفظ…"else"حفظ والعودة") }
        TextButton(onClick = { deleting = true }) { Text("حذف التدوينة", color = MaterialTheme.colorScheme.error) }
    }
}
