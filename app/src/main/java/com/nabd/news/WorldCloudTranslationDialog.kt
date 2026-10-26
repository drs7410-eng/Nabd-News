package com.nabd.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun WorldCloudTranslationDialog(vm:NabdViewModel,a:Article,meta:WorldArticle,publisher:WorldPublisher,close:(String)->Unit) {
    var engine by remember{mutableStateOf(vm.settings.defaultProvider().takeIf{it in setOf("OpenAI","Gemini")}?:"OpenAI")}
    var selection by remember{mutableStateOf("النص المتاح كاملًا")};var index by remember{mutableIntStateOf(0)};var choosing by remember{mutableStateOf(false)}
    var working by remember{mutableStateOf(false)};var progress by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    var job by remember{mutableStateOf<Job?>(null)};val scope=rememberCoroutineScope()
    val parts=remember(a.title,a.summary,a.fullContent,a.contentBlocks,a.image){WorldTextParts.article(a).filter{it.original.isNotBlank()&&(publisher.allowImages||!it.slot.startsWith("caption-"))}}
    val chosen=if(selection=="فقرة محددة")listOfNotNull(parts.getOrNull(index))else parts
    val model=vm.settings.model(engine,"ترجمة تحريرية")
    val conservativeInput=chosen.sumOf{it.original.toByteArray().size*4L+EDITORIAL_TRANSLATION_INSTRUCTIONS.toByteArray().size+4096}
    val estimate=vm.ai.price(engine,model)?.cost(conservativeInput,chosen.size*AiPolicy.MAX_OUTPUT.toLong())
    val allowed=WorldRights.cloud(publisher,meta)
    if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text("اختر فقرة من النص المتاح")},text={LazyColumn(Modifier.heightIn(max=420.dp)) {
        itemsIndexed(parts,key={_,p->p.slot}){i,p->TextButton(onClick={index=i;choosing=false}){Text("${i+1} • ${p.original.take(130)}",maxLines=4)}}
    }},confirmButton={TextButton(onClick={choosing=false}){Text("إغلاق")}})
    AlertDialog(onDismissRequest={if(!working)close("")},title={Text("ترجمة تحريرية باختيارك")},text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(publisher.name,style=MaterialTheme.typography.titleMedium)
        ChoiceRow(listOf("OpenAI","Gemini"),engine){if(!working)engine=it}
        Caption("النموذج: ${model.ifBlank{"اضبطه من إعدادات AI الحالية"}}")
        Picker("النطاق",listOf("النص المتاح كاملًا","فقرة محددة"),selection){if(!working)selection=it}
        if(selection=="فقرة محددة")OutlinedButton(onClick={choosing=true},enabled=!working){Text("الفقرة ${index+1} • ${parts.getOrNull(index)?.original?.take(65).orEmpty()}")}
        Caption("${chosen.size} جزءًا؛ طلب واحد لكل جزء غير محفوظ بالنموذج نفسه. إعادة فتح المقال لا ترسل طلبًا سحابيًا. الأصل لا يتغير.")
        Caption("حد إدخال تقديري محافظ: $conservativeInput Token • سقف إخراج لكل طلب ${AiPolicy.MAX_OUTPUT}")
        Caption("تقدير أعلى محلي عند توفر التسعير: ${money(estimate)}. الاستهلاك الفعلي يسجله المزود وقد يختلف؛ الميزانية الحالية تُراجع قبل كل طلب.")
        Text("سيُرسل النص الأصلي المحدد إلى $engine. لا تشمل العملية ملاحظاتك. لا تُنفّذ إذا كان إذن الناشر يمنع النقل أو الترجمة.",color=Accent)
        if(a.isExcerpt)Caption("المصدر جزئي أو مقتطف؛ تحسين ترجمته لا يحوّله إلى مقال كامل.",Accent)
        if(!allowed)Text("هذا المصدر أو هذا المقال لا يملك إذن معالجة سحابية مسجلًا، أو ينقصه اسم الكاتب المطلوب.",color=Accent)
        if(engine=="Gemini")Caption("يُشترط إقرار الفئة المجانية من إعدادات الترجمة مع إعدادات النموذج الحالية. لا انتقال إلى نموذج آخر عند نفاد الحصة.")
        if(chosen.size>80)Text("النص أكبر من حد 80 جزءًا للعملية السحابية. اختر فقرة محددة؛ لم يُقطع المقال أو يُرسل جزء منه على أنه كامل.",color=Accent)
        if(working){LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress)}
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={
        working=true;error=""
        job=scope.launch {try {
            require(chosen.size in 1..80)
            chosen.forEachIndexed{i,part->ensureActive();progress="حفظ ومراجعة الجزء ${i+1} من ${chosen.size}";vm.worldTranslation.translate(a.id,part.slot,part.original,engine)}
            close(engine)
        }catch(e:CancellationException){throw e}catch(e:Exception){error=safeError(e)}finally{working=false}}
    },enabled=!working&&allowed&&chosen.size in 1..80&&model.isNotBlank()){Text("إرسال وتحسين الترجمة")}},dismissButton={TextButton(onClick={job?.cancel();close("")}){Text(if(working)"إيقاف — قد يكون الطلب الجاري احتُسب"else"إلغاء")}})
}
