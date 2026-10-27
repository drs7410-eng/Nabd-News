@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nabd.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

val worldLanguages:Map<String,String> = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages().sortedWith(compareBy<String>{listOf("en","fr","de","es","it","ja","ko","zh","pt","ar").indexOf(it).takeIf{n->n>=0}?:100}.thenBy{it}).associateWith{java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.forLanguageTag("ar"))}
@Composable fun WorldLanguagePicker(label:String,code:String,includeArabic:Boolean=true,choose:(String)->Unit) {
    val options=worldLanguages.filterKeys{includeArabic||it!="ar"}
    Picker(label,options.values.toList(),options[code]?:code){value->options.entries.firstOrNull{it.value==value}?.let{choose(it.key)}}
}

@Composable fun WorldSourcesScreen(vm:NabdViewModel,browse:(String)->Unit) {
    val publishers by vm.worldPublishers.collectAsStateWithLifecycle();val feeds by vm.worldFeeds.collectAsStateWithLifecycle();val metadata by vm.worldArticles.collectAsStateWithLifecycle()
    val modelStates by vm.worldModelStatus.collectAsStateWithLifecycle()
    val downloads by vm.worldDownloads.collectAsStateWithLifecycle()
    var modelLanguage by remember{mutableStateOf("")}
    LaunchedEffect(publishers.map{it.id to it.enabled}){publishers.filter{it.enabled&&it.allowLocal}.map{it.language}.distinct().forEach{vm.checkWorldModel(it)}}
    if(modelLanguage.isNotBlank())AlertDialog(onDismissRequest={modelLanguage=""},title={Text("إعداد العربية لهذه الصحيفة")},text={Text("تنزيل نموذج ${worldLanguages[modelLanguage]?:modelLanguage} إلى العربية من Google، ثم تُترجم الأخبار الظاهرة والمقال عند فتحه على الجهاز. قد يحتاج التنزيل عشرات MB؛ لا إرسال سحابي ولا رسوم ترجمة.")},confirmButton={TextButton(onClick={val language=modelLanguage;modelLanguage="";vm.downloadWorldModel(language,true)}){Text("تنزيل عبر Wi-Fi")}},dismissButton={TextButton(onClick={val language=modelLanguage;modelLanguage="";vm.downloadWorldModel(language,false)}){Text("استخدام اتصال الهاتف")}})
    var query by rememberSaveable{mutableStateOf("")};var adding by remember{mutableStateOf(false)};var rights by remember{mutableStateOf<WorldPublisher?>(null)}
    var working by remember{mutableStateOf<Set<String>>(emptySet())}
    var setupMessages by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var advanced by rememberSaveable{mutableStateOf<Set<String>>(emptySet())}
    var continueAfterPermission by remember{mutableStateOf("")}
    fun activate(id:String){
        if(id in working)return
        working=working+id;setupMessages=setupMessages+(id to "جارٍ اكتشاف RSS وفحصه وإكمال إعداد الصحيفة…")
        vm.task {try{
            val result=vm.world.activate(id);setupMessages=setupMessages+(id to result.message)
            when(result.state){
                "permission_required"->{continueAfterPermission=id;rights=vm.dao.worldPublisher(id)}
                "ready"->{vm.notices.emit(result.message);browse(id)}
            }
        }finally{working=working-id}}
    }
    if(adding)WorldAddSourceDialog(vm){adding=false}
    rights?.let{p->WorldPermissionDialog(vm,p){
        rights=null
        if(continueAfterPermission==p.id){continueAfterPermission="";vm.task{val saved=vm.dao.worldPublisher(p.id);if(saved?.allowCache==true)activate(p.id)}}
    }}
    LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
        item { SceneCard {
            Text("صحف من العالم",style=MaterialTheme.typography.headlineSmall)
            SearchField(query,{query=it},"ابحث بالاسم أو البلد أو اللغة")
            FilledTonalButton(onClick={adding=true}){Icon(Icons.Outlined.Add,null);Text("إضافة صحيفة أو خلاصة")}
            Caption("اختر الصحيفة وسيتولى نبض اكتشاف RSS وفحصه وضبط اللغة والقسم والتفعيل. إذا لم تُعرف حقوق المعالجة سيطلب استكمالها مرة واحدة؛ لا روابط وهمية ولا تجاوز للاشتراكات.")
        } }
        items(publishers.filter{query.isBlank()||"${it.name} ${it.arabicName} ${it.country} ${it.language}".contains(query,true)},key={it.id}){p->SceneCard {
            Row { Surface(shape=MaterialTheme.shapes.medium,color=Accent.copy(alpha=.12f)){Text(p.name.take(2).uppercase(),Modifier.padding(12.dp),color=Accent)}
                Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(p.name,style=MaterialTheme.typography.titleMedium);Caption("${p.arabicName} • ${p.country}");Caption(worldLanguages[p.language]?:p.language)} }
            val pf=feeds.filter{it.publisherId==p.id}
            val count=metadata.count{it.publisherId==p.id}
            Caption("$count خبر محفوظ محليًا • ${if(p.enabled)"مفعّل" else "غير مفعّل"}")
            Button(onClick={activate(p.id)},enabled=p.id !in working,modifier=Modifier.fillMaxWidth()){
                if(p.id in working){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                Text(if(p.id in working)"جارٍ إعداد الصحيفة…"else if(p.enabled)"تحديث وفتح الصحيفة"else"اختيار وتفعيل الصحيفة")
            }
            setupMessages[p.id]?.let{Caption(it,Accent)}
            if(p.enabled&&!p.allowLocal) {
                Caption("RSS مفعل؛ الترجمة تحتاج مراجعة أذونات المصدر مرة واحدة")
                TextButton(onClick={rights=p}){Text("إعداد ترجمة هذه الصحيفة")}
            }
            if(p.allowLocal) {
                Caption("الترجمة المحلية • ${worldLanguages[p.language]?:p.language} • ${modelStates[p.language]?:"يُفحص النموذج عند التفعيل"}",Mint)
                if(modelStates[p.language]!="جاهز محليًا")TextButton(onClick={modelLanguage=p.language},enabled=p.language !in downloads){Text(if(p.language in downloads)"جارٍ تجهيز العربية…" else "تجهيز العربية لهذه الصحيفة")}
            }
            TextButton(onClick={advanced=if(p.id in advanced)advanced-p.id else advanced+p.id}){Text(if(p.id in advanced)"إخفاء الإعدادات المتقدمة"else"تفاصيل المصدر والإعدادات المتقدمة")}
            if(p.id in advanced) {
            LinkButton(p.website,"الموقع الرسمي للصحيفة")
            if(pf.isEmpty())Caption("لا RSS متحقق منه بعد؛ يمكن فحص الروابط المعلنة في الموقع")
            pf.forEach { f->
                androidx.compose.foundation.text.selection.SelectionContainer{Caption(f.url)}
                Picker("قسم الخلاصة — تصنيفك",worldCategories,f.category){category->vm.task{vm.world.changeCategory(f,category)}}
                Caption("${f.category} • ${when(f.status){"verified"->"خلاصة تم الوصول إليها";"stale"->"تعذر التحديث؛ يوجد فحص سابق";"failed"->"فشل الفحص";else->"رابط مرشح غير مختبر"}}",if(f.succeeded>0)Mint else Muted)
                Caption("HTTP ${f.httpCode.takeIf{it>0}?:"غير متاح"} • آخر وصول ناجح: ${if(f.succeeded>0)dateText(f.succeeded)else "لم يُثبت"}")
                if(f.succeeded>0)Caption("عينة RSS: ${f.articleCount} مادة • حقل نص موسّع ${f.fullCount} • صور ${f.imageCount}. الحقل الموسع لا يثبت اكتمال المقال.")
                if(f.latestPublished>0)Caption("أحدث نشر في العينة: ${dateText(f.latestPublished)}")
                if(f.error.isNotBlank())Text(f.error,color=Accent,style=MaterialTheme.typography.bodySmall)
                FlowRow { TextButton(onClick={working=working+p.id;vm.task{try{vm.world.check(f,false)}finally{working=working-p.id}}},enabled=p.id !in working){Text("فحص RSS")}
                    TextButton(onClick={working=working+p.id;vm.task{try{val r=vm.world.check(f,true);vm.notices.emit(if(r.status=="verified")"تم تفعيل الخلاصة الحقيقية" else r.error)}finally{working=working-p.id}}},enabled=p.allowCache&&p.id !in working){Text("فحص وتفعيل")}
                }
            }
            Caption(p.rightsNote)
            FlowRow {
                TextButton(onClick={working=working+p.id;vm.task{try{val found=vm.world.discover(p);vm.notices.emit(if(found.isEmpty())"لم تُكتشف خلاصة صالحة؛ لم تُخترع روابط"else "تم التحقق من ${found.size} خلاصات؛ راجع الأذونات ثم فعّل ما تريد")}finally{working=working-p.id}}},enabled=p.id !in working){Text(if(p.id in working)"جارٍ الفحص…"else "اكتشاف خلاصات الموقع")}
                TextButton(onClick={rights=p}){Text("أذونات المعالجة")}
                TextButton(onClick={browse(p.id)},enabled=count>0){Text("تصفح الصحيفة")}
            }
            if(p.enabled)TextButton(onClick={vm.task{vm.dao.saveWorldPublisher(p.copy(enabled=false))}}){Text("إيقاف تحديث الصحيفة")}
            }
        } }
    }
}
@Composable private fun WorldAddSourceDialog(vm:NabdViewModel,close:()->Unit) {
    var name by remember{mutableStateOf("")};var url by remember{mutableStateOf("")};var website by remember{mutableStateOf("")}
    var country by remember{mutableStateOf("")};var lang by remember{mutableStateOf("en")};var category by remember{mutableStateOf("العالم")}
    var direct by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("إضافة مصدر عالمي")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name,{name=it.take(200)},label={Text("اسم الصحيفة")})
        Row{Switch(direct,{direct=it});Text("أملك رابط RSS مباشرة")}
        OutlinedTextField(website,{website=it.trim()},label={Text("رابط موقع الصحيفة HTTPS")})
        if(direct)OutlinedTextField(url,{url=it.trim()},label={Text("رابط RSS أو Atom")})
        OutlinedTextField(country,{country=it.take(100)},label={Text("البلد")})
        WorldLanguagePicker("لغة المصدر",lang){lang=it}
        Picker("قسم هذه الخلاصة",worldCategories,category){category=it}
        Caption("لن يُحفظ نص الصحيفة قبل تسجيل إذن الحفظ؛ فحص الخلاصة يعرض حالتها وقياساتها فقط.")
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{
        require(name.isNotBlank()&&WorldUrls.valid(website)){"أدخل اسمًا وموقع HTTPS عامًا صالحًا"}
        val p=WorldPublisher(AiPolicy.hash("world-publisher|$website"),name,name,website,country.ifBlank{"غير محدد"},lang)
        val known=WorldCatalog.entries.firstOrNull{WorldRights.knownRestricted(it.publisher.website)&&WorldRights.knownRestricted(website)}?.publisher
        vm.dao.saveWorldPublisher(vm.dao.worldPublisher(p.id)?:if(known!=null)p.copy(permission="restricted",allowCache=true,termsUrl=known.termsUrl,rightsNote=known.rightsNote)else p)
        if(direct){require(WorldUrls.valid(url)&&WorldUrls.samePublisher(website,url)){"نطاق RSS لا يطابق الصحيفة"};vm.world.check(WorldFeed(AiPolicy.hash("world-feed|$url"),p.id,url,category,lang))}
        else vm.world.discover(p)
        close()
    }catch(e:Exception){error=safeError(e)}}}){Text("فحص وإضافة")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable internal fun WorldPermissionDialog(vm:NabdViewModel,p:WorldPublisher,close:()->Unit) {
    var terms by remember{mutableStateOf(p.termsUrl)};var note by remember{mutableStateOf(p.rightsNote)}
    var cache by remember{mutableStateOf(p.allowCache)};var local by remember{mutableStateOf(p.allowLocal)};var cloud by remember{mutableStateOf(p.allowCloud)}
    var html by remember{mutableStateOf(p.allowHtml)};var images by remember{mutableStateOf(p.allowImages)};var confirmed by remember{mutableStateOf(p.permission=="recorded")};var error by remember{mutableStateOf("")}
    val restricted=p.permission=="restricted" || WorldRights.knownRestricted(p.website)
    AlertDialog(onDismissRequest=close,title={Text("حقوق ${p.name}")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("وجود RSS أو اشتراك شخصي لا يكفي للسماح بالاستخراج أو الترجمة أو الإرسال السحابي. سجّل فقط ما تسمح به شروط الناشر فعلًا.")
        if(restricted){Text(p.rightsNote,color=Accent);LinkButton(p.termsUrl,"شروط الناشر")}else {
            OutlinedTextField(terms,{terms=it.trim()},label={Text("رابط شروط أو إذن الناشر")})
            OutlinedTextField(note,{note=it.take(4000)},label={Text("نطاق الإذن والقيود والاستثناءات")},minLines=3)
            Row{Checkbox(confirmed,{confirmed=it});Text("راجعت إذن هذه الأفعال والاستثناءات لهذه الصحيفة")}
            listOf("حفظ النصوص المتاحة محليًا" to cache,"ترجمة محلية" to local,"إرسال للترجمة والتحليل السحابي" to cloud,"استخراج HTML المسموح" to html,"عرض الصور المرخصة المرتبطة فقط" to images).forEachIndexed { i,(title,value)->Row{Switch(value,{when(i){0->cache=it;1->local=it;2->cloud=it;3->html=it;4->images=it}});Text(title,Modifier.weight(1f))} }
            Caption("لا يُحمّل المقال أو يُرسل AI بمجرد حفظ الإذن. الصور غير مشمولة تلقائيًا بترخيص النص. الترجمات القديمة تبقى مؤرشفة ولا تُستبدل بنص ناشر.")
        }
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={if(restricted)close() else vm.task{try{
        require(confirmed && WorldUrls.valid(terms)&&note.isNotBlank()){"أكمل مراجعة الإذن ورابطه ونطاقه"}
        vm.world.permissions(p.copy(termsUrl=terms,rightsNote=note,permission="recorded",allowCache=cache,allowLocal=local,allowCloud=cloud,allowHtml=html,allowImages=images));close()
    }catch(e:Exception){error=safeError(e)}}}){Text(if(restricted)"إغلاق" else "حفظ الأذونات")}},dismissButton={if(!restricted)TextButton(onClick=close){Text("إلغاء")}})
}
