package com.nabd.news

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

@Composable fun MediaScreen(vm:NabdViewModel) {
    val clips by vm.clips.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val busy by vm.mediaBusy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("فيديو") }
    var config by rememberSaveable { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Pair<MediaTarget,String>?>(null) }
    var reelSession by remember { mutableStateOf<Pair<List<MediaClip>,Int>?>(null) }
    reelSession?.let { (items,start) -> ReelsScreen(vm,items,start) { reelSession=null } }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var xUrl by rememberSaveable { mutableStateOf("") }
    var xName by rememberSaveable { mutableStateOf("") }
    playing?.let { (target,title) -> MediaPlayer(target,title,close={playing=null},autoPlay=true,muted=false) }
    if(config) MediaSettings(vm) { config=false; version++ }
    val interests = remember(version) { vm.settings.get("mediaTopics","تقنية,الخليج").split(',').filter { it.isNotBlank() }.toSet() }
    val region = remember(version) { vm.settings.get("mediaRegion","الخليج") }
    val personalized = remember(version) { vm.settings.get("mediaLearning","true") == "true" }
    val keywords = remember(version) { vm.settings.get("mediaKeywords") }
    val excluded = remember(version) { vm.settings.get("mediaExclude").split(',', '،').map { it.trim() }.filter { it.isNotBlank() } }
    val ranked = remember(clips,interests,region,personalized,keywords,excluded,category,sources,tab) {
        if(tab=="المفضلة") clips.filter { it.favorite && (category=="الكل" || it.topic==category) }.sortedByDescending { it.published }
        else MediaPolicy.rank(clips.filter { c -> (c.sourceId=="manual-links" || sources.any{it.id==c.sourceId}) && (category=="الكل" || c.topic==category) && excluded.none { c.title.contains(it,true) } },interests,region,keywords,personalized,System.currentTimeMillis())
    }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            ChoiceRow(listOf("فيديو","المفضلة","متابعة X"),tab){tab=it}
            IconButton(onClick={config=true}){Icon(Icons.Outlined.Tune,"تفضيلات الفيديو ومصادره")}
        }  } }
        if(tab!="متابعة X") {
            item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  SectionTitle(if(tab=="المفضلة")"مفضلة المقاطع" else "شاهد ما يهمك",if(tab=="المفضلة") "مقاطع حفظتها للعودة إليها؛ تبقى محفوظة عند إعادة تعيين التوصيات." else if(personalized) "ترتيب محلي يوازن اهتماماتك وتنوع المصادر والتفاعل وحداثة المقاطع؛ لا يُرسل سجل التفاعل إلى AI." else "الأحدث من مصادرك، دون تخصيص.")
                TextButton(onClick=vm::refreshMedia,enabled=!busy){Text(if(busy)"جارٍ تحديث المصادر…" else "تحديث المقاطع")}
                ChoiceRow(listOf("الكل")+MediaPolicy.topics,category){category=it}
             } }
            if(ranked.isEmpty()) item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  BoxCard {
                Text(if(tab=="المفضلة")"لم تحفظ مقاطع في المفضلة بعد" else "لا توجد مقاطع متاحة بهذه التفضيلات بعد")
                Caption("حدّث مصادر الأخبار للعثور على روابط الفيديو المنشورة في خلاصاتها، أو أضف قناة YouTube أو خلاصة فيديو من التفضيلات. لا تُعرض مقاطع تجريبية.")
                Button(onClick={config=true}){Text("اختيار مصادر الفيديو")}
            }  } }
            if(ranked.isNotEmpty()) item { Button(onClick={reelSession=ranked.toList() to 0},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.PlayArrow,null);Text("بدء المشاهدة المتتابعة")} }
            items(ranked,key={it.id}) { clip ->
                var menu by remember { mutableStateOf(false) }
                BoxCard {
                    if(clip.thumbnail.isNotBlank()) NewsImage(clip.thumbnail,clip.title)
                    Text(clip.title,style=MaterialTheme.typography.titleMedium)
                    Caption("${clip.source} • ${clip.provider} • ${clip.topic}",Mint)
                    if(clip.published>0) Caption(dateText(clip.published))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Button(onClick={vm.task{vm.dao.openClip(clip.id,System.currentTimeMillis())}; reelSession=ranked.toList() to ranked.indexOfFirst{it.id==clip.id}.coerceAtLeast(0)}) {
                            Icon(Icons.Outlined.PlayArrow,null);Text("مشاهدة")
                        }
                        Box {
                            IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"خيارات المقطع")}
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text(if(clip.favorite)"إزالة من مفضلة المقاطع" else "حفظ في مفضلة المقاطع")},onClick={menu=false;vm.task{vm.dao.favoriteClip(clip.id,!clip.favorite)}})
                                DropdownMenuItem(text={Text(if(clip.liked)"إزالة الإعجاب" else "يعجبني • اقترح المزيد")},onClick={menu=false;vm.task{vm.dao.likeClip(clip.id,!clip.liked)}})
                                DropdownMenuItem(text={Text("إخفاء هذا المقطع")},onClick={menu=false;vm.task{vm.dao.hideClip(clip.id,true)}})
                            }
                        }
                    }
                }
            }
        } else {
            item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  BoxCard {
                SectionTitle("منشورات وحسابات X", "عرض رسمي داخل التطبيق. الحسابات الخاصة أو المحتوى المحذوف أو المقيّد لا يمكن عرضه. ليست هذه خلاصة RSS أو نسخة محفوظة من منشورات X.")
                OutlinedTextField(xUrl,{xUrl=it.trim()},Modifier.fillMaxWidth(),label={Text("رابط حساب عام أو منشور X")})
                OutlinedTextField(xName,{xName=it.take(100)},Modifier.fillMaxWidth(),label={Text("اسم للمتابعة")})
                Button(onClick={vm.task{
                    val canonical=MediaPolicy.xLink(xUrl) ?: error("أدخل رابط حساب أو منشور عام صالحًا على X")
                    vm.dao.saveSource(Source(id="x-${AiPolicy.hash(canonical)}",name=xName.ifBlank{canonical.substringAfter("x.com/")},url=canonical,type="X"))
                    xUrl="";xName=""
                }}){Text("إضافة للمتابعة")}
            }  } }
            items(sources.filter{it.type=="X"},key={"social-${it.id}"}) { source -> BoxCard {
                Text(source.name);Caption(source.url)
                Button(onClick={MediaPolicy.xLink(source.url)?.let{playing=MediaTarget("X",it) to source.name}}){Text(if(source.url.contains("/status/"))"عرض المنشور داخل نبض" else "عرض منشورات الحساب داخل نبض")}
                TextButton(onClick={vm.task{vm.dao.deleteSource(source.id)}}){Text("إزالة المتابعة")}
            } }
        }
    }
}
@Composable private fun MediaSettings(vm:NabdViewModel,close:()->Unit) {
    var topics by remember { mutableStateOf(vm.settings.get("mediaTopics","تقنية,الخليج").split(',').filter{it.isNotBlank()}.toSet()) }
    var region by remember { mutableStateOf(vm.settings.get("mediaRegion","الخليج")) }
    var learning by remember { mutableStateOf(vm.settings.get("mediaLearning","true")=="true") }
    var words by remember { mutableStateOf(vm.settings.get("mediaKeywords")) }
    var exclude by remember { mutableStateOf(vm.settings.get("mediaExclude")) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceTopic by remember { mutableStateOf("أخبار") }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("اهتماماتي") }
    var clipUrl by remember { mutableStateOf("") }
    var clipTitle by remember { mutableStateOf("") }
    val sources by vm.sources.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=close,title={Text("تجربة مشاهدة تناسبك")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  ChoiceRow(listOf("اهتماماتي","المصادر"),section){section=it}  } }
            if(section=="اهتماماتي") {
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Caption("مواضيعك") ; MediaPolicy.topics.forEach { topic ->
                    Row { Checkbox(topic in topics,{topics=if(it)topics+topic else topics-topic});Text(topic) }
                }  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  ChoiceRow(listOf("الخليج","العالم"),region){region=it}  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  OutlinedTextField(words,{words=it.take(1000)},label={Text("كلمات تهمك، مفصولة بفواصل")})  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  OutlinedTextField(exclude,{exclude=it.take(1000)},label={Text("إخفاء عناوين تحتوي كلمات")})  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Row { Switch(learning,{learning=it});Text("تخصيص الترتيب محليًا") };Caption("المنطقة تفضيل للكلمات والعناوين، وليست تحديدًا جغرافيًا مضمونًا أو قائمة ترند عالمية.")  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  TextButton(onClick={vm.task{vm.dao.resetMediaLearning();message="مُسح سجل التوصيات؛ بقيت قائمة المفضلة محفوظة"}}){Text("إعادة تعيين سجل التوصيات") }  } }
            } else {
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  SectionTitle("إضافة مقطع من منصة")
                    OutlinedTextField(clipUrl,{clipUrl=it.trim()},label={Text("رابط فيديو عام")})
                    OutlinedTextField(clipTitle,{clipTitle=it.take(1000)},label={Text("عنوان تختاره للمقطع")})
                    Picker("التصنيف",MediaPolicy.topics,sourceTopic){sourceTopic=it}
                    TextButton(onClick={vm.task{try{vm.media.addClip(clipUrl,clipTitle,sourceTopic);clipUrl="";clipTitle="";message="حُفظ رابط المقطع"}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=safeError(e)}}}){Text("حفظ المقطع داخل نبض")}
                 } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  SectionTitle("قنوات مقترحة")
                    listOf("BBC نيوز عربي" to "UCelk6aHijZq-GJBBB9YpReA", "الجزيرة" to "UCfiwzLy-8yKzIbsmZTzxDgw").forEach { (name,id) ->
                        TextButton(onClick={adding=true;vm.task{try{vm.media.addSource(name,"https://www.youtube.com/channel/$id","أخبار");message="أُضيفت $name"}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=safeError(e)}finally{adding=false}}},enabled=!adding){Text("إضافة $name وفحصها")}
                    }
                    Caption("لا تُضاف قناة حتى تصل خلاصة عامة صالحة من اتصالك.")
                 } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Caption("أضف قناة YouTube العامة برابطها، أو RSS/Atom ينشر روابط فيديو أو ملفات MP4/HLS مباشرة. تُكتشف خلاصة القناة المعلنة ويُفحص المحتوى قبل الحفظ.")  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  OutlinedTextField(sourceName,{sourceName=it.take(100)},label={Text("اسم المصدر")})  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  OutlinedTextField(sourceUrl,{sourceUrl=it.trim()},label={Text("رابط قناة YouTube أو RSS للفيديو")})  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Picker("موضوع القناة",MediaPolicy.topics,sourceTopic){sourceTopic=it}  } }
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Button(onClick={adding=true;vm.task{try{vm.media.addSource(sourceName,sourceUrl,sourceTopic);sourceName="";sourceUrl="";message="أُضيف المصدر وحُمّلت مقاطعه"}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=safeError(e)}finally{adding=false}}},enabled=!adding){Text(if(adding)"جارٍ الفحص…" else "إضافة وتفعيل") }  } }
                items(sources.filter{it.type=="VIDEO"},key={it.id}){s->Column{Text(s.name);Caption(s.url);TextButton(onClick={vm.task{vm.dao.deleteSource(s.id)}}){Text("إيقاف متابعة المصدر")}}}
                item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Caption("YouTube وVimeo وTikTok وInstagram وX عبر تضمين المنصة؛ والملفات المباشرة عبر مشغل Android. منع التضمين أو الحقوق أو تسجيل الدخول قد يمنع التشغيل. يتصل المشغل بالمنصة عند فتحه، وقد تسجل المنصة المشاهدة. لا تنزيل خفي ولا تجاوز للحجب.")  } }
            }
            if(message.isNotBlank()) item { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {  Caption(message,Mint)  } }
        }
    },confirmButton={TextButton(onClick={
        vm.settings.set("mediaTopics",topics.joinToString(","));vm.settings.set("mediaRegion",region);vm.settings.set("mediaLearning",learning.toString())
        vm.settings.set("mediaKeywords",words);vm.settings.set("mediaExclude",exclude);close()
    }){Text("حفظ وإغلاق")}})
}
