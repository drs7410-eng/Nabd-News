@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nabd.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun TopicAiPanel(vm:NabdViewModel,topic:SceneTopic) {
    var provider by rememberSaveable(topic.id){mutableStateOf(vm.settings.defaultProvider())}
    var operation by rememberSaveable(topic.id){mutableStateOf("تحليل موضوع")}
    var question by rememberSaveable(topic.id){mutableStateOf("")}
    var includePosts by rememberSaveable(topic.id){mutableStateOf(false)}
    var includeNotes by rememberSaveable(topic.id){mutableStateOf(false)}
    var web by rememberSaveable(topic.id){mutableStateOf(false)};var chat by rememberSaveable(topic.id){mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)};var claimId by remember{mutableStateOf<String?>(null)}
    var context by remember{mutableStateOf<TopicContext?>(null)};var error by remember{mutableStateOf("")}
    val articleContext by vm.articles.collectAsStateWithLifecycle();val signalContext by vm.trendSignals.collectAsStateWithLifecycle()
    val links by vm.sceneLinks.collectAsStateWithLifecycle();val posts by vm.socialPosts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val notes by vm.notes.collectAsStateWithLifecycle()
    val noteLinks by vm.sceneNotes.collectAsStateWithLifecycle()
    val history by remember(topic.id){vm.dao.messages("topic-chat:${topic.id}")}.collectAsStateWithLifecycle(initialValue=emptyList())
    LaunchedEffect(topic.id,links,posts,includePosts,articleContext,signalContext){context=null;error="";try{context=vm.topicAi.context(topic.id,includePosts)}catch(e:Exception){error=safeError(e)}}
    val selectedNotes=if(includeNotes)notes.filter{n->noteLinks.any{it.topicId==topic.id && it.noteId==n.id}}else emptyList()
    val upperTokens=(context?.text.orEmpty()+AI_INSTRUCTIONS+selectedNotes.joinToString{it.body.take(4000)}+if(chat)AiPolicy.context(history.map{it.role to it.text}).joinToString{it.second}else "").toByteArray().size.toLong()+2048
    val model=vm.settings.model(provider,operation)
    val estimated=if(web)null else vm.ai.price(provider,model)?.cost(upperTokens,AiPolicy.MAX_OUTPUT.toLong())
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("تنفيذ تحليل الموضوع؟")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("$provider • ${if(provider=="Gemini Nano")"محلي تجريبي" else model.ifBlank{"لم يُحدد النموذج"}}")
        Caption("آخر ${context?.articleCount?:0} من ${context?.totalArticles?:0} أخبار. قد تتضمن مقتطفات موسومة، ولا يُعاد بناء متن غير متاح.")
        Caption("النصوص المضافة من X: ${context?.postTexts?:0} • الملاحظات المختارة: ${selectedNotes.size}")
        Caption("حجز إدخال محافظ للميزانية: نحو $upperTokens Token كحد تقديري محلي، وليس تعداد المزود الفعلي.")
        Caption("التكلفة التقديرية: ${if(provider=="Gemini Nano")"محلي" else money(estimated)}")
        if(web && provider=="OpenAI")Text("البحث الخارجي اختياري وقد يضيف رسوم أدوات من رصيدك؛ لا يتاح مع حد ميزانية محلي مفعّل.",color=Accent)
        Text("لا حكم بالصحة اعتمادًا على النموذج وحده. دون أدلة مستقلة، النتيجة تحليل للمصادر المتاحة.")
    }},confirmButton={TextButton(onClick={confirm=false
        val instruction=if(operation=="استخراج الادعاءات")"استخرج الادعاءات فقط في JSON صالح: {\"claims\":[{\"claim\":\"نص الادعاء\",\"verifiable\":true}]}. التصنيف أولي ولا يثبت الصحة." else question
        vm.runTopicAnalysis(topic.id,operation,provider,instruction,includePosts,includeNotes,web&&provider=="OpenAI",chat,if(operation=="فحص الادعاءات")claimId else null)
    },enabled=context!=null && !busy){Text("إرسال وتنفيذ")}},dismissButton={TextButton(onClick={confirm=false}){Text("إلغاء")}})
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SceneCard {
            Text("تحليل قابل للمراجعة",style=MaterialTheme.typography.titleLarge)
            ChoiceRow(listOf("OpenAI","Gemini","Gemini Nano"),provider){provider=it;if(it=="Gemini Nano"){operation="تلخيص موضوع";includePosts=false;includeNotes=false;web=false}}
            Caption("النموذج: ${if(provider=="Gemini Nano")"ML Kit محلي، دعم العربية تجريبي" else model.ifBlank{"اضبط المفتاح والنموذج من إعدادات AI الحالية"}}")
            Picker("المهمة",listOf("تلخيص موضوع","تحليل موضوع","مقارنة التغطيات","استخراج الادعاءات","فحص الادعاءات","سؤال مخصص"),operation){operation=it;claimId=null}
            OutlinedTextField(question,{question=it.take(4000)},Modifier.fillMaxWidth(),label={Text("سؤال مخصص أو توجيه")},minLines=3)
            if(provider!="Gemini Nano") {
                Row{Checkbox(includePosts,{includePosts=it});Text("إرسال نصوص X التي أضفتها أو شاركتها")}
                Row{Checkbox(includeNotes,{includeNotes=it});Text("إرسال تدويناتي المرتبطة صراحة")}
                Row{Switch(chat,{chat=it});Text("الاحتفاظ بحوار متتابع لهذا الموضوع")}
            }
            if(provider=="OpenAI")Row{Switch(web,{web=it});Text("بحث خارجي اختياري — قد تُضاف رسوم أدوات")}
            if(error.isNotBlank())Text(error,color=Accent)
            if(context!=null)Caption("سياق متاح: ${context!!.articleCount} خبر من ${context!!.totalArticles}، مع إشارات المصادر. لا تُرسل الملاحظات أو نصوص X افتراضيًا.")
            Button(onClick={confirm=true},enabled=!busy&&context!=null&&(provider!="Gemini Nano"||operation=="تلخيص موضوع"),modifier=Modifier.fillMaxWidth()){Text(if(busy)"جارٍ التنفيذ…" else "مراجعة السياق والتكلفة ثم التنفيذ")}
        }
        if(chat && history.isNotEmpty())SceneCard { Text("الحوار المحفوظ",style=MaterialTheme.typography.titleMedium)
            history.takeLast(12).forEach{m->Caption(if(m.role=="user")"سؤالك" else "${m.provider} • إجابة آلية",Mint);Text(m.text)}
            Caption("تُعرض آخر 12 رسالة؛ يُرسل سياق محدود وفق سياسة المحادثة الحالية.")
        }
        SceneClaims(vm,topic){claim->claimId=claim.id;operation="فحص الادعاءات";question="افحص هذا الادعاء على حدة: ${claim.text}";confirm=true}
    }
}
@Composable private fun SceneClaims(vm:NabdViewModel,topic:SceneTopic,check:(VerificationClaim)->Unit) {
    val claims by vm.sceneClaims.collectAsStateWithLifecycle();val evidence by vm.sceneEvidence.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var add by remember{mutableStateOf(false)};var claimText by remember{mutableStateOf("")}
    var evidenceFor by remember{mutableStateOf<VerificationClaim?>(null)};var url by remember{mutableStateOf("")}
    var stance by remember{mutableStateOf("context")};var published by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    if(add)AlertDialog(onDismissRequest={add=false},title={Text("ادعاء مستقل")},text={OutlinedTextField(claimText,{claimText=it.take(2000)},label={Text("اكتب الادعاء المراد فحصه")},minLines=3)},confirmButton={TextButton(onClick={vm.task{if(claimText.isNotBlank()){vm.dao.saveClaim(VerificationClaim(topicId=topic.id,text=claimText.trim()));claimText="";add=false}}}){Text("حفظ الادعاء")}},dismissButton={TextButton(onClick={add=false}){Text("إلغاء")}})
    evidenceFor?.let { claim -> AlertDialog(onDismissRequest={evidenceFor=null},title={Text("مصدر دليل — تصنيفك قابل للمراجعة")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(url,{url=it.trim()},label={Text("رابط HTTPS للدليل")})
        Picker("الموقف",listOf("سياق غير مصنف","يدعم الادعاء","يعارض الادعاء"),when(stance){"support"->"يدعم الادعاء";"conflict"->"يعارض الادعاء";else->"سياق غير مصنف"}){stance=when(it){"يدعم الادعاء"->"support";"يعارض الادعاء"->"conflict";else->"context"}}
        OutlinedTextField(published,{published=it},label={Text("تاريخ المصدر YYYY-MM-DD — اختياري")})
        OutlinedTextField(desc,{desc=it.take(2000)},label={Text("ماذا يثبت المصدر؟")})
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{require(url.startsWith("https://")&&ReaderNavigation.allowed(url)){"أدخل رابط HTTPS صالحًا"}
        val at=if(published.isBlank())0 else java.time.LocalDate.parse(published).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        vm.dao.saveClaimEvidence(ClaimEvidence(claimId=claim.id,url=url,stance=stance,published=at,description=desc));evidenceFor=null;url="";published="";desc="";error=""
    }catch(e:Exception){error=safeError(e)}}}){Text("حفظ الدليل")}},dismissButton={TextButton(onClick={evidenceFor=null}){Text("إلغاء")}}) }
    SceneCard { Text("ادعاءات تحتاج مراجعة",style=MaterialTheme.typography.titleLarge);TextButton(onClick={add=true}){Text("إضافة ادعاء يدويًا")};Caption("لا تمنح النتيجة الآلية حكمًا بالصحة. تصنيف الأدلة اليدوي يعود لك، والاستقلال والتاريخ يحتاجان مراجعة.") }
    var visible by rememberSaveable(topic.id){mutableIntStateOf(20)}
    val topicClaims=claims.filter{it.topicId==topic.id}
    topicClaims.take(visible).forEach { claim -> SceneCard {
        Text(claim.text,style=MaterialTheme.typography.titleMedium);Caption(claim.status,Accent)
        Caption("قابلية التحقق: ${when(claim.verifiable){"yes"->"مرشح للتحقق، تصنيف أولي";"no"->"قد لا يكون قابلًا للتحقق، تصنيف أولي";else->"لم تُحدد"}}")
        evidence.filter{it.claimId==claim.id}.forEach { e ->
            Caption("${when(e.stance){"support"->"يدعم — بتصنيفك";"conflict"->"يعارض — بتصنيفك";else->"مصدر سياق أو استشهاد غير مصنف"}} • ${dateText(e.published)}")
            if(e.description.isNotBlank())Text(e.description);LinkButton(e.url,"عرض الدليل الأصلي")
        }
        FlowRow { TextButton(onClick={check(claim)},enabled=!busy){Text("تحليل هذا الادعاء")};TextButton(onClick={evidenceFor=claim;error=""}){Text("إضافة دليل")};TextButton(onClick={vm.task{vm.dao.deleteClaim(claim.id)}}){Text("حذف")}}
    } }
    if(topicClaims.size>visible)TextButton(onClick={visible+=20}){Text("عرض المزيد من الادعاءات (${topicClaims.size-visible})")}
}

