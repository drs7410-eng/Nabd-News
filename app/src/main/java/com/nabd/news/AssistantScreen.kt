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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf

@Composable fun AssistantScreen(vm: NabdViewModel, initialArticles: List<Article>, chat: Boolean, chatId: String) {
    var provider by rememberSaveable { mutableStateOf(vm.settings.defaultProvider()) }
    var operation by rememberSaveable { mutableStateOf(if (initialArticles.size > 1) "مقارنة التغطيات" else "تلخيص قصير") }
    var question by rememberSaveable { mutableStateOf("") }
    var sendNotes by rememberSaveable { mutableStateOf(false) }
    var web by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var relatedIds by rememberSaveable { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val all by vm.articles.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val messageFlow = remember(chatId) { if (chat) vm.dao.messages(chatId) else flowOf(emptyList()) }
    val messages by messageFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = LocalClipboardManager.current
    val primary = initialArticles.firstOrNull()
    val items = (initialArticles.map { original -> all.firstOrNull { it.id == original.id } ?: original } + all.filter { it.id in relatedIds.split(',') }).distinctBy { it.id }.take(8)
    val personal = if (sendNotes) notes.filter { n -> n.articleIds.split(',').any { id -> items.any { it.id == id } } } else emptyList()
    val candidates = remember(primary, all) {
        val words = primary?.title?.split(' ')?.filter { it.length > 3 } ?: emptyList()
        all.filter { it.id !in initialArticles.map { a -> a.id } }.map { it to words.count { w -> it.title.contains(w) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { it.first }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("تنفيذ الطلب عبر $provider؟") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("سيُجلب أحدث متن محفوظ ثم يُرسل كاملًا لـ ${items.size} خبر إلى النموذج: ${vm.settings.model(provider, if (chat) "سؤال مخصص" else operation).ifBlank { "لم يُحدد" }}")
            Caption("المهمة: ${if (chat) "سؤال مخصص" else operation}")
            if (chat) Caption("يُرسل أحدث جزء من الحوار ضمن حد سياق محلي لتقليل الاستهلاك، وقد لا يتذكر المحرك الرسائل القديمة.")
            if (personal.isNotEmpty()) Text("ستُرسل أيضًا ${personal.size} تدوينة مرتبطة، بعد اختيارك الصريح.", color = Accent)
            if (web && provider == "OpenAI") Text("البحث الخارجي قد يضيف رسوم أدوات. لن يُستخدم مع ميزانية محلية مفعّلة.", color = Accent)
            Caption("التكلفة المحلية تقدير، والفاتورة الفعلية لدى المزود. لا انتقال تلقائي إلى نموذج آخر.")
        }
    }, confirmButton = { Button(onClick = {
        confirm = false
        vm.analyze(items, if (chat) "سؤال مخصص" else operation, provider, question, if (chat) chatId else null, personal, web)
    }) { Text("إرسال وتنفيذ") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BoxCard { Caption(if (chat) "حوار محفوظ حول الخبر" else "السياق الذي سيستخدمه المساعد", Mint); items.forEach { Text(it.title, fontWeight = FontWeight.Bold, lineHeight = 26.sp) }; Caption("يُحمّل المتن قبل التحليل وتُقرأ أحدث نسخة من قاعدة البيانات. إذا بقي مقتطفًا يتوقف الطلب؛ لا اقتطاع صامت للنص ولا توليد لبقية المقال.") } }
        item { ChoiceRow(listOf("OpenAI", "Gemini"), provider) { provider = it }; Caption("النموذج: ${vm.settings.model(provider, if (chat) "سؤال مخصص" else operation).ifBlank { "اختر نموذجًا في الإعدادات" }}") }
        if (!chat) {
            item { Picker("نوع العملية", operations, operation) { operation = it } }
            if (operation == "فحص الادعاءات") item { BoxCard { Text("فحص الادعاءات والأدلة", color = Accent, fontWeight = FontWeight.Bold); Text("الإجابة وحدها ليست تحققًا. دون أدلة مستقلة ستكون الحالة «أدلة غير كافية». الروابط المرفقة تحتاج مراجعتك.", lineHeight = 26.sp) } }
            if (operation == "مقارنة التغطيات") {
                item { SectionTitle("تغطيات مرتبطة مقترحة", "اقتراح محلي بناءً على كلمات العنوان؛ راجع صلة كل خبر قبل إضافته.") }
                items(candidates, key = { "candidate-${it.id}" }) { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(a.id in relatedIds.split(','), { checked ->
                            val ids = relatedIds.split(',').filter { it.isNotBlank() }.toSet()
                            relatedIds = (if (checked && items.size < 8) ids + a.id else ids - a.id).joinToString(",")
                        })
                        Column(Modifier.weight(1f)) { Text(a.title, fontSize = 14.sp, lineHeight = 24.sp); Caption("${a.source} • ${a.kind}") }
                    }
                }
                if (candidates.isEmpty()) item { Caption("لا توجد تغطيات مقترحة. يمكنك اختيار أخبار يدويًا من الرئيسية عبر «تحليل مجموعة».") }
            }
        }
        if (chat && messages.isEmpty()) item { Caption("اكتب سؤالك الأول. سيبقى الحوار محفوظًا على جهازك.") }
        if (chat) items(messages, key = { it.id }) { m ->
            BoxCard { Caption(if (m.role == "user") "أنت" else "إجابة ${m.provider} • ${m.model}", if (m.role == "user") Accent else Mint)
                SelectionContainer { Text(m.text, lineHeight = 28.sp) }
                if (m.role == "assistant") Row { TextButton(onClick = { clipboard.setText(AnnotatedString(m.text)) }) { Text("نسخ") }; TextButton(onClick = {
                    vm.saveNote(Note(title = "إجابة حول ${primary?.title?.take(60)}", body = m.text, articleIds = primary?.id ?: "", links = primary?.url ?: "", tags = "محادثة ذكية"))
                    vm.task { vm.notices.emit("حُفظت الإجابة في الدفتر") }
                }) { Text("حفظ في الدفتر") } }
            }
        }
        item { OutlinedTextField(question, { question = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text(if (chat) "سؤالك التالي" else "سؤال أو تعليمات إضافية") }, minLines = 3, shape = RoundedCornerShape(16.dp)) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sendNotes, { sendNotes = it }); Text("إرسال تدويناتي المرتبطة بهذا الخبر فقط", Modifier.weight(1f), fontSize = 14.sp) }; if (sendNotes) Caption("التدوينات المختارة: ${personal.joinToString { it.title.ifBlank { "مسودة" } }.ifBlank { "لا توجد" }}", Accent) }
        if (provider == "OpenAI") item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(web, { web = it }); Spacer(Modifier.width(10.dp)); Column { Text("بحث خارجي لفحص الأدلة"); Caption("اختياري • قد يضيف رسومًا • يتطلب دعم النموذج") } } }
        item { Button(onClick = { confirm = true }, enabled = !busy && (!chat || question.isNotBlank()) && (operation != "مقارنة التغطيات" || chat || items.size >= 2), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(10.dp)); Text(if (chat) "إرسال السؤال" else "تنفيذ $operation") }; Caption("تُحفظ النتائج تلقائيًا وتُسترجع العملية المطابقة دون طلب API جديد.") }
        if (!chat && primary != null) item { HorizontalDivider(); TextButton(onClick = { vm.nanoSummary(primary) }, enabled = !busy) { Text("تجربة تلخيص Gemini Nano محليًا") }; Caption("اختبار محلي تجريبي فقط؛ العربية غير مدعومة رسميًا في إصدار ML Kit المدمج. لا انتقال سحابي تلقائي.") }
    }
}
@Composable fun ChatsScreen(vm: NabdViewModel, open: (Conversation) -> Unit) {
    val chats by vm.conversations.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (chats.isEmpty()) item { EmptyState("لا توجد محادثات بعد", "افتح خبرًا واختر «محادثة حول هذا الخبر».", Icons.Outlined.ChatBubbleOutline) }
        items(chats, key = { it.id }) { c -> BoxCard(Modifier.clickable { open(c) }) { Caption(dateText(c.updated)); Text(c.title, fontWeight = FontWeight.Bold); Caption("إعادة فتح المحادثة المحفوظة", Mint) } }
    }
}
@Composable fun HistoryScreen(vm: NabdViewModel, open: (Analysis) -> Unit) {
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Analysis?>(null) }
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف التحليل؟") }, text = { Text("سيُحذف من السجل المحلي. التدوينات المنسوخة منه تبقى محفوظة.") }, confirmButton = { TextButton(onClick = { val id = deleting!!.id; deleting = null; vm.task { vm.dao.deleteAnalysis(id) } }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SearchField(query, { query = it }, "ابحث في نتائج التحليل…") }
        if (analyses.isEmpty()) item { EmptyState("أرشيف الفهم", "نتائج مساعد نبض تُحفظ تلقائيًا هنا وتتاح دون اتصال.", Icons.Outlined.AutoAwesome) }
        items(analyses.filter { "${it.result} ${it.operation} ${it.model}".contains(query, true) }, key = { it.id }) { a ->
            BoxCard(Modifier.clickable { open(a) }) { Caption("${a.provider} • ${a.model}", Mint); Text(a.operation, fontWeight = FontWeight.Bold); Text(a.result.take(160), color = Muted, lineHeight = 25.sp); Caption(a.status); Caption("${dateText(a.created)} • ${money(a.cost)}"); Row { TextButton(onClick = { open(a) }) { Text("فتح") }; TextButton(onClick = { deleting = a }) { Text("حذف") } } }
        }
    }
}
