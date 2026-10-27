@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nabd.news

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged

object WorldTranslationChoice {
    fun get(rows:List<WorldTranslation>,slot:String,text:String,language:String,glossaryHash:String,engine:String="MLKit",expectedVersion:String?=null)=
        rows.firstOrNull{it.slot==slot && it.textHash==AiPolicy.hash(text) && it.language==language && it.glossaryHash==glossaryHash && it.engine==engine && (expectedVersion==null||it.engineVersion==expectedVersion) && it.state=="ready"}
}
@Composable fun WorldScreen(vm:NabdViewModel,open:(Article)->Unit) {
    val publishers by vm.worldPublishers.collectAsStateWithLifecycle();val metadata by vm.worldArticles.collectAsStateWithLifecycle()
    val articles by vm.articles.collectAsStateWithLifecycle();val busy by vm.worldBusy.collectAsStateWithLifecycle();val translatedIds by vm.worldTranslatedIds.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableStateOf("الرئيسية العالمية")};var query by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("الكل")}
    var publisher by rememberSaveable{mutableStateOf("")};var country by rememberSaveable{mutableStateOf("الكل")};var filters by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){vm.world.seed()}
    val headlineFlow=remember{vm.dao.worldHeadlines().distinctUntilChanged()}
    val searchTranslations=if(query.isNotBlank())headlineFlow.collectAsStateWithLifecycle(initialValue=emptyList()).value else emptyList()
    val byArticle=remember(metadata){metadata.associateBy{it.articleId}};val byPublisher=remember(publishers){publishers.associateBy{it.id}}
    val latestCutoff=remember(articles,vm.retentionDays()){System.currentTimeMillis()-vm.retentionDays()*86400000L}
    val visible=remember(articles,byArticle,byPublisher,query,category,publisher,country,tab,translatedIds,searchTranslations,latestCutoff){articles.filter{a->
        val m=byArticle[a.id];val p=m?.let{byPublisher[it.publisherId]}
        m!=null && p!=null && (tab=="المحفوظات" || query.isNotBlank() || a.published<=0 || a.published>=latestCutoff) && (tab!="المحفوظات" || a.saved || a.id in translatedIds) &&
            (publisher.isBlank()||m.publisherId==publisher)&&(country=="الكل"||p.country==country)&&(category=="الكل"||a.category==category)&&
            (query.isBlank()||"${a.title} ${a.summary} ${p.name} ${p.arabicName}".contains(query,true)||searchTranslations.any{it.articleId==a.id&&it.translated.contains(query,true)})
    }.sortedByDescending{it.published}}
    if(filters)AlertDialog(onDismissRequest={filters=false},title={Text("صحافة تناسب اهتمامك")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ScenePublisherPicker(publishers,publisher){publisher=it}
        Picker("البلد",listOf("الكل")+publishers.map{it.country}.distinct(),country){country=it}
        Caption("البلد هو بلد الصحيفة، وليس موقع الحدث. الأقسام بحسب تصنيف الخلاصة؛ لا يُفترض أن الصحيفة توفر كل الأقسام.")
    }},confirmButton={TextButton(onClick={filters=false}){Text("تطبيق")}},dismissButton={TextButton(onClick={publisher="";country="الكل";category="الكل";filters=false}){Text("مسح الفلاتر")}})
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=20.dp,vertical=6.dp)){ChoiceRow(listOf("الرئيسية العالمية","الصحف","المحفوظات","الترجمة"),tab){tab=it}}
        when(tab) {
            "الصحف"->WorldSourcesScreen(vm){id->publisher=id;tab="الرئيسية العالمية"}
            "الترجمة"->WorldTranslationSettings(vm)
            else->LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
                item(key="world-intro") { Surface(shape=RoundedCornerShape(26.dp),color=Panel) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Mint.copy(alpha=.10f),Panel,Accent.copy(alpha=.08f)))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Caption("NABD WORLD",Mint)
                        Text("صحافة العالم، بلغتك.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        Caption(if(tab=="المحفوظات")"أرشيف الأصل والترجمة؛ إعادة الفتح لا تنشئ طلب AI." else "هوية المصدر محفوظة. والترجمة الآلية واضحة، بلا تشتيت.")
                        SearchField(query,{query=it},"ابحث في الأصل والترجمات المحفوظة")
                        Picker("القسم",listOf("الكل")+worldCategories,category){category=it}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick={filters=true}){Icon(Icons.Outlined.FilterList,null);Text("الصحيفة والبلد")}
                            TextButton(onClick=vm::refreshWorld,enabled=!busy){Icon(Icons.Outlined.Refresh,null);Text(if(busy)"جارٍ التحديث…"else"تحديث المصادر")}
                        }
                        if(publisher.isNotBlank())TextButton(onClick={publisher=""}){Text("${byPublisher[publisher]?.name.orEmpty()} ×")}
                        Caption("${visible.size} مادة محفوظة • ${publishers.count{it.enabled}} صحيفة مفعّلة. لا يُنزّل المتن الكامل ولا يُرسل للسحابة بتحديث القائمة.")
                    }
                } }
                if(busy)item{SceneLoading()}
                if(visible.isEmpty()&&!busy)item{SceneEmpty("ابدأ بصحيفة موثقة","افتح الصحف وافحص خلاصة حقيقية، ثم راجع إذن المعالجة. جهّز نموذج اللغة مرة واحدة من الترجمة. لن نملأ الصفحة بأخبار تجريبية.","استعراض الصحف"){tab="الصحف"}}
                itemsIndexed(visible,key={_,a->a.id}){index,a->val meta=byArticle[a.id]!!;val p=byPublisher[meta.publisherId]!!
                    WorldNewsCard(vm,a,meta,p,index==0){open(a)}
                }
            }
        }
    }
}
@Composable fun ScenePublisherPicker(publishers:List<WorldPublisher>,selected:String,choose:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Column { OutlinedButton(onClick={expanded=true}){Text(publishers.firstOrNull{it.id==selected}?.name?:"جميع الصحف",maxLines=2)}
        DropdownMenu(expanded,{expanded=false},Modifier.heightIn(max=340.dp)) {
            DropdownMenuItem(text={Text("جميع الصحف")},onClick={choose("");expanded=false})
            publishers.forEach{p->DropdownMenuItem(text={Text("${p.name} • ${p.arabicName}")},onClick={choose(p.id);expanded=false})}
        }
    }
}
@Composable fun WorldGoogleAttribution(){
    val context=LocalContext.current
    Surface(color=Color.White,shape=MaterialTheme.shapes.small,modifier=Modifier.clickable{openOriginalArticle(context,"https://translate.google.com/")}) {
        Image(painterResource(R.drawable.google_translate_attribution),contentDescription="Powered by Google Translate — ترجمة آلية محلية",modifier=Modifier.padding(horizontal=10.dp,vertical=8.dp).size(width=176.dp,height=16.dp))
    }
}
@Composable fun WorldNewsCard(vm:NabdViewModel,a:Article,meta:WorldArticle,p:WorldPublisher,lead:Boolean=false,open:()->Unit) {
    val rows by remember(a.id){vm.dao.worldTranslations(a.id).distinctUntilChanged()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val glossary by vm.worldGlossary.collectAsStateWithLifecycle();val version by vm.configVersion.collectAsStateWithLifecycle()
    val gh=remember(glossary,a.category){EditorialProtection.glossaryHash(glossary,a.category)}
    val auto=remember(version){vm.settings.get("worldAutoLocal","true")=="true"}
    val allowed=WorldRights.local(p,meta)
    val preferred=remember(a.id,version){vm.settings.get("worldPreferred_${a.id}","MLKit")}
    fun best(slot:String,text:String)= (if(preferred!="MLKit"&&WorldRights.cloud(p,meta))WorldTranslationChoice.get(rows,slot,text,meta.language,gh,preferred)else null)
        ?:if(allowed)WorldTranslationChoice.get(rows,slot,text,meta.language,gh,"MLKit",vm.worldLocal.version)else null
    val title=best("title",a.title);val summary=best("summary",a.summary)
    var translating by remember(a.id){mutableStateOf(false)}
    var translationProblem by remember(a.id){mutableStateOf("")}
    LaunchedEffect(a.id,a.title,a.summary,auto,gh,allowed,version){
        translationProblem=""
        if(auto&&allowed) {
            try { if(vm.worldLocal.ready(meta.language)) {
                translating=true
                if(a.title.length<=2400)vm.worldTranslation.translate(a.id,"title",a.title)
                if(a.summary.isNotBlank()&&a.summary.length<=1200)vm.worldTranslation.translate(a.id,"summary",a.summary)
            } else translationProblem="نموذج ${worldLanguages[meta.language]?:meta.language} غير جاهز؛ نزّله من الترجمة"
            }catch(e:CancellationException){throw e}catch(e:Exception){translationProblem=safeError(e)}finally{translating=false}
        }
    }
    SceneCard(Modifier.clickable(onClick=open)) {
        Caption("${p.name} • ${a.category}",Mint)
        if(meta.author.isNotBlank())Caption("بقلم ${meta.author}")
        if(lead&&p.allowImages&&a.image.isNotBlank())NewsImage(a.image,"صورة ${p.name}")
        Row {
            Text(title?.translated?:a.title,Modifier.weight(1f),style=if(lead)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            IconButton(onClick={vm.toggleSaved(a)}){Icon(if(a.saved)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"حفظ الخبر العالمي",tint=Accent)}
        }
        if(a.summary.isNotBlank())Text(summary?.translated?:a.summary,maxLines=3,style=MaterialTheme.typography.bodyMedium,color=Muted)
        if((title?.engine=="MLKit"||summary?.engine=="MLKit")&&meta.language!="ar")WorldGoogleAttribution()
        if(WorldRights.needsAuthor(p.website))LinkButton("https://creativecommons.org/licenses/by/3.0/","CC BY 3.0 • الأصل مرجعك والترجمة آلية")
        if(title!=null||summary!=null)Caption("مترجم آليًا • ${listOfNotNull(title?.engine,summary?.engine).distinct().joinToString(" / ")}") else Caption(when{translating->"تُجهّز العربية محليًا؛ الأصل متاح";!allowed->WorldRights.localBlock(p,meta);translationProblem.isNotBlank()->translationProblem;!auto->"الترجمة المحلية التلقائية متوقفة في إعدادات الترجمة";else->"العربية غير جاهزة؛ افتح المقال لإعادة المحاولة"})
        NewsTimestamp(a.published)
    }
}
