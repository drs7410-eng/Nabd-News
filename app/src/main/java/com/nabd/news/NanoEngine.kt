package com.nabd.news

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.*
import kotlinx.coroutines.guava.await

/** No cloud fallback. ML Kit beta1 advertises English/Japanese/Korean, not Arabic. */
class NanoEngine(private val context: Context) : AiProvider {
    override val id = "Gemini Nano"
    override suspend fun models(): List<String> {
        val c = client()
        return try { if (c.checkFeatureStatus().await() == FeatureStatus.AVAILABLE) listOf("local-summary") else emptyList() }
        finally { c.close() }
    }
    override suspend fun generate(request: AiRequest): AiResult {
        require(request.model == "local-summary" && request.instructions == "local-summary-only" && !request.webSearch && request.history.isEmpty()) {
            "المحرك المحلي يدعم التلخيص التجريبي فقط، وليس المحادثة أو التحقق أو البحث."
        }
        return AiResult(summarize(request.input))
    }
    private fun client() = Summarization.getClient(SummarizerOptions.builder(context)
        .setInputType(SummarizerOptions.InputType.ARTICLE)
        .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
        .setLanguage(SummarizerOptions.Language.ENGLISH).build())
    suspend fun status(): String {
        val c = client()
        return try {
            when (c.checkFeatureStatus().await()) {
                FeatureStatus.AVAILABLE -> "المحرك متاح محليًا. العربية غير مدعومة رسميًا في إصدار ML Kit المدمج."
                FeatureStatus.DOWNLOADABLE -> "الجهاز متوافق؛ النموذج يحتاج تنزيلًا. العربية غير مدعومة رسميًا."
                FeatureStatus.DOWNLOADING -> "جارٍ تنزيل النموذج المحلي."
                else -> "غير متاح على هذا الجهاز أو إصدار AICore الحالي."
            }
        } finally { c.close() }
    }
    suspend fun download(onProgress: (String) -> Unit) {
        val c = client()
        try {
            c.downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) { onProgress("بدء التنزيل: ${bytesToDownload / 1024 / 1024} MB") }
                override fun onDownloadProgress(totalBytesDownloaded: Long) { onProgress("تم تنزيل ${totalBytesDownloaded / 1024 / 1024} MB") }
                override fun onDownloadCompleted() { onProgress("اكتمل تنزيل النموذج") }
                override fun onDownloadFailed(e: com.google.mlkit.genai.common.GenAiException) { onProgress("فشل التنزيل. تحقق من دعم الجهاز والشبكة.") }
            }).await()
        } finally { c.close() }
    }
    suspend fun summarize(text: String): String {
        val c = client()
        return try {
            require(c.checkFeatureStatus().await() == FeatureStatus.AVAILABLE) { "المحرك المحلي غير متاح. افحصه من الإعدادات أولًا." }
            c.runInference(SummarizationRequest.builder(text.take(12000)).build()).await().summary
        } finally { c.close() }
    }
}
