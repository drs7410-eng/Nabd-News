package com.nabd.news

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

/** Only the requested real BBC URL can pass acceptance; no substitute article, fixture, or generated body. */
class BbcReferenceLiveTest {
    @Test fun exactBbcArticleMustMeetAcceptanceOrWriteSpecificFailure() {
        assumeTrue("Explicit live reference run only", System.getProperty("nabd.bbcReference") == "true")
        val builder = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        // JVM harness honors the inherited environment proxy. Android probe uses the phone's network normally.
        val configuredProxy = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
        if (!configuredProxy.isNullOrBlank()) {
            val uri = URI(configuredProxy)
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, if (uri.port > 0) uri.port else 80)))
        }
        val result = BbcArticleDiagnostic(builder.build()).run()
        val directory = File("build/reports/bbc-reference").apply { mkdirs() }
        directory.resolve("report.json").writeText(result.report.json())
        directory.resolve("report-ar.md").writeText(result.report.markdown())
        assertEquals("The real BBC article did not pass acceptance: ${result.report.reason}", BbcDiagnosticState.FULL_ARTICLE, result.report.state)
    }
}
