package com.nabd.news

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test

/** Explicit opt-in; real device networking, no substitute fixtures or automatic paid APIs. */
class SceneTrendsDeviceTest {
    @Test fun realSaudiGoogleTrendsRss()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sceneLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=NabdDatabase.open(context)
        try {
            val state=GoogleTrendsDataSource(db.dao(),Network().client).refresh("SA",true)
            val signals=db.dao().allTrendSignals().filter{it.source=="google_trends" && it.region=="SA"}
            val report=com.google.gson.Gson().toJson(mapOf("url" to GoogleTrendsDataSource.url("SA"),"httpCode" to state.httpCode,"error" to state.error,"lastSuccess" to state.succeeded,"savedSignalCount" to signals.size,"attempted" to state.attempted))
            java.io.File(context.getExternalFilesDir(null),"scene-trends-live-report.json").writeText(report)
            assertTrue("Live request failed; inspect scene-trends-live-report.json: ${state.error}",state.error.isBlank())
            assertTrue(state.httpCode in listOf(200,304))
            // A valid empty feed is valid empty data; do not fabricate entries to satisfy a count.
        } finally {db.close()}
    }
}
