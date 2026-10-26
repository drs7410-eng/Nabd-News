package com.nabd.news

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NabdApp(vm: NabdViewModel) {
    val feedStates = rememberSaveableStateHolder()
    var articleReturnPage by rememberSaveable { mutableStateOf("home") }
    var page by rememberSaveable { mutableStateOf("home") }
    var worldReturnPage by rememberSaveable { mutableStateOf("home") }
    var sceneTopicId by rememberSaveable { mutableStateOf("") }
    var sceneReturnPage by rememberSaveable { mutableStateOf("scene") }
    var noteReturnPage by rememberSaveable { mutableStateOf("notebook") }
    var exploreReturnPage by rememberSaveable { mutableStateOf("home") }
    var articleId by rememberSaveable { mutableStateOf("") }
    var groupIds by rememberSaveable { mutableStateOf("") }
    var noteId by rememberSaveable { mutableStateOf("") }
    var chatId by rememberSaveable { mutableStateOf("") }
    val articles by vm.articles.collectAsStateWithLifecycle()
    val worldMetadata by vm.worldArticles.collectAsStateWithLifecycle()
    val worldPublishers by vm.worldPublishers.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var exportKind by remember { mutableStateOf("json") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.task {
            withContext(Dispatchers.IO) {
                val data = if (exportKind == "md") vm.backup.markdown() else vm.backup.exportJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(data) } ?: error("تعذر إنشاء الملف")
            }
            vm.notices.emit("اكتمل التصدير دون مفاتيح API أو إعدادات الحساب")
        }
    }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingRestore = it }
    if (pendingRestore != null) AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("استعادة نسخة نبض؟") },
        text = { Text("سيُدمج المحتوى محليًا. السجلات التي تحمل المعرّف نفسه قد تُستبدل. يُنصح بتصدير نسخة حالية أولًا. لن تُستورد مفاتيح أو إعدادات اتصال.") },
        confirmButton = { TextButton(onClick = {
            val uri = pendingRestore!!; pendingRestore = null
            vm.task {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(20_000_001) } ?: error("تعذر قراءة الملف")
                    require(bytes.size <= 20_000_000) { "الملف يتجاوز 20 MB" }
                    vm.backup.restore(bytes.toString(Charsets.UTF_8))
                }
                vm.notices.emit("اكتملت استعادة المحتوى المحلي")
            }
        }) { Text("استعادة") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("إلغاء") } })
    LaunchedEffect(Unit) { vm.notices.collect { snack.showSnackbar(it) } }
    val pendingPost by vm.pendingSharedPost.collectAsStateWithLifecycle()
    fun openTopic(id:String) { sceneTopicId=id;sceneReturnPage=when(page){"topic"->sceneReturnPage;"home","scene","explore","settings","saved","notebook","chats"->page;else->"scene"};page="topic" }
    if(pendingPost!=null) SaveSharedPostDialog(vm,pendingPost!!,{vm.pendingSharedPost.value=null},::openTopic)
    val topPages = setOf("home", "scene", "saved", "notebook", "settings")
    fun back() { page = when (page) { "assistant", "chat" -> "article"; "note" -> noteReturnPage; "topic" -> sceneReturnPage; "world" -> worldReturnPage; "worldTranslation" -> "article"; "explore" -> exploreReturnPage; "usage", "history", "sources", "chats" -> "settings"; "media" -> "home"; "article" -> articleReturnPage; else -> "home" } }
    BackHandler(page != "home") { back() }
    val currentArticle = articles.firstOrNull { it.id == articleId }
    fun openArticle(a: Article) { articleReturnPage = page; articleId = a.id; page = "article" }
    fun openWorld() { worldReturnPage=if(page in setOf("home","explore","settings","scene","saved"))page else "home";page="world" }
    fun openAssistant(a: Article, ids: String = a.id) { articleId = a.id; groupIds = ids; page = "assistant" }
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snack) }, topBar = {
        Column {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page !in topPages) IconButton(onClick = { back() }) { Icon(Icons.Outlined.ArrowForward, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(when(page) { "home" -> "نبض"; "world" -> "نبض العالم"; "worldTranslation" -> "إعدادات الترجمة"; "scene" -> "نبض المشهد"; "topic" -> "ملف الموضوع"; "explore" -> "استكشف"; "saved" -> "محفوظاتي"; "notebook" -> "دفتر نبض"; "settings" -> "إعدادات نبض"; "article" -> "تفاصيل الخبر"; "assistant" -> "مساعد نبض الذكي"; "chat" -> "حوار حول الخبر"; "note" -> "تدوينة شخصية"; "history" -> "سجل التحليلات"; "usage" -> "الاستخدام والتكلفة"; "media" -> "شاهد وتابع"; "sources" -> "مصادر الأخبار"; "chats" -> "المحادثات المحفوظة"; else -> "نبض" }, fontWeight = FontWeight.Bold, fontSize = 27.sp)
                    if (page == "home") Caption("أقرب إلى الحدث", Accent)
                }
                if (page == "home" || page == "saved") IconButton(onClick = vm::refresh, enabled = !busy) { Icon(Icons.Outlined.Refresh, "تحديث الأخبار", tint = Accent) }
                if (page in setOf("home", "explore", "saved")) IconButton(onClick = { page = "media" }) { Icon(Icons.Outlined.VideoLibrary, "الفيديو ومتابعة X", tint = Mint) }
                if (page in setOf("explore","scene")) IconButton(onClick=::openWorld){Icon(Icons.Outlined.Public,"نبض العالم",tint=Mint)}
                if (page in setOf("home", "scene")) IconButton(onClick = { exploreReturnPage=page;page="explore" }) { Icon(Icons.Outlined.Search,"البحث الشامل",tint=Muted) }
                if (page !in topPages) IconButton(onClick = { page = "settings" }) { Icon(Icons.Outlined.Settings, "الإعدادات", tint = Muted) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        }
    }, bottomBar = {
        if (page in topPages) NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
            listOf(Triple("home", "الرئيسية", Icons.Outlined.Home), Triple("scene", "المشهد", Icons.Outlined.Insights),
                Triple("saved", "محفوظاتي", Icons.Outlined.BookmarkBorder), Triple("notebook", "الدفتر", Icons.Outlined.EditNote),
                Triple("settings", "الإعدادات", Icons.Outlined.Tune)).forEach { (route, label, icon) ->
                NavigationBarItem(selected = page == route, onClick = { page = route }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, indicatorColor = Accent.copy(alpha = .12f), selectedTextColor = Accent))
            }
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                "home", "saved", "explore" -> feedStates.SaveableStateProvider(page) { FeedScreen(vm, page, articles, notes, ::openArticle, { a -> vm.result.value = a }, { n -> noteId = n.id; noteReturnPage="notebook"; page = "note" }, { ids ->
                    val first = articles.firstOrNull { it.id in ids } ?: return@FeedScreen
                    openAssistant(first, ids.joinToString(","))
                }, { page = "sources" }, ::openTopic, ::openWorld) }
                "article" -> feedStates.SaveableStateProvider("article-$articleId") { if(currentArticle!=null) {
                    val meta=worldMetadata.firstOrNull{it.articleId==currentArticle.id}
                    val publisher=meta?.let{m->worldPublishers.firstOrNull{it.id==m.publisherId}}
                    val assist={openAssistant(currentArticle)}
                    val addNote={noteReturnPage="article";noteId=newId();vm.saveNote(Note(id=noteId,articleIds=currentArticle.id,links=currentArticle.url));page="note"}
                    val chat={chatId="chat-${currentArticle.id}";vm.task{vm.dao.saveConversation(Conversation(id=chatId,articleId=currentArticle.id,title=currentArticle.title))};page="chat"}
                    if(meta!=null&&publisher!=null)WorldReaderScreen(vm,currentArticle,meta,publisher,assist,addNote,chat,{page="worldTranslation"})
                    else ArticleScreen(vm,currentArticle,assist,addNote,chat)
                }else LoadingArticle() }
                "assistant" -> if (currentArticle != null) AssistantScreen(vm, articles.filter { it.id in groupIds.split(',') }, false, "") else LoadingArticle()
                "chat" -> if (currentArticle != null) AssistantScreen(vm, listOf(currentArticle), true, chatId) else LoadingArticle()
                "notebook" -> NotebookScreen(vm, notes) { n -> noteId = n.id; noteReturnPage="notebook"; page = "note" }
                "note" -> NoteScreen(vm, noteId, notes.firstOrNull { it.id == noteId }) { page = noteReturnPage }
                "settings" -> SettingsScreen(vm, { if(it=="world")openWorld()else page = it }, { kind -> exportKind = kind; export.launch(if (kind == "md") "nabd-notes.md" else "nabd-backup.json") }, { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })
                "usage" -> UsageScreen(vm)
                "world" -> feedStates.SaveableStateProvider("world") { WorldScreen(vm,::openArticle) }
                "worldTranslation" -> WorldTranslationSettings(vm)
                "scene" -> SceneScreen(vm,::openTopic)
                "topic" -> TopicFileScreen(vm,sceneTopicId,::openArticle,::openTopic,{n->noteId=n.id;noteReturnPage="topic";page="note"})
                "media" -> MediaScreen(vm)
                "sources" -> SourcesScreen(vm)
                "history" -> HistoryScreen(vm) { vm.result.value = it }
                "chats" -> ChatsScreen(vm) { c -> if(c.articleId.startsWith("topic:")) openTopic(c.articleId.removePrefix("topic:")) else { articleId = c.articleId; chatId = c.id; page = "chat" } }
            }
        }
    }
    if (result != null) Dialog(onDismissRequest = { vm.result.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
        ResultContent(result!!, vm) { vm.result.value = null }
    }
}
@Composable private fun LoadingArticle() { EmptyState("الخبر غير متاح", "انتظر تحميل البيانات المحلية أو عُد إلى الأخبار.") }

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FeedScreen(vm: NabdViewModel, page: String, articles: List<Article>, notes: List<Note>, open: (Article) -> Unit,
    openAnalysis: (Analysis) -> Unit, openNote: (Note) -> Unit, analyzeGroup: (Set<String>) -> Unit, sources: () -> Unit, openSceneTopic:(String)->Unit = {}, openWorld:()->Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var source by rememberSaveable { mutableStateOf("الكل") }
    var period by rememberSaveable { mutableStateOf("كل الأوقات") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("") }
    val chosen = selected.split(',').filter { it.isNotBlank() }.toSet()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val sceneTopics by vm.sceneTopics.collectAsStateWithLifecycle()
    val worldMeta by vm.worldArticles.collectAsStateWithLifecycle()
    val worldPubs by vm.worldPublishers.collectAsStateWithLifecycle()
    val worldIds=remember(worldMeta){worldMeta.map{it.articleId}.toSet()}
    val translatedSearch=if(query.isNotBlank())vm.dao.worldHeadlines().collectAsStateWithLifecycle(initialValue=emptyList()).value else emptyList()
    val since = when (period) { "اليوم" -> System.currentTimeMillis() - 86400000L; "هذا الأسبوع" -> System.currentTimeMillis() - 7 * 86400000L; else -> 0L }
    val filtered = articles.filter { (page!="home" || it.id !in worldIds) && (page != "saved" || it.saved) && (category == "الكل" || it.category == category) && (source == "الكل" || it.source == source) && it.published >= since && (query.isBlank() || ("${it.title} ${it.availableText} ${it.source}".contains(query, true) || translatedSearch.any{t->t.articleId==it.id&&t.translated.contains(query,true)})) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (page == "home") item {
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF26313F), Color(0xFF15232A), Panel))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Caption("مساحتك الإخبارية الخاصة", Color(0xFF76D9B1))
                    Text("الخبر من مصدره.\nوالفهم أبعد من العنوان.", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp, color=Color.White)
                    Caption("أخبار أصلية • مساعد ذكي • دفتر شخصي",Color(0xFFBAC4D2))
                    FilledTonalButton(onClick=openWorld){Icon(Icons.Outlined.Public,null);Spacer(Modifier.width(8.dp));Text("نبض العالم • صحافة العالم، بلغتك") }
                }
            }
        }
        item { SearchField(query, { query = it }, if (page == "explore") "ابحث في الأخبار والتدوينات والتحليلات" else "ابحث في الأخبار…") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf("الكل") + (articles.map { it.category } + if (page == "explore") notes.map { it.category } else emptyList()).distinct()).forEach { c -> FilterChip(category == c, { category = c }, label = { Text(c) }) } } }
        if (page == "explore") {
            item { Picker("المصدر", listOf("الكل") + articles.map { it.source }.distinct(), source) { source = it } }
            item { ChoiceRow(listOf("كل الأوقات", "اليوم", "هذا الأسبوع"), period) { period = it } }
            if (query.isNotBlank()) {
                items(sceneTopics.filter { "${it.title} ${it.description}".contains(query,true) },key={"scene-topic-${it.id}"}) { topic ->
                    SceneCard(Modifier.clickable { openSceneTopic(topic.id) }) { Caption("نبض المشهد • ملف موضوع",Mint);Text(topic.title) }
                }
                items(notes.filter { n -> "${n.title} ${n.body} ${n.tags}".contains(query, true) && n.updated >= since &&
                    (category == "الكل" || n.category == category || articles.any { it.id in n.articleIds.split(',') && it.category == category }) &&
                    (source == "الكل" || articles.any { it.id in n.articleIds.split(',') && it.source == source }) }, key = { "note-${it.id}" }) { n ->
                    BoxCard(Modifier.clickable { openNote(n) }) { Caption("دفتر نبض", Mint); Text(n.title.ifBlank { "مسودة" }, fontWeight = FontWeight.Bold); Text(n.body.take(180), color = Muted) }
                }
                items(analyses.filter { a -> "${a.result} ${a.operation}".contains(query, true) && a.created >= since &&
                    (category == "الكل" || articles.any { it.id in a.articleIds.split(',') && it.category == category }) &&
                    (source == "الكل" || articles.any { it.id in a.articleIds.split(',') && it.source == source }) }, key = { "ai-${it.id}" }) { a ->
                    BoxCard(Modifier.clickable { openAnalysis(a) }) { Caption("تحليل محفوظ • ${a.provider}", Mint); Text(a.operation); Text(a.result.take(180), color = Muted) }
                }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} خبر", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { selecting = !selecting; selected = "" }) { Text(if (selecting) "إلغاء الاختيار" else "تحليل مجموعة") }
        } }
        if (selecting) item {
            Button(onClick = { analyzeGroup(chosen) }, enabled = chosen.size in 2..8, modifier = Modifier.fillMaxWidth()) { Text("مقارنة وتحليل ${chosen.size} أخبار") }
            Caption("اختر من خبرين إلى ثمانية أخبار مرتبطة بموضوع واحد.")
        }
        if (filtered.isEmpty()) item { EmptyState(if (page == "saved") "محفوظاتك تبدأ من خبر" else "جاهز لأول خبر", "أضف مصادر RSS أو اضغط تحديث. لا تُعرض أخبار تجريبية بوصفها أخبارًا حقيقية."); Action("إدارة المصادر", Icons.Outlined.RssFeed, sources) }
        items(filtered, key = { it.id }) { a ->
            val meta=worldMeta.firstOrNull{it.articleId==a.id};val publisher=meta?.let{m->worldPubs.firstOrNull{it.id==m.publisherId}}
            if(meta!=null&&publisher!=null&&!selecting)WorldNewsCard(vm,a,meta,publisher){open(a)}
            else ArticleCard(if(publisher!=null&&!publisher.allowImages)a.copy(image="")else a, a.id in chosen, selecting, {
            selected = (if (a.id in chosen) chosen - a.id else if (chosen.size < 8) chosen + a.id else chosen).joinToString(",")
        }) { if (selecting) selected = (if (a.id in chosen) chosen - a.id else if (chosen.size < 8) chosen + a.id else chosen).joinToString(",") else open(a) } }
    }
}

