@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nabd.news

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** Stable pieces retain publisher block ordering; links use original offsets only in original mode. */
data class WorldReadingPiece(val part:WorldTextParts.Part,val block:ArticleBlock)
fun worldReadingPieces(a:Article):List<WorldReadingPiece> = if(a.fullContent.isBlank())emptyList()else ContentCodec.displayBlocks(a).flatMapIndexed { index,b ->
    if(b.type=="image")listOf(WorldReadingPiece(WorldTextParts.Part("image-$index",""),b))else {
        var offset=0
        WorldTextParts.part("block-$index",b.text).map{part->
            val start=offset;offset+=part.original.length
            val links=b.links.orEmpty().filter{it.start>=start&&it.end<=offset}.map{it.copy(start=it.start-start,end=it.end-start)}
            WorldReadingPiece(part,b.copy(text=part.original,links=links))
        }
    }
}
@Composable fun WorldReaderScreen(vm:NabdViewModel,a:Article,meta:WorldArticle,publisher:WorldPublisher,assistant:()->Unit,note:()->Unit,chat:()->Unit,configure:()->Unit) {
    val rows by remember(a.id){vm.dao.worldTranslations(a.id).distinctUntilChanged()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val glossary by vm.worldGlossary.collectAsStateWithLifecycle();val version by vm.configVersion.collectAsStateWithLifecycle();val loading by vm.loadingDetails.collectAsStateWithLifecycle()
    val history by vm.analyses.collectAsStateWithLifecycle();val notes by vm.notes.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=LocalContext.current;val clipboard=LocalClipboardManager.current
    val gh=remember(glossary,a.category){EditorialProtection.glossaryHash(glossary,a.category)}
    var original by rememberSaveable(a.id){mutableStateOf(false)};var compare by rememberSaveable(a.id){mutableStateOf(false)}
    var font by rememberSaveable{mutableFloatStateOf(vm.settings.get("articleFontSize","18").toFloatOrNull()?.coerceIn(16f,28f)?:18f)}
    var formatting by remember{mutableStateOf(false)};var more by remember{mutableStateOf(false)};var archive by remember{mutableStateOf(false)};var cloud by remember{mutableStateOf(false)}
    var permissions by remember{mutableStateOf(false)}
    var translating by remember{mutableStateOf(false)};var problem by remember{mutableStateOf("")};var retry by remember{mutableIntStateOf(0)};var expanded by rememberSaveable(a.id){mutableStateOf(false)}
    var preferred by rememberSaveable(a.id){mutableStateOf(vm.settings.get("worldPreferred_${a.id}","MLKit"))}
    val auto=remember(version){vm.settings.get("worldAutoLocal","true")=="true"}
    val allowed=WorldRights.local(publisher,meta)
    val pieces=remember(a.contentBlocks,a.fullContent,a.image){worldReadingPieces(a)}
    val titleParts=remember(a.title){WorldTextParts.part("title",a.title)}
    val summaryParts=remember(a.summary){WorldTextParts.part("summary",a.summary)}
    val shownSummary=if(expanded)summaryParts else summaryParts.take(2)
    val plan=remember(titleParts,shownSummary,pieces,publisher.allowImages){titleParts+shownSummary+pieces.flatMap{piece->if(piece.block.type!="image")listOf(piece.part)else if(publisher.allowImages)WorldTextParts.part(piece.part.slot.replace("image-","caption-"),piece.block.caption)else emptyList()}}
    fun cached(part:WorldTextParts.Part)=if(allowed||WorldRights.cloud(publisher,meta))WorldTranslationChoice.get(rows,part.slot,part.original,meta.language,gh,preferred,if(preferred=="MLKit")vm.worldLocal.version else null)
        ?:WorldTranslationChoice.get(rows,part.slot,part.original,meta.language,gh,"MLKit",vm.worldLocal.version)else null
    LaunchedEffect(a.id){vm.loadDetails(a.id)}
    LaunchedEffect(a.id,plan,gh,auto,version,allowed,retry) {
        problem=""
        if(auto&&allowed)try {
            require(plan.size<=400 && plan.sumOf{it.original.toByteArray().size}<=250000){"المقال أكبر من حد الترجمة المحلية لهذه الجلسة؛ الأصل متاح. اختر فقرات محددة للتحسين بدل قطع النص."}
            if(plan.all{cached(it)!=null})return@LaunchedEffect
            if(!vm.worldLocal.ready(meta.language)){problem="نموذج ${worldLanguages[meta.language]?:meta.language} ← العربية يحتاج تنزيلًا مرة واحدة من إعدادات الترجمة";return@LaunchedEffect}
            translating=true
            for(part in plan) {
                ensureActive()
                if(cached(part)==null)try{vm.worldTranslation.translate(a.id,part.slot,part.original)}catch(e:CancellationException){throw e}catch(e:Exception){problem=safeError(e)}
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){problem=safeError(e)}finally{translating=false}
    }
    if(permissions)WorldPermissionDialog(vm,publisher){permissions=false;vm.configVersion.value++}
    if(archive)AlertDialog(onDismissRequest={archive=false},title={Text("أرشيف الترجمات — نسخ قديمة محفوظة")},text={LazyColumn(Modifier.heightIn(max=470.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(rows.filter{it.state=="ready"&&(WorldRights.local(publisher,meta)||WorldRights.cloud(publisher,meta))},key={it.id}){r->SceneCard{Caption("${r.slot} • ${r.engine} • ${r.engineVersion} • ${dateText(r.created)}");Caption("هذه نسخة مؤرشفة؛ قد يختلف النص أو القاموس الحالي");SelectionContainer{Text(r.translated)};TextButton(onClick={clipboard.setText(AnnotatedString(r.translated))}){Text("نسخ هذه النسخة")}}}
    }},confirmButton={TextButton(onClick={archive=false}){Text("إغلاق")}})
    if(formatting)AlertDialog(onDismissRequest={formatting=false},title={Text("راحة القراءة")},text={Column{Text("حجم الخط ${font.toInt()}");Slider(font,{font=it;vm.settings.set("articleFontSize",it.toString())},valueRange=16f..28f);Row{Switch(compare,{compare=it});Text("مقارنة فقرة بفقرة مع الأصل")}}},confirmButton={TextButton(onClick={formatting=false}){Text("تم")}})
    if(cloud)WorldCloudTranslationDialog(vm,a,meta,publisher){engine->cloud=false;if(engine.isNotBlank()){preferred=engine;vm.settings.set("worldPreferred_${a.id}",engine);vm.configVersion.value++}}
    val listState=rememberLazyListState()
    LazyColumn(state=listState,contentPadding=PaddingValues(horizontal=22.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item(key="publisher") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Caption("${publisher.name} • ${publisher.country} • ${newsAge(a.published)} • ${dateText(a.published)}",Mint)
            if(meta.author.isNotBlank())Text("بقلم ${meta.author}",style=MaterialTheme.typography.labelLarge)
            LinkButton(a.url,"المقال الأصلي • ${publisher.name}")
            if(publisher.termsUrl.isNotBlank())LinkButton(publisher.termsUrl,"شروط إعادة الاستخدام والترجمة")
            if(WorldRights.needsAuthor(publisher.website))LinkButton("https://creativecommons.org/licenses/by/3.0/","CC BY 3.0 • النص من Global Voices؛ ترجمة آلية معدلة غير معتمدة من الناشر")
        } }
        if(publisher.allowImages&&a.image.isNotBlank())item(key="lead-image"){NewsImage(a.image,"صورة المقال الأصلية من ${publisher.name}")}
        item(key="title") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            titleParts.forEach{part->SelectionContainer{Text(if(original)part.original else cached(part)?.translated?:part.original,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,lineHeight=43.sp)}}
            if(!original&&titleParts.any{cached(it)!=null})Caption("مترجم آليًا • ${titleParts.mapNotNull{cached(it)?.engine}.distinct().joinToString(" / ")} • ليس صياغة الناشر الأصلية",Accent)
        } }
        stickyHeader(key="actions") { SceneCard {
            Row(horizontalArrangement=Arrangement.SpaceEvenly,modifier=Modifier.fillMaxWidth()) {
                TextButton(onClick={original=false}){Text("العربية",color=if(!original)Accent else Muted)}
                TextButton(onClick={original=true}){Text("الأصل",color=if(original)Accent else Muted)}
                IconButton(onClick={vm.toggleSaved(a)}){Icon(if(a.saved)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"حفظ الأصل والترجمة",tint=Accent)}
                IconButton(onClick=note){Icon(Icons.Outlined.EditNote,"تدوينة على المقال")}
                Box { IconButton(onClick={more=true}){Icon(Icons.Outlined.MoreVert,"خيارات المقال العالمي")}
                    DropdownMenu(more,{more=false}) {
                        DropdownMenuItem(text={Text("تنسيق الخط ومقارنة الأصل")},onClick={more=false;formatting=true})
                        DropdownMenuItem(text={Text("تحسين الترجمة أو إعادة ترجمة فقرة")},onClick={more=false;cloud=true})
                        DropdownMenuItem(text={Text("أرشيف الترجمات السابقة")},onClick={more=false;archive=true})
                        DropdownMenuItem(text={Text("إعداد الترجمة المحلية")},onClick={more=false;configure()})
                        DropdownMenuItem(text={Text("التلخيص والتحليل والتحقق")},onClick={more=false;assistant()})
                        DropdownMenuItem(text={Text("محادثة حول المقال")},onClick={more=false;chat()})
                        DropdownMenuItem(text={Text("استخدام الترجمة المحلية المحفوظة")},onClick={more=false;preferred="MLKit";vm.settings.set("worldPreferred_${a.id}","MLKit")})
                        DropdownMenuItem(text={Text("مشاركة رابط المصدر")},onClick={more=false;context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${a.title}\n${a.url}")},"مشاركة المقال"))})
                        DropdownMenuItem(text={Text("نسخ النص المعروض")},onClick={more=false
                            val ready=plan.all{cached(it)!=null}
                            if(original||ready){clipboard.setText(AnnotatedString(plan.joinToString("\n\n"){if(original)it.original else cached(it)!!.translated}));vm.task{vm.notices.emit("نُسخ النص المتاح؛ راجع حالة اكتمال المصدر")}}
                            else vm.task{vm.notices.emit("الترجمة لم تكتمل؛ انسخ فقرة محددة أو اعرض الأصل")}
                        })
                    }
                }
            }
            if(!original&&plan.any{cached(it)?.engine=="MLKit"})WorldGoogleAttribution()
            if(!original)Caption("الترجمة آلية وقد تخطئ في المعنى والمصطلحات؛ الأصل مرجعك دائمًا. لا تُضاف تتمة للمقال.")
        } }
        if(a.id in loading)item(key="article-loading"){Column{LinearProgressIndicator(Modifier.fillMaxWidth());Caption("جارٍ فحص متن المصدر المسموح؛ لا يُفتح متصفح تلقائيًا")}}
        item(key="availability"){SceneCard {
            Caption(if(a.isExcerpt)"المتاح مقتطف أو متن جزئي؛ ليس المقال الكامل"else "متن أصلي محفوظ • ${a.contentOrigin}",if(a.isExcerpt)Accent else Mint)
            if(a.isExcerpt)Text("المحتوى الكامل غير متاح أو لم يُثبت اكتماله داخل نبض. لا يُكمّل بالذكاء الاصطناعي.")
            if(a.detailReason.isNotBlank())Caption(a.detailReason)
            if(meta.restriction.isNotBlank())Text(meta.restriction,color=Accent)
            if(!allowed)Caption(WorldRights.localBlock(publisher,meta))
            if(problem.isNotBlank())Text(problem,color=Accent)
            if(!auto)TextButton(onClick={vm.settings.set("worldAutoLocal","true");vm.configVersion.value++}){Text("تفعيل الترجمة المحلية")}
            if(!allowed)TextButton(onClick={permissions=true}){Text("إعداد ترجمة هذه الصحيفة")}
            else if(problem.isNotBlank())FlowRow{TextButton(onClick={retry++}){Text("إعادة محاولة الترجمة المحلية")};TextButton(onClick=configure){Text("إعداد نموذج اللغة")}}
            TextButton(onClick={vm.loadDetails(a.id,true)},enabled=a.id !in loading){Text("إعادة فحص متن المصدر")}
            LinkButton(a.url,"فتح المصدر الأصلي باختيارك")
        } }
        if(shownSummary.isNotEmpty())item(key="summary"){SceneCard {
            Caption("مقدمة • مقتطف المصدر ${if(!original)"مع ترجمة آلية عند توفرها"else "الأصلي"}",Mint)
            shownSummary.forEach{part->WorldParagraph(part,ArticleBlock(text=part.original),cached(part),original,compare,font,translating,problem.isNotBlank())}
            if(summaryParts.size>2)TextButton(onClick={expanded=!expanded}){Text(if(expanded)"طي المقدمة"else"عرض المقتطف كاملًا")}
        } }
        item(key="body-label"){Caption(if(a.fullContent.isBlank())"لا متن إضافي متاح من المصدر"else if(original)"متن المصدر الأصلي"else"المتن المترجم آليًا — ترتيب المصدر محفوظ",Mint)}
        items(pieces,key={it.part.slot}){piece->if(piece.block.type=="image") {
            if(publisher.allowImages&&ImagePolicy.resolve(piece.block.url,a.url)!=null) {
                val captions=WorldTextParts.part(piece.part.slot.replace("image-","caption-"),piece.block.caption)
                val caption=if(!original&&captions.isNotEmpty()&&captions.all{cached(it)!=null})captions.joinToString(""){cached(it)!!.translated}else piece.block.caption
                NewsImage(piece.block.url,caption.ifBlank{"صورة أصلية ضمن المقال"},caption)
            }
        } else WorldParagraph(piece.part,piece.block,cached(piece.part),original,compare,font,translating,problem.isNotBlank())}
        if(!original&&plan.any{cached(it)?.engine=="MLKit"})item(key="google-attribution"){WorldGoogleAttribution()}
        item(key="assistant"){SceneCard { Text("افهم ما وراء النص",style=MaterialTheme.typography.titleLarge);Caption("التحليل والتلخيص منفصلان عن الترجمة وعن كلام المصدر، ولا يُرسلان تلقائيًا.");FilledTonalButton(onClick=assistant){Text("التلخيص والتحليل وفحص الادعاءات")};TextButton(onClick=note){Text("أضف تدوينة شخصية")}} }
        items(history.filter{a.id in it.articleIds.split(',')},key={"analysis-${it.id}"}){h->SceneCard{Caption("${h.provider} • ${h.operation}");TextButton(onClick={vm.result.value=h}){Text("فتح النتيجة المحفوظة")}}}
        val linkedNotes=notes.filter{a.id in it.articleIds.split(',')}
        if(linkedNotes.isNotEmpty())item(key="notes"){SceneCard{Text("ملاحظاتك المرتبطة",style=MaterialTheme.typography.titleMedium);linkedNotes.forEach{Text(it.title.ifBlank{it.body.take(120)})}}}
    }
}
@Composable private fun WorldParagraph(part:WorldTextParts.Part,block:ArticleBlock,translation:WorldTranslation?,original:Boolean,compare:Boolean,font:Float,loading:Boolean,failed:Boolean) {
    val context=LocalContext.current
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(original||translation!=null)SelectionContainer {
            if(original)Text(linkedArticleText(block){openOriginalArticle(context,it)},Modifier.fillMaxWidth(),fontSize=(font+if(block.type=="heading")4 else 0).sp,lineHeight=(font*1.85f).sp,
                style=LocalTextStyle.current.copy(textDirection=TextDirection.ContentOrLtr),textAlign=TextAlign.Start,fontWeight=if(block.type=="heading")FontWeight.Bold else FontWeight.Normal)
            else Text((if(block.type=="list_item")"• "else "")+translation!!.translated,Modifier.fillMaxWidth(),fontSize=(font+if(block.type=="heading")4 else 0).sp,lineHeight=(font*1.85f).sp,
                textAlign=if(block.type=="paragraph")TextAlign.Justify else TextAlign.Start,fontWeight=if(block.type=="heading")FontWeight.Bold else FontWeight.Normal,color=if(block.type=="quote")Mint else MaterialTheme.colorScheme.onSurface)
        } else {
            if(loading&&!failed)Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.small){Column(Modifier.fillMaxWidth().heightIn(min=100.dp).padding(16.dp)){Caption("تُجهّز هذه الفقرة بالعربية…");LinearProgressIndicator(Modifier.fillMaxWidth())}}
            else Column{Caption("العربية غير جاهزة لهذه الفقرة؛ النص الأصلي متاح أدناه",Accent);SelectionContainer{Text(part.original,fontSize=font.sp,lineHeight=(font*1.85f).sp,style=LocalTextStyle.current.copy(textDirection=TextDirection.ContentOrLtr))}}
        }
        if(compare&&!original&&translation!=null)Surface(color=Panel,shape=MaterialTheme.shapes.medium){Column(Modifier.padding(14.dp)){Caption("الأصل • للمقارنة");SelectionContainer{Text(part.original,fontSize=(font-1).sp,lineHeight=(font*1.7f).sp,style=LocalTextStyle.current.copy(textDirection=TextDirection.ContentOrLtr))}}}
        if(!original&&translation!=null)block.links.orEmpty().map{it.url}.distinct().forEach{LinkButton(it,"رابط ضمن الفقرة الأصلية")}
    }
}
