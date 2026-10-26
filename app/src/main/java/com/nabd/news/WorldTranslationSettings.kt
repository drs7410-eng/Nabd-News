@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nabd.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun WorldTranslationSettings(vm:NabdViewModel) {
    val glossary by vm.worldGlossary.collectAsStateWithLifecycle();val statuses by vm.worldModelStatus.collectAsStateWithLifecycle();val downloads by vm.worldDownloads.collectAsStateWithLifecycle()
    var language by rememberSaveable{mutableStateOf("en")};var wifi by rememberSaveable{mutableStateOf(true)}
    val version by vm.configVersion.collectAsStateWithLifecycle()
    val auto=remember(version){vm.settings.get("worldAutoLocal","false")=="true"};var confirmDownload by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<WorldGlossary?>(null)};var adding by remember{mutableStateOf(false)}
    var free by remember{mutableStateOf(vm.settings.get("worldGeminiFreeConfirmed")=="true")}
    LaunchedEffect(language){vm.checkWorldModel(language)}
    if(adding||editing!=null)WorldGlossaryDialog(vm,editing){adding=false;editing=null}
    if(confirmDownload)AlertDialog(onDismissRequest={confirmDownload=false},title={Text("تنزيل نموذج ترجمة على الجهاز؟")},text={Text("سينزّل ML Kit من Google النماذج اللازمة للترجمة من ${worldLanguages[language]?:language} إلى العربية. قد يكون الحجم عشرات MB. النصوص تُترجم محليًا ولا توجد رسوم لكل ترجمة. ${if(wifi)"سيُنتظر اتصال Wi-Fi عند الحاجة." else "سمحت باستخدام اتصال الهاتف وقد تترتب كلفة بيانات من مشغلك."} لا يلزم مفتاح API.")},confirmButton={TextButton(onClick={confirmDownload=false;vm.downloadWorldModel(language,wifi)}){Text("تنزيل وإعداد")}},dismissButton={TextButton(onClick={confirmDownload=false}){Text("إلغاء")}})
    LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
        item { SceneCard {
            Text("ترجمة هادئة، تحت سيطرتك",style=MaterialTheme.typography.headlineSmall)
            Caption("تُترجم العناوين والمقتطفات الظاهرة والفقرات عند القراءة فقط، بعد جاهزية النموذج وأذونات المصدر. لا تحميل آلي لكل المقالات ولا إرسال سحابي تلقائي.")
            Row{Switch(auto,{vm.settings.set("worldAutoLocal",it.toString());vm.configVersion.value++});Text("ترجمة محلية تلقائية أثناء التصفح",Modifier.weight(1f))}
            WorldLanguagePicker("لغة النموذج",language,includeArabic=false){language=it}
            Caption("${worldLanguages[language]} ← العربية • ${statuses[language]?:"جارٍ فحص النموذج"}",Mint)
            Row{Switch(wifi,{wifi=it});Text("تنزيل النماذج عبر Wi-Fi فقط")}
            Button(onClick={confirmDownload=true},enabled=language !in downloads){Text(if(language in downloads)"جارٍ التنزيل؛ راجع اتصال Wi-Fi" else "إعداد أو تنزيل النموذج")}
            Caption("الترجمة المحلية عامة وليست تدقيقًا بشريًا. اللغات غير الإنجليزية قد تمر عبر الإنجليزية؛ اختبر دقة العربية والمصطلحات. لا يُدّعى اعتماد جودتها الصحفية قبل تجربة الجهاز.")
            LinkButton("https://developers.google.com/ml-kit/language/translation/translation-terms","إرشادات الترجمة والنسبة إلى Google")
        } }
        item { SceneCard {
            Text("تحسين سحابي عند الطلب",style=MaterialTheme.typography.titleLarge)
            Caption("OpenAI وGemini يستخدمان مفاتيح وإعدادات نبض الموجودة. من قائمة القارئ اختر تحسين النص المتاح أو فقرة بعينها، ثم راجع عدد الطلبات والتكلفة. لا انتقال تلقائي بين المحركات.")
            Row{Checkbox(free,{free=it;vm.settings.set("worldGeminiFreeConfirmed",it.toString())});Text("أستخدم Gemini ضمن فئة مجانية دون فوترة مفعّلة",Modifier.weight(1f))}
            Caption("هذا إقرار إعداد حسابك، وليس اكتشافًا آليًا للفئة أو ضمانًا للفوترة. راجع حصة حسابك؛ عند تجاوزها يظهر الخطأ دون التحويل إلى نموذج آخر. لا ترسل النصوص إذا كانت شروط الناشر تمنع ذلك.")
        } }
        item { SceneCard { Text("القاموس الصحفي",style=MaterialTheme.typography.titleLarge);Caption("مصطلحات معتمدة بحسب اللغة والمجال. تثبت الأسماء والمصطلحات والأرقام برموز حماية، وتُرفض المخرجات إذا أفسدت هذه الرموز. تغيير القاموس ينشئ نسخة ترجمة جديدة دون حذف القديمة.");FilledTonalButton(onClick={adding=true}){Text("إضافة مصطلح")}} }
        items(glossary,key={it.id}){g->SceneCard {
            Text("${g.original} ← ${g.arabic}",style=MaterialTheme.typography.titleMedium);Caption("${g.language} • ${g.domain} • ${if(g.active)"فعال" else "معطل"}")
            if(g.reference.isNotBlank())Caption("المصدر اللغوي: ${g.reference}")
            if(g.notes.isNotBlank())Text(g.notes,style=MaterialTheme.typography.bodySmall)
            FlowRow{TextButton(onClick={editing=g}){Text("تعديل")};TextButton(onClick={vm.task{vm.dao.saveWorldGlossary(g.copy(active=!g.active,updated=System.currentTimeMillis()))}}){Text(if(g.active)"تعطيل"else"تفعيل")};TextButton(onClick={vm.task{vm.dao.deleteWorldGlossary(g.id)}}){Text("حذف المصطلح")}}
        } }
    }
}
@Composable private fun WorldGlossaryDialog(vm:NabdViewModel,old:WorldGlossary?,close:()->Unit) {
    var original by remember{mutableStateOf(old?.original.orEmpty())};var arabic by remember{mutableStateOf(old?.arabic.orEmpty())};var language by remember{mutableStateOf(old?.language?:"en")}
    var domain by remember{mutableStateOf(old?.domain?:"عام")};var reference by remember{mutableStateOf(old?.reference.orEmpty())};var notes by remember{mutableStateOf(old?.notes.orEmpty())};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("مصطلح صحفي معتمد")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(original,{original=it.take(200)},label={Text("المصطلح الأصلي")})
        OutlinedTextField(arabic,{arabic=it.take(200)},label={Text("المقابل العربي المعتمد")})
        WorldLanguagePicker("اللغة",language){language=it}
        Picker("المجال",listOf("عام")+worldCategories,domain){domain=it}
        OutlinedTextField(reference,{reference=it.take(1000)},label={Text("المصدر اللغوي أو المرجع")})
        OutlinedTextField(notes,{notes=it.take(2000)},label={Text("ملاحظات الاستخدام — لا تُرسل إلى AI")})
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{
        require(original.isNotBlank()&&arabic.isNotBlank()){"أدخل المصطلح ومقابله"}
        require(EditorialProtection.numbers(original)==EditorialProtection.numbers(arabic)){"لا تغيّر الأرقام في المصطلح المعتمد"}
        require(vm.dao.allWorldGlossary().none{it.id!=old?.id&&it.language==language&&it.domain==domain&&it.original.equals(original.trim(),true)}){"يوجد مصطلح مطابق في هذا المجال؛ عدّله بدل تكراره"}
        vm.dao.saveWorldGlossary(WorldGlossary(old?.id?:newId(),original.trim(),arabic.trim(),domain,language,reference,notes,old?.active?:true));close()
    }catch(e:Exception){error=safeError(e)}}}){Text("حفظ المصطلح")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
