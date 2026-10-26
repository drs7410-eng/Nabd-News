package com.nabd.news

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BbcDiagnosticViewModel : ViewModel() {
    val result = MutableStateFlow<BbcDiagnosticResult?>(null)
    val running = MutableStateFlow(false)
    val stage = MutableStateFlow("لم يبدأ الاختبار")
    val preview = MutableStateFlow(false)
    fun run(description: String) {
        if (running.value) return
        running.value = true; result.value = null; preview.value = false; stage.value = BbcDiagnosticState.FETCHING_ARTICLE.name
        viewModelScope.launch {
            try {
                val tested = withContext(Dispatchers.IO) {
                    // A fresh client: no AI API headers, app cookies, credentials, or vault access.
                    val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
                    BbcArticleDiagnostic(client).run(description) { stage.value = it }
                }
                result.value = tested; stage.value = tested.report.state.name
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                result.value = BbcDiagnosticResult(BbcDiagnosticReport(state = BbcDiagnosticState.EXTRACTION_FAILED,
                    reason = "خطأ الاختبار: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(300)}"))
                stage.value = BbcDiagnosticState.EXTRACTION_FAILED.name
            } finally { running.value = false }
        }
    }
    fun openPreview() {
        val current = result.value ?: return
        if (current.previewBlocks.isEmpty()) return
        preview.value = true
        result.value = current.copy(report = current.report.copy(nativePreview = "USER_OPENED_COMPOSE_CANDIDATE_PREVIEW_NOT_VISUALLY_VERIFIED"))
    }
}

/** Explicitly launched debug-only activity. No browser, WebView, Room writes, or changes to the production reader. */
class BbcDiagnosticActivity : ComponentActivity() {
    private val vm: BbcDiagnosticViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { NabdTheme { DiagnosticScreen() } } }
    }
    @Composable private fun DiagnosticScreen() {
        val result by vm.result.collectAsStateWithLifecycle()
        val running by vm.running.collectAsStateWithLifecycle()
        val stage by vm.stage.collectAsStateWithLifecycle()
        val preview by vm.preview.collectAsStateWithLifecycle()
        var rssDescription by remember { mutableStateOf("") }
        var exportStatus by remember { mutableStateOf("") }
        var exportZip by remember { mutableStateOf(false) }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val snapshot = result
            if (uri != null && snapshot != null) lifecycleScope.launch {
                exportStatus = "جارٍ حفظ التقرير محليًا…"
                exportStatus = try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { stream ->
                            if (exportZip) ZipOutputStream(stream).use { zip ->
                                fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                                entry("report.json", snapshot.report.json().toByteArray())
                                entry("report-ar.md", snapshot.report.markdown().toByteArray())
                                snapshot.htmlSnapshot?.let { entry("bbc-article-response.html", it) }
                            } else stream.write(snapshot.report.json().toByteArray())
                        } ?: error("تعذر إنشاء الملف")
                    }
                    "حُفظ التقرير؛ لا يحتوي على مفاتيح API أو دفتر نبض."
                } catch (_: Exception) { "تعذر حفظ التقرير؛ أعد اختيار الملف." }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Ink) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { SectionTitle("اختبار مقال BBC", "تشخيص للرابط المحدد فقط، قبل اعتماد إصلاح قارئ نبض. لا تُعد هذه الحزمة دليل نجاح للاستخراج قبل تشغيلها من شبكتك.") }
                item { SelectionContainer { Caption(BbcArticleDiagnostic.URL, Mint) } }
                item { BoxCard {
                    Text("وصف RSS المحفوظ (اختياري)")
                    Caption("يبحث الاختبار عن الرابط نفسه في RSS الحالي. إذا خرج المقال من الخلاصة، يمكنك لصق وصفه الأصلي المحفوظ هنا. لا يُستخدم وصف HTML بدل وصف RSS، ويُميّز النص الملصق عن المطابقة الحية.")
                    OutlinedTextField(rssDescription, { rssDescription = it.take(50000) }, label = { Text("وصف RSS الأصلي فقط") }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !running)
                    Button(onClick = { vm.run(rssDescription) }, enabled = !running) { Text("بدء اختبار الرابط عبر اتصال الهاتف") }
                    Text(stage, color = Accent)
                    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                } }
                result?.let { tested ->
                    item { BoxCard {
                        Text("الحالة: ${tested.report.state}", color = Accent)
                        Text(tested.report.reason, lineHeight = 27.sp)
                        Caption("لم يُفتح متصفح أو WebView، ولم يُكتب شيء في قاعدة أخبار نبض أو تُغيّر مصادر أخرى.")
                        Button(onClick = { exportZip = false; export.launch("bbc-c6ly0gdxd5jlo-report.json") }) { Text("حفظ التقرير JSON") }
                        Button(onClick = { exportZip = true; export.launch("bbc-c6ly0gdxd5jlo-diagnostic.zip") }) { Text("حفظ حزمة التقرير وHTML المتاح") }
                        Caption("حزمة ZIP قد تتضمن HTML المقال العام الذي جُلب للاختبار، إن سمحت فحوص الوصول بتحليله. المشاركة تتم باختيارك بعد الحفظ.")
                        if (tested.previewBlocks.isNotEmpty()) OutlinedButton(onClick = vm::openPreview) { Text("معاينة النص المرشح داخل Compose") }
                        if (exportStatus.isNotBlank()) Caption(exportStatus)
                    } }
                    item { SelectionContainer { Text(tested.report.markdown(), lineHeight = 26.sp) } }
                    if (preview) {
                        item { SectionTitle("معاينة مرشح المتن", "هذه معاينة تشخيصية داخل تنسيق نبض، وليست إثباتًا بأن شاشة الخبر الإنتاجية نجحت أو أن النص كامل. راجع حالة التقرير.") }
                        itemsIndexed(tested.previewBlocks) { _, block ->
                            if (block.type == "image") NewsImage(block.url, block.caption.ifBlank { "صورة داخل مرشح المتن" }, block.caption)
                            else SelectionContainer { Text((if (block.type == "list_item") "• " else "") + block.text,
                                fontSize = (if (block.type == "heading") 22 else 18).sp, lineHeight = 32.sp) }
                        }
                    }
                }
            }
        }
    }
}
