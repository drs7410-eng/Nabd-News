@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nabd.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Composable fun TopicFileScreen(vm:NabdViewModel,id:String,openArticle:(Article)->Unit,openTopic:(String)->Unit,openNote:(Note)->Unit) {
    val topics by vm.sceneTopics.collectAsStateWithLifecycle();val articles by vm.articles.collectAsStateWithLifecycle()
    val allLinks by vm.sceneLinks.collectAsStateWithLifecycle();val allPosts by vm.socialPosts.collectAsStateWithLifecycle()
    val signals by vm.trendSignals.collectAsStateWithLifecycle();val allNotes by vm.notes.collectAsStateWithLifecycle()
    val noteLinks by vm.sceneNotes.collectAsStateWithLifecycle();val analyses by vm.analyses.collectAsStateWithLifecycle()
    val analysisLinks by vm.topicAnalyses.collectAsStateWithLifecycle();val timeline by vm.sceneTimeline.collectAsStateWithLifecycle()
    val topic=topics.firstOrNull{it.id==id}
    if(topic==null){SceneEmpty("جارٍ فتح ملف الموضوع","قد تكون البيانات في طور القراءة من التخزين المحلي.");return}
    var section by rememberSaveable(id){mutableStateOf("نظرة عامة")};var linkNews by remember{mutableStateOf(false)}
    var edit by remember{mutableStateOf(false)};var noteDialog by remember{mutableStateOf(false)};var eventDialog by remember{mutableStateOf(false)}
    var attachAnalysis by remember{mutableStateOf(false)};var attachNote by remember{mutableStateOf(false)}
    val linkedIds=remember(allLinks,id){allLinks.filter{it.topicId==id && it.mode>=0}.map{it.articleId}.toSet()}
    val news=remember(articles,linkedIds){articles.filter{it.id in linkedIds}.sortedBy{it.published}}
    val posts=allPosts.filter{it.topicId==id}
    val notes=allNotes.filter{n->noteLinks.any{it.topicId==id && it.noteId==n.id}}
    val savedAnalyses=analyses.filter{a->analysisLinks.any{it.topicId==id && it.analysisId==a.id}}
    if(linkNews)TopicNewsLinksDialog(vm,topic,articles,linkedIds){linkNews=false}
    if(edit)TopicEditDialog(vm,topic){edit=false}
    if(noteDialog)TopicNoteDialog(vm,topic){noteDialog=false}
    if(eventDialog)TopicEventDialog(vm,topic){eventDialog=false}
    if(attachAnalysis)AlertDialog(onDismissRequest={attachAnalysis=false},title={Text("ربط تحليل محفوظ")},text={LazyColumn { items(analyses.take(100),key={it.id}){a->TextButton(onClick={vm.task{vm.dao.saveTopicAnalysis(TopicAnalysisLink(id,a.id));attachAnalysis=false}}){Text("${a.operation} • ${dateText(a.created)}",maxLines=2)}} }},confirmButton={TextButton(onClick={attachAnalysis=false}){Text("إغلاق")}})
    if(attachNote)AlertDialog(onDismissRequest={attachNote=false},title={Text("ربط تدوينة من الدفتر")},text={LazyColumn { items(allNotes.take(100),key={it.id}){n->TextButton(onClick={vm.task{vm.dao.saveTopicNote(TopicNote(id,n.id));attachNote=false}}){Text(n.title.ifBlank{"مسودة"},maxLines=2)}} }},confirmButton={TextButton(onClick={attachNote=false}){Text("إغلاق")}})
    LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
        item { SceneCard {
            Row { Text(topic.title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                IconButton(onClick={vm.task{vm.dao.followSceneTopic(id,!topic.saved)}}){Icon(if(topic.saved)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"حفظ الموضوع ومتابعته",tint=Accent)} }
            Caption(when(topic.origin){"google"->"موضوع بحث؛ الارتباط بالأخبار تقريبي وليس إثباتًا أنها نفس الحدث";"local"->"تجمّع محلي لحدث محتمل؛ راجع الربط وصححه";else->"ملف متابعة أنشأته أنت"})
            Caption("بداية الرصد: ${dateText(topic.created)}")
            Picker("داخل ملف الموضوع",listOf("نظرة عامة","خط الأحداث","التغطيات","منشورات X","التحليلات","التدوينات"),section){section=it}
        } }
        when(section) {
            "نظرة عامة" -> {
                item { SceneCard {
                    if(topic.description.isNotBlank())Text(topic.description)else Caption("أضف وصفًا لهذا الملف. الوصف شخصي ولا يُرسل إلى AI افتراضيًا.")
                    Caption("${news.size} خبر محفوظ • ${news.map{ArabicTopicText.publisher(it.url)}.distinct().size} نطاق نشر • ${posts.size} منشور • ${notes.size} تدوينة")
                    if(news.isNotEmpty())Caption("أول نشر معروف: ${dateText(news.filter{it.published>0}.minOfOrNull{it.published}?:0)}\nآخر نشر معروف: ${dateText(news.maxOf{it.published})}")
                    FlowRow{TextButton(onClick={edit=true}){Text("وصف الملف")};TextButton(onClick={linkNews=true}){Text("ربط الأخبار وتصحيحها")}}
                    FlowRow{FilledTonalButton(onClick={noteDialog=true}){Text("ملاحظة سريعة")};TextButton(onClick={section="التحليلات"}){Text("تحليل الموضوع")}}
                } }
                items(signals.filter{it.topicId==id}.groupBy{it.source}.mapNotNull{it.value.maxByOrNull{s->s.observedAt}},key={it.id}){s->SceneCard {
                    Text(TrendEngine.label(s),style=MaterialTheme.typography.titleMedium);Caption("${s.source} • ${s.region} • رُصد ${dateText(s.observedAt)}")
                    if(s.source=="google_trends") {
                        Caption("تاريخ المصدر: ${dateText(s.published)}")
                        if(s.traffic.isNotBlank())Text("حجم بحث تقريبي كما ورد: ${s.traffic}")else Caption("لم توفر الخلاصة حجم البحث؛ لا توجد نسبة نمو محلية مختلقة")
                        LinkButton(s.sourceUrl,"مصدر إشارة البحث")
                        val refs=runCatching { Gson().fromJson<List<SignalNewsReference>>(s.newsLinks,object:TypeToken<List<SignalNewsReference>>(){}.type) }.getOrNull().orEmpty()
                        refs.take(8).forEach{r->LinkButton(r.url,"${r.source} • ${r.title}")}
                        if(refs.isNotEmpty())Caption("هذه روابط أرفقتها خلاصة Google؛ ليست مقالات جُلبت أو تحققنا من متنها تلقائيًا.")
                    } else { Caption("${s.newsCount} خبر • ${s.sourceCount} نطاق نشر مختلف");Caption("آخر 6 ساعات: ${s.recentCount} خبر • الست السابقة: ${s.previousCount}") }
                } }
            }
            "التغطيات" -> {
                item { Column { Button(onClick={linkNews=true}){Text("ربط أو استبعاد أخبار")};Caption("الخبر المختلف يبقى مستقلًا. تُزال روابط RSS المتطابقة فقط؛ لا تُمحى الروايات المتعارضة.") } }
                items(news,key={it.id}){a->SceneCard(Modifier.clickable{openArticle(a)}){
                    Text(a.title,style=MaterialTheme.typography.titleMedium);Caption("${a.source} • ${a.kind} • ${dateText(a.published)}",Mint)
                    if(timeline.any{it.topicId==id&&it.kind=="source_update"&&it.reference==a.url})Caption("رُصد تغير سابق في عنوان أو تاريخ هذه المادة")else Caption("مادة محفوظة؛ لا يوجد سجل تغير مرصود")
                    if(a.summary.isNotBlank())Text(a.summary.take(320));Caption(if(a.isExcerpt)"النص المحفوظ مقتطف أو جزئي" else "يتوفر متن محفوظ")
                } }
                if(news.isEmpty())item{SceneEmpty("لا أخبار مرتبطة بعد","اختر مواد من قاعدة نبض، أو حدّث التجميع المحلي. لا يُحمّل متن من مواقع غير مسموحة.")}
            }
            "منشورات X" -> {
                item { Caption("وقت المنشور أدناه هو وقت حفظه في نبض، وليس تاريخ نشر مؤكدًا من X.") }
                items(posts,key={it.id}){post->ScenePostCard(vm,post,openTopic)}
                if(posts.isEmpty())item{SceneEmpty("أضف زاوية اجتماعية للموضوع","شارك رابط منشور من X إلى نبض، واختر هذا الملف عند الحفظ.")}
            }
            "التحليلات" -> {
                item{TopicAiPanel(vm,topic)}
                item{TextButton(onClick={attachAnalysis=true}){Text("ربط تحليل محفوظ سابقًا")}}
                items(savedAnalyses,key={it.id}){a->SceneCard {
                    Text(a.operation,style=MaterialTheme.typography.titleMedium);Caption("${a.provider} • ${a.model} • ${dateText(a.created)}")
                    Caption("Tokens: ${a.inputTokens?:"غير متاح"} / ${a.outputTokens?:"غير متاح"} • التكلفة ${money(a.cost)}")
                    Text(a.result.take(400));Caption(a.status,Accent)
                    FlowRow { TextButton(onClick={vm.result.value=a}){Text("فتح النتيجة")};TextButton(onClick={vm.task{
                        val n=Note(title="${topic.title} — ${a.operation}",body=a.result,analysisId=a.id,articleIds=news.joinToString(","){it.id},links=a.sources,category="نبض المشهد",tags="تحليل موضوع")
                        vm.dao.saveNote(n);vm.dao.saveTopicNote(TopicNote(id,n.id));vm.notices.emit("حُفظ التحليل في دفتر نبض وربط بالموضوع")
                    }}){Text("إلى الدفتر")}}
                } }
            }
            "التدوينات" -> {
                item{FlowRow{Button(onClick={noteDialog=true}){Text("تدوينة جديدة")};TextButton(onClick={attachNote=true}){Text("ربط تدوينة")}}}
                items(notes.sortedByDescending{it.updated},key={it.id}){n->SceneCard(Modifier.clickable{openNote(n)}){Text(n.title.ifBlank{"مسودة"},style=MaterialTheme.typography.titleMedium);Text(n.body.take(350));Caption(dateText(n.updated))}}
                if(notes.isEmpty())item{SceneEmpty("أفكارك جزء من الملف","أضف ملاحظة قصيرة أو اربط تدوينة من الدفتر. تبقى محلية ما لم تختر إرسالها صراحة.")}
            }
            "خط الأحداث" -> {
                val derived=news.filter{it.published>0}.map{TopicTimelineEvent(id="news-${it.id}",topicId=id,title=it.title,at=it.published,kind="news_published",reference=it.url)}+
                    posts.map{TopicTimelineEvent(id="x-${it.id}",topicId=id,title=it.title.ifBlank{"حفظ رابط منشور"},at=it.created,kind="post_saved",reference=it.url)}+
                    savedAnalyses.map{TopicTimelineEvent(id="ai-${it.id}",topicId=id,title=it.operation,at=it.created,kind="ai_analysis")}+
                    notes.map{TopicTimelineEvent(id="note-${it.id}",topicId=id,title=it.title,at=it.created,kind="note_created")}
                val entries=(derived+timeline.filter{it.topicId==id}).sortedBy{it.at}
                item{Column{Button(onClick={eventDialog=true}){Text("إضافة تطور يدوي")};Caption("تميز التسميات بين تاريخ نشر المصدر ووقت الحفظ والتحليل والرصد. لا يُستنتج تاريخ غير متاح.")}}
                items(entries,key={it.id}){e->SceneCard {Caption(dateText(e.at),Mint);Text(e.title);Caption(when(e.kind){"news_published"->"نشر خبر حسب المصدر";"post_saved"->"حفظ منشور في نبض";"source_update"->"وقت رصد تغير العنوان أو تاريخ الخلاصة، وليس إثبات تغير المتن";"ai_analysis"->"إنشاء تحليل آلي";"note_created"->"إنشاء تدوينة";else->"تطور أدخلته يدويًا"});if(e.reference.isNotBlank())LinkButton(e.reference,"رابط المرجع")}}
                if(entries.isEmpty())item{SceneEmpty("لم يبدأ خط الأحداث بعد","اربط خبرًا أو أضف تطورًا بتاريخه ومصدره.")}
            }
        }
    }
}
@Composable private fun TopicNewsLinksDialog(vm:NabdViewModel,topic:SceneTopic,articles:List<Article>,linked:Set<String>,close:()->Unit) {
    var query by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("تصحيح تغطية الموضوع")},text={Column {
        SearchField(query,{query=it},"ابحث في عناوين الأخبار")
        LazyColumn(Modifier.heightIn(max=380.dp)){items(articles.filter{query.isBlank()||"${it.title} ${it.source}".contains(query,true)}.take(200),key={it.id}){a->Row {
            Checkbox(a.id in linked,{include->vm.task{vm.topics.link(topic.id,a.id,include)}})
            Column(Modifier.weight(1f)){Text(a.title,maxLines=3);Caption("${a.source} • ${dateText(a.published)}")}
        }}}
        Caption("الاستبعاد اليدوي محفوظ ولن يعيده التجميع تلقائيًا. تعرض القائمة حتى 200 نتيجة؛ ضيّق البحث للوصول إلى الأقدم.")
    }},confirmButton={TextButton(onClick=close){Text("تم")}})
}
@Composable private fun TopicEditDialog(vm:NabdViewModel,topic:SceneTopic,close:()->Unit) {
    var title by remember{mutableStateOf(topic.title)};var body by remember{mutableStateOf(topic.description)}
    AlertDialog(onDismissRequest=close,title={Text("وصف ملف الموضوع")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{title=it.take(500)},enabled=topic.origin=="manual",label={Text("العنوان")})
        if(topic.origin!="manual")Caption("عنوان إشارة المصدر محفوظ كما ورد؛ يمكنك تخصيص وصف الملف.")
        OutlinedTextField(body,{body=it.take(10000)},label={Text("وصف شخصي — مستبعد من AI افتراضيًا")},minLines=4)
    }},confirmButton={TextButton(onClick={if(title.isNotBlank())vm.task{vm.dao.saveSceneTopic(topic.copy(title=title,description=body));close()}}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable private fun TopicNoteDialog(vm:NabdViewModel,topic:SceneTopic,close:()->Unit) {
    var title by remember{mutableStateOf("")};var body by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("ملاحظة للموضوع")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{title=it.take(500)},label={Text("عنوان اختياري")});OutlinedTextField(body,{body=it.take(30000)},label={Text("ملاحظتك")},minLines=4);if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{vm.topics.note(topic.id,title,body);close()}catch(e:Exception){error=safeError(e)}}}){Text("حفظ في الدفتر")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable private fun TopicEventDialog(vm:NabdViewModel,topic:SceneTopic,close:()->Unit) {
    var title by remember{mutableStateOf("")};var date by remember{mutableStateOf("")};var url by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("تطور بتاريخه ومصدره")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{title=it.take(1000)},label={Text("التطور")});OutlinedTextField(date,{date=it},label={Text("YYYY-MM-DD")});OutlinedTextField(url,{url=it.trim()},label={Text("رابط مرجع HTTPS — اختياري")});if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{
        require(title.isNotBlank());require(url.isBlank() || url.startsWith("https://")&&ReaderNavigation.allowed(url))
        val at=java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        vm.dao.saveTopicTimeline(TopicTimelineEvent(topicId=topic.id,title=title,at=at,reference=url));close()
    }catch(_:Exception){error="أدخل عنوانًا وتاريخ YYYY-MM-DD ورابط HTTPS صالحًا إن أضفته"}}}){Text("حفظ التطور")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}

