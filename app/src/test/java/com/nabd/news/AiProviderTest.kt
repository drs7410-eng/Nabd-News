package com.nabd.news

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class AiProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var api: JsonApi
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder().baseUrl(server.url("/v1/")).client(Network().client).build().create(JsonApi::class.java)
    }
    @After fun close() { server.shutdown() }
    @Test fun `OpenAI uses Responses store false and extracts real annotations`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"ملخص عربي","annotations":[{"type":"url_citation","url":"https://example.org/evidence"}]}]}],"usage":{"input_tokens":100,"output_tokens":30}}"""))
        val result = OpenAiProvider({ "test-placeholder" }, api).generate(AiRequest("text-model", "تعليمات", "خبر", webSearch = true))
        assertEquals("ملخص عربي", result.text)
        assertEquals(listOf("https://example.org/evidence"), result.citations)
        assertEquals(100L, result.inputTokens)
        val request = server.takeRequest()
        assertEquals("/v1/responses", request.path)
        assertEquals("Bearer test-placeholder", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertFalse(body.getBoolean("store"))
        assertEquals("web_search", body.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertFalse(request.path!!.contains("test-placeholder"))
    }
    @Test fun `Gemini key is a header and no paid model fallback occurs`() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"إجابة عربية"}]}}],"usageMetadata":{"promptTokenCount":50,"candidatesTokenCount":20}}"""))
        val result = GeminiProvider({ "test-placeholder" }, api).generate(AiRequest("chosen-model", "تعليمات", "خبر"))
        assertEquals("إجابة عربية", result.text)
        val request = server.takeRequest()
        assertEquals("test-placeholder", request.getHeader("x-goog-api-key"))
        assertEquals("/v1/models/chosen-model:generateContent", request.path)
        assertEquals(1, server.requestCount)
    }
    @Test fun `invalid key and quota errors are safe and do not retry`() = runTest {
        for (code in listOf(401, 403, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("{\"error\":\"sensitive-body-test-placeholder\"}"))
            try {
                OpenAiProvider({ "test-placeholder" }, api).generate(AiRequest("model", "", "news"))
                fail("Must fail")
            } catch (e: Exception) {
                val message = safeError(e)
                assertFalse(message.contains("sensitive-body"))
                assertFalse(message.contains("test-placeholder"))
                assertTrue(message.isNotBlank())
            }
        }
        assertEquals(4, server.requestCount)
    }
    @Test fun `models connection test uses GET without content generation`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"model-b"},{"id":"model-a"}]}"""))
        assertEquals(listOf("model-a", "model-b"), OpenAiProvider({ "test-placeholder" }, api).models())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
    }
    @Test fun `Gemini listing excludes unsupported models`() = runTest {
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/text","supportedGenerationMethods":["generateContent"]},{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}]}"""))
        assertEquals(listOf("text"), GeminiProvider({ "test-placeholder" }, api).models())
    }
    @Test fun `mock returns Arabic without cloud access`() = runTest {
        assertTrue(MockAiProvider().generate(AiRequest("mock", "", "خبر")).text.contains("أدلة غير كافية"))
        assertEquals(0, server.requestCount)
    }
}
