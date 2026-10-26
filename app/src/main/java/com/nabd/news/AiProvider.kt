package com.nabd.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

interface JsonApi {
    @GET suspend fun get(@Url url: String, @HeaderMap headers: Map<String, String>): ResponseBody
    @POST suspend fun post(@Url url: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody): ResponseBody
}
data class AiRequest(val model: String, val instructions: String, val input: String, val history: List<Pair<String, String>> = emptyList(), val webSearch: Boolean = false)
data class AiResult(val text: String, val citations: List<String> = emptyList(), val inputTokens: Long? = null, val outputTokens: Long? = null)
interface AiProvider {
    val id: String
    suspend fun models(): List<String>
    suspend fun generate(request: AiRequest): AiResult
}
class AiFailure(val userMessage: String) : IOException(userMessage)
fun safeError(error: Throwable): String = when (error) {
    is AiFailure -> error.userMessage
    is retrofit2.HttpException -> when (error.code()) {
        400 -> "الطلب أو النموذج غير مدعوم. راجع اختيار النموذج وطول المحتوى."
        401 -> "المفتاح غير صحيح أو أُلغي. أعد إدخاله من الإعدادات."
        403 -> "لا توجد صلاحية لهذا النموذج أو الخدمة أو المنطقة."
        404 -> "النموذج غير متاح. حدّث قائمة النماذج واختر نموذجًا يدعم إنشاء النص."
        429 -> "تم تجاوز الحصة أو معدل الطلبات، أو نفد الرصيد. راجع لوحة المزود وانتظر قبل إعادة المحاولة."
        in 500..599 -> "خدمة الذكاء الاصطناعي غير متاحة مؤقتًا. تبقى الأخبار والدفتر متاحين."
        else -> "رفض المزود الطلب (HTTP ${error.code()}). راجع الإعدادات."
    }
    is IOException -> "تعذر الاتصال الآمن أو انتهت المهلة. تحقق من الشبكة وحاول مجددًا."
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "راجع الإعدادات."
    else -> "تعذر إتمام العملية. بقيت بياناتك المحفوظة محليًا."
}
class Network {
    val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    fun api(base: String): JsonApi = Retrofit.Builder().baseUrl(base).client(client).build().create(JsonApi::class.java)
}
class OpenAiProvider(private val key: () -> String, private val api: JsonApi) : AiProvider {
    override val id = "OpenAI"
    private fun headers() = mapOf("Authorization" to "Bearer ${key()}")
    override suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        val data = JSONObject(api.get("models", headers()).use { it.string() }).getJSONArray("data")
        (0 until data.length()).map { data.getJSONObject(it).getString("id") }.sorted()
    }
    override suspend fun generate(request: AiRequest): AiResult = withContext(Dispatchers.IO) {
        val inputs = JSONArray()
        request.history.forEach { (role, text) -> inputs.put(JSONObject().put("role", role).put("content", text)) }
        inputs.put(JSONObject().put("role", "user").put("content", request.input))
        val payload = JSONObject().put("model", request.model).put("instructions", request.instructions)
            .put("input", inputs).put("max_output_tokens", AiPolicy.MAX_OUTPUT).put("store", false)
        if (request.webSearch) payload.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        val json = JSONObject(api.post("responses", headers(), payload.toString().toRequestBody("application/json".toMediaType())).use { it.string() })
        if (json.optString("status") == "failed") throw AiFailure("لم يكتمل إنشاء الإجابة لدى OpenAI. راجع النموذج والحصة.")
        val output = json.optJSONArray("output") ?: JSONArray()
        val text = mutableListOf<String>(); val citations = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.getJSONObject(j)
                if (c.optString("type") == "output_text") text += c.optString("text")
                if (c.optString("type") == "refusal") text += c.optString("refusal")
                val annotations = c.optJSONArray("annotations") ?: continue
                for (k in 0 until annotations.length()) {
                    val a = annotations.getJSONObject(k)
                    if (a.optString("type") == "url_citation" && a.optString("url").startsWith("https://")) citations += a.getString("url")
                }
            }
        }
        if (text.isEmpty()) throw AiFailure("لم يُرجع النموذج نصًا. قد يكون حد الإخراج غير كافٍ أو النموذج غير مناسب.")
        val usage = json.optJSONObject("usage")
        val suffix = if (json.optString("status") == "incomplete") "\n\n[إجابة غير مكتملة بسبب حد الإخراج]" else ""
        AiResult(text.joinToString("\n") + suffix, citations.distinct(), usage?.nullableLong("input_tokens"), usage?.nullableLong("output_tokens"))
    }
}
class GeminiProvider(private val key: () -> String, private val api: JsonApi) : AiProvider {
    override val id = "Gemini"
    private fun headers() = mapOf("x-goog-api-key" to key())
    override suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>(); var page = ""
        do {
            val json = JSONObject(api.get("models?pageSize=100" + if (page.isBlank()) "" else "&pageToken=${java.net.URLEncoder.encode(page, "UTF-8")}", headers()).use { it.string() })
            val models = json.optJSONArray("models") ?: JSONArray()
            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                if (model.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true)
                    result += model.getString("name").removePrefix("models/")
            }
            page = json.optString("nextPageToken")
        } while (page.isNotEmpty())
        result.sorted()
    }
    override suspend fun generate(request: AiRequest): AiResult = withContext(Dispatchers.IO) {
        require(request.model.matches(Regex("[a-zA-Z0-9._-]+"))) { "اسم نموذج Gemini غير صالح" }
        val contents = JSONArray()
        request.history.forEach { (role, text) -> contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", text)))) }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", request.input))))
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.instructions))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", AiPolicy.MAX_OUTPUT))
        val json = JSONObject(api.post("models/${request.model}:generateContent", headers(), body.toString().toRequestBody("application/json".toMediaType())).use { it.string() })
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiFailure("لم يُرجع Gemini إجابة؛ قد تكون محجوبة أو تجاوز الطلب حدود النموذج.")
        val text = (0 until parts.length()).map { parts.getJSONObject(it).optString("text") }.filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) throw AiFailure("الإجابة فارغة. حاول بسؤال أو نموذج آخر.")
        val usage = json.optJSONObject("usageMetadata")
        AiResult(text, inputTokens = usage?.nullableLong("promptTokenCount"), outputTokens = usage?.nullableLong("candidatesTokenCount"))
    }
}
fun JSONObject.nullableLong(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

/** Test-only injection; never chooses a cloud provider or performs network requests. */
class MockAiProvider : AiProvider {
    override val id = "Mock"
    override suspend fun models() = listOf("mock-arabic")
    override suspend fun generate(request: AiRequest) = AiResult("• ملخص تجريبي من المحتوى المقدم.\n• أدلة غير كافية للتحقق المستقل.\n• لا يمثل هذا اتصالًا فعليًا.", inputTokens = 20, outputTokens = 30)
}
