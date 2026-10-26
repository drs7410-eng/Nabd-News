package com.nabd.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: NabdDatabase
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("persistence-test.db")
        db = Room.databaseBuilder(context, NabdDatabase::class.java, "persistence-test.db").build()
    }
    @After fun close() {
        db.close(); context.deleteDatabase("persistence-test.db")
        context.getSharedPreferences("nabd_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("nabd_vault", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun emptyOperationOverrideUsesDefaultModel() {
        val s = SecureSettings(context)
        s.set("model_OpenAI", "chosen-model")
        s.set("model_OpenAI_تلخيص قصير", "")
        assertEquals("chosen-model", s.model("OpenAI", "تلخيص قصير"))
    }
    @Test fun credentialSurvivesReopenAndIsEncrypted() {
        val settings = SecureSettings(context)
        val placeholder = "not-a-real-credential-test-only"
        settings.saveKey("OpenAI", placeholder)
        assertEquals(placeholder, SecureSettings(context).getKey("OpenAI"))
        val prefs = context.getSharedPreferences("nabd_vault", Context.MODE_PRIVATE)
        assertFalse(prefs.getString("OpenAI", "")!!.contains(placeholder))
        settings.deleteKey("OpenAI")
        assertNull(SecureSettings(context).getKey("OpenAI"))
    }
    @Test fun newsNoteAndAnalysisSurviveDatabaseReopenOffline() = runTest {
        val article = Article(title = "خبر محفوظ", body = "نص الخبر", url = "https://example.org/a", source = "اختبار")
        db.dao().addArticles(listOf(article, article))
        val note = Note(title = "تدوينة", body = "مسودة", articleIds = article.id)
        db.dao().saveNote(note)
        db.dao().saveNote(note.copy(body = "محتوى معدل"))
        val analysis = Analysis(articleIds = article.id, operation = "تلخيص", provider = "Mock", model = "mock", result = "نتيجة محفوظة", sources = article.url, status = "أدلة غير كافية", cacheKey = "cache-test")
        db.dao().saveAnalysis(analysis)
        db.close()
        db = Room.databaseBuilder(context, NabdDatabase::class.java, "persistence-test.db").build()
        assertEquals(1, db.dao().allArticles().size)
        assertEquals("محتوى معدل", db.dao().allNotes().single().body)
        assertEquals(article.id, db.dao().allNotes().single().articleIds)
        assertEquals("نتيجة محفوظة", db.dao().cached("cache-test")!!.result)
    }
    @Test fun backupExcludesCredentialsAndRestoresLinkedContent() = runTest {
        val settings = SecureSettings(context)
        settings.saveKey("OpenAI", "never-export-this-placeholder")
        val article = Article(title = "خبر", body = "نص", url = "https://example.org/backup", source = "مصدر")
        db.dao().addArticles(listOf(article))
        val note = Note(title = "ملاحظة", body = "شخصية", articleIds = article.id)
        db.dao().saveNote(note)
        val backup = BackupRepository(db)
        val json = backup.exportJson()
        assertFalse(json.contains("never-export-this-placeholder"))
        assertFalse(json.contains("nabd_vault"))
        db.dao().deleteNote(note.id)
        backup.restore(json)
        assertEquals(article.id, db.dao().allNotes().single().articleIds)
        settings.deleteKey("OpenAI")
    }
    @Test fun cachedAnalysisDoesNotCallProviderTwice() = runTest {
        val settings = SecureSettings(context)
        settings.saveKey("OpenAI", "test-placeholder-no-real-api")
        settings.set("model_OpenAI", "mock-arabic")
        settings.set("enabled_OpenAI", "true")
        settings.set("daily", "0"); settings.set("monthly", "0")
        var calls = 0
        val mock = object : AiProvider {
            override val id = "OpenAI"
            override suspend fun models() = listOf("mock-arabic")
            override suspend fun generate(request: AiRequest): AiResult {
                assertFalse(request.input.contains("private-note-marker"))
                calls++; return AiResult("نتيجة تجريبية", inputTokens = 1, outputTokens = 1) }
        }
        val repo = AiRepository(db.dao(), settings, mapOf("OpenAI" to mock))
        // A complete manual test source is required; this must never relax the production excerpt guard.
        val article = Article(title = "خبر", body = "نص متاح", fullContent="نص كامل مخصص لاختبار ذاكرة التحليل فقط",contentOrigin="manual",detailState="ready",url = "https://example.org/cache", source = "مصدر")
        db.dao().addArticles(listOf(article))
        db.dao().saveNote(Note(title = "private", body = "private-note-marker", articleIds = article.id))
        repo.analyze(listOf(article), "تلخيص قصير", "OpenAI")
        repo.analyze(listOf(article), "تلخيص قصير", "OpenAI")
        assertEquals(1, calls)
        assertEquals(1, db.dao().allAnalyses().size)
    }
    @Test fun refreshingFeedPreservesBookmarkIdentityAndMissingOriginalDescription() = runTest {
        val original = Article(id = "stable-saved", title = "عنوان قديم", body = "وصف أصلي", summary = "وصف أصلي", url = "https://publisher.test/a?utm_source=old",
            source = "مصدر", sourceId = "source", sourceItemId = "guid", saved = true)
        db.dao().addArticles(listOf(original))
        db.dao().mergeArticles(listOf(Article(title = "عنوان محدّث", body = "", url = "https://publisher.test/a", source = "مصدر", sourceId = "source")))
        val refreshed = db.dao().allArticles().single()
        assertEquals("stable-saved", refreshed.id); assertTrue(refreshed.saved)
        assertEquals("وصف أصلي", refreshed.summary); assertEquals("guid", refreshed.sourceItemId)
        assertEquals("عنوان محدّث", refreshed.title)
    }
    @Test fun parsingAtomAndRssKeepsOriginalSource() {
        val source = Source(name = "مصدر", url = "https://example.org/feed")
        val repo = NewsRepository(db.dao(), Network().client)
        val rss = """<rss><channel><item><title>عنوان عربي</title><link>https://example.org/one</link><description><![CDATA[<p>نص الخبر</p>]]></description></item></channel></rss>"""
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>عنوان عربي</title><link href="https://example.org/one"/><summary>نص الخبر</summary></entry></feed>"""
        val a = repo.parse(rss.toByteArray(), source).single()
        val b = repo.parse(atom.toByteArray(), source).single()
        assertEquals(a.id, b.id)
        assertEquals("نص الخبر", a.body)
        assertEquals("مصدر", a.source)
    }
}
