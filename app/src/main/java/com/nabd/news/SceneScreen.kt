@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nabd.news

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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

@Composable fun SceneScreen(vm:NabdViewModel,openTopic:(String)->Unit) {
    val topics by vm.sceneTopics.collectAsStateWithLifecycle();val signals by vm.trendSignals.collectAsStateWithLifecycle()
    val fetch by vm.sceneFetch.collectAsStateWithLifecycle();val busy by vm.sceneRefreshing.collectAsStateWithLifecycle()
    val articles by vm.articles.collectAsStateWithLifecycle();val links by vm.sceneLinks.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableStateOf("الرائج الآن")};var region by rememberSaveable{mutableStateOf("SA")}
    var query by rememberSaveable{mutableStateOf("")};var period by rememberSaveable{mutableStateOf("24 ساعة")}
    var kind by rememberSaveable{mutableStateOf("كل الإشارات")};var watched by rememberSaveable{mutableStateOf(false)}
    var info by remember{mutableStateOf(false)};var preferences by remember{mutableStateOf(false)}
    var create by remember{mutableStateOf(false)};var selected by rememberSaveable{mutableStateOf("")}
    LaunchedEffect(Unit){vm.initializeScene()}
    if(create)SceneCreateTopicDialog(vm){id->create=false;if(id.isNotBlank())openTopic(id)}
    if(preferences)ScenePreferences(vm){preferences=false}
    if(info)AlertDialog(onDismissRequest={info=false},title={Text("كيف نرصد المشهد؟")},text={Text(TrendEngine.METHOD)},confirmButton={TextButton(onClick={info=false}){Text("فهمت")}})
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ChoiceRow(listOf("الرائج الآن","رصد X","تغطية الصحف","التحليل الذكي"),tab){tab=it}
        }
        when(tab) {
            "رصد X" -> SceneSocialScreen(vm,openTopic)
            "التحليل الذكي" -> LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
                item { SceneCard {
                    Text("اختر موضوعًا لفهم تطوراته",style=MaterialTheme.typography.titleLarge)
                    SceneTopicPicker(topics,selected){selected=it}
                    Caption("كل تحليل باختيارك، عبر المحركات المعدة بالفعل. قراءة المحفوظات والتجميع المحلي لا تحتاج مفتاح AI.")
                } }
                val active=topics.firstOrNull{it.id==selected}
                if(active!=null)item{TopicAiPanel(vm,active)} else item{SceneEmpty("ابدأ بملف موضوع","احفظ موضوعًا من الرائج أو أنشئ موضوع متابعة، ثم اربط به الأخبار والمنشورات.","إنشاء موضوع"){create=true}}
            }
            else -> {
                val hours=when(period){"48 ساعة"->48;"7 أيام"->168;else->24}
                val since=System.currentTimeMillis()-hours*3600000L
                val latest=remember(signals){signals.groupBy{it.topicId}.mapValues{(_,s)->s.groupBy{it.source}.mapNotNull{it.value.maxByOrNull{x->x.observedAt}}}}
                val newsById=remember(articles){articles.associateBy{it.id}}
                val related=remember(links,newsById){links.filter{it.mode>=0}.groupBy{it.topicId}.mapValues{(_,ls)->ls.mapNotNull{newsById[it.articleId]}}}
                val displayed=remember(topics,latest,related,tab,region,query,watched,kind,period) {
                    topics.filter { topic ->
                        val ss=latest[topic.id].orEmpty()
                        val matchesText=query.isBlank() || "${topic.title} ${topic.description}".contains(query,true)
                        val hasFresh=ss.any{(if(it.published>0)it.published else it.observedAt)>=since}
                        val matchesRegion=region=="WORLD" || topic.region=="SA"
                        matchesText && (!watched || topic.saved) &&
                            if(tab=="تغطية الصحف") related[topic.id].orEmpty().any{it.published>=since} && ss.any{it.source=="local_coverage"}
                            else if(watched) (matchesRegion || topic.origin=="manual")
                            else matchesRegion && hasFresh && (kind=="كل الإشارات" || ss.any{it.source==if(kind=="رائج في البحث")"google_trends" else "local_coverage"})
                    }.sortedWith(if(tab=="تغطية الصحف")compareByDescending<SceneTopic>{latest[it.id].orEmpty().firstOrNull{s->s.source=="local_coverage"}?.sourceCount?:0}.thenByDescending{it.updated} else compareByDescending<SceneTopic>{it.saved}.thenByDescending{latest[it.id].orEmpty().maxOfOrNull{s->if(s.published>0)s.published else s.observedAt}?:it.updated})
                }
                LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
                    item { SceneCard {
                        Text(if(tab=="تغطية الصحف")"حدث واحد، زوايا متعددة" else "إشارات واضحة، بلا أرقام مصطنعة",style=MaterialTheme.typography.titleLarge)
                        SearchField(query,{query=it},"ابحث في الموضوعات وأرشيف المتابعة")
                        if(tab!="تغطية الصحف")ChoiceRow(listOf("السعودية","العالم المتاح"),if(region=="SA")"السعودية" else "العالم المتاح"){region=if(it=="السعودية")"SA" else "WORLD"}
                        ChoiceRow(listOf("24 ساعة","48 ساعة","7 أيام"),period){period=it}
                        if(tab!="تغطية الصحف")Picker("نوع الإشارة",listOf("كل الإشارات","رائج في البحث","تغطية محلية"),kind){kind=it}
                        Row { FilterChip(watched,{watched=!watched},label={Text("قائمة المتابعة")});Spacer(Modifier.width(8.dp));TextButton(onClick={create=true}){Text("موضوع جديد")} }
                        Row { Button(onClick={if(tab=="تغطية الصحف")vm.rebuildScene() else vm.refreshScene(region)},enabled=!busy){Icon(Icons.Outlined.Refresh,null);Text(if(busy)"جارٍ الفحص" else "تحديث الرصد")}
                            IconButton(onClick={info=true}){Icon(Icons.Outlined.Info,"منهجية الإشارات")};IconButton(onClick={preferences=true}){Icon(Icons.Outlined.Tune,"إعدادات الرصد")}}
                        Caption(if(tab=="تغطية الصحف")"تجميع محلي محافظ لعناوين آخر 7 أيام؛ ليس إثباتًا لتطابق الحدث أو استقلال التحرير." else if(region=="WORLD")"عينة أسواق: الولايات المتحدة والمملكة المتحدة والإمارات، وليست قائمة عالمية شاملة." else "أولوية السعودية؛ تصنيف الأخبار المحلية يعتمد على قرائن العنوان، واتجاه البحث على بلد الخلاصة.")
                    } }
                    if(busy)item{SceneLoading()}
                    if(tab!="تغطية الصحف")items(fetch.filter{it.id in if(region=="SA")listOf("google-SA") else listOf("google-US","google-GB","google-AE")},key={"fetch-${it.id}"}){state ->
                        SceneCard { Caption("${GoogleTrendsDataSource.regions[state.id.removePrefix("google-")]} • Google Trends",Mint)
                            if(state.error.isNotBlank()){Text(state.error,color=Accent);TextButton(onClick={vm.refreshScene(region,true)},enabled=!busy){Text("إعادة المحاولة")}}
                            Caption("آخر فحص: ${dateText(state.attempted)} • HTTP ${if(state.httpCode>0)state.httpCode else "غير متاح"}")
                            if(state.succeeded>0)Caption("آخر وصول ناجح: ${dateText(state.succeeded)}")
                        }
                    }
                    items(displayed,key={it.id}){topic->SceneTopicCard(vm,topic,latest[topic.id].orEmpty(),related[topic.id].orEmpty(),openTopic)}
                    if(displayed.isEmpty() && !busy)item{SceneEmpty("لا توجد إشارات محفوظة بهذه الفلاتر","حدّث الرصد أو وسّع الفترة. عند فشل Google Trends لا نستبدل البيانات بنتائج وهمية؛ يمكنك متابعة التجميع المحلي.","تجميع الأخبار المحفوظة"){vm.rebuildScene()}}
                }
            }
        }
    }
}
@Composable fun SceneTopicCard(vm:NabdViewModel,topic:SceneTopic,signals:List<TrendSignal>,news:List<Article>,open:(String)->Unit) {
    SceneCard(Modifier.clickable{open(topic.id)}) {
        Row { Text(topic.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            IconButton(onClick={vm.task{vm.dao.followSceneTopic(topic.id,!topic.saved)}}){Icon(if(topic.saved)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"متابعة الموضوع",tint=Accent)} }
        signals.forEach{s->Caption("${TrendEngine.label(s)} • ${if(s.source=="google_trends")GoogleTrendsDataSource.regions[s.region]?:s.region else "أخبار نبض المحفوظة"}",if(s.source=="google_trends")Mint else Accent)}
        if(signals.isEmpty())Caption("موضوع متابعة شخصي؛ لا إشارة انتشار مثبتة")
        if(news.isNotEmpty())Caption("${news.size} خبر مرتبط محفوظ • ${news.map{ArabicTopicText.publisher(it.url)}.distinct().size} نطاق نشر مختلف")
        signals.firstOrNull{it.source=="local_coverage" && it.previousCount>0}?.let{Caption("آخر 6 ساعات: ${it.recentCount} • الست السابقة: ${it.previousCount} خبر")}
        Caption("آخر رصد محفوظ: ${dateText(signals.maxOfOrNull{it.observedAt}?:topic.created)}")
    }
}
@Composable private fun SceneCreateTopicDialog(vm:NabdViewModel,close:(String)->Unit) {
    var title by remember{mutableStateOf("")};var body by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest={close("")},title={Text("ملف متابعة جديد")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{title=it.take(500)},label={Text("الموضوع أو القضية")});OutlinedTextField(body,{body=it.take(10000)},label={Text("وصف شخصي — لا يُرسل إلى AI افتراضيًا")},minLines=3)
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{close(vm.topics.create(title,body).id)}catch(e:Exception){error=safeError(e)}}}){Text("إنشاء")}},dismissButton={TextButton(onClick={close("")}){Text("إلغاء")}})
}
@Composable private fun ScenePreferences(vm:NabdViewModel,close:()->Unit) {
    var background by remember{mutableStateOf(vm.settings.get("sceneBackground","false")=="true")}
    var appearance by remember{mutableStateOf(vm.settings.get("appearance","dark"))}
    AlertDialog(onDismissRequest=close,title={Text("إعدادات نبض المشهد")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("المظهر");ChoiceRow(listOf("داكن","فاتح","حسب الجهاز"),when(appearance){"light"->"فاتح";"system"->"حسب الجهاز";else->"داكن"}){appearance=when(it){"فاتح"->"light";"حسب الجهاز"->"system";else->"dark"}}
        Row { Switch(background,{background=it});Text("تحديث رصد السعودية في الخلفية") }
        Caption("اختياري، كل 6 ساعات كحد طلب؛ يحدد Android التنفيذ الفعلي. اتصال غير محسوب وبطارية غير منخفضة. يُفحص RSS وتجميع الأخبار المحفوظة فقط، ولا يُستدعى AI ولا تُراقب حسابات X.")
    }},confirmButton={TextButton(onClick={vm.settings.set("appearance",appearance);vm.configVersion.value++;vm.settings.set("sceneBackground",background.toString());SceneScheduler.set(vm.getApplication(),background);close()}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}

