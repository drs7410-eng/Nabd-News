package com.nabd.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in real-network test of this exact article. A failure writes evidence before failing acceptance. */
@RunWith(AndroidJUnit4::class)
class BbcReferenceDeviceTest {
    @Test fun requestedArticleOnDeviceMustMeetAcceptance() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable -e bbcReference true to use the phone network", arguments.getString("bbcReference") == "true")
        val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
        val result = BbcArticleDiagnostic(client).run(arguments.getString("rssDescription").orEmpty())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "bbc-reference").apply { mkdirs() }
        folder.resolve("bbc-article-response.html").delete() // Never export a stale HTML snapshot from a previous run.
        folder.resolve("report.json").writeText(result.report.json())
        folder.resolve("report-ar.md").writeText(result.report.markdown())
        result.htmlSnapshot?.let { folder.resolve("bbc-article-response.html").writeBytes(it) }
        assertEquals("Real article acceptance not established: ${result.report.reason}", BbcDiagnosticState.FULL_ARTICLE, result.report.state)
    }
}
