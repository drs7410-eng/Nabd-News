package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

/** Harness safety only. These checks do not claim extraction of any real BBC article. */
class BbcDiagnosticSafetyTest {
    @Test fun queryTrackingDoesNotChangeReferenceIdentity() {
        val tracked = BbcArticleDiagnostic.URL + "?at_medium=RSS&at_campaign=rss"
        val evidence = mapOf("requestedRssUrl" to tracked, "baseUrl" to BbcArticleDiagnostic.URL,
            "currentProductionMatcher" to ArticleIdentity.same(tracked, BbcArticleDiagnostic.URL),
            "diagnosticBbcOnlyMatcher" to BbcArticleDiagnostic.sameReference(tracked))
        java.io.File("build/reports/bbc-reference").apply { mkdirs() }.resolve("identity-comparison.json")
            .writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence))
        assertTrue(BbcArticleDiagnostic.sameReference(tracked))
        assertFalse(BbcArticleDiagnostic.sameReference("https://www.bbc.com/arabic/articles/other"))
    }
    @Test fun proxyConnect403IsNotReportedAsBbcHttp403() {
        val observation = BbcArticleDiagnostic.transportFailure(BbcArticleDiagnostic.URL, "IOException: Unexpected response code for CONNECT: 403")
        assertEquals(403, observation.proxyConnectStatus)
        assertNull(observation.httpStatus); assertNull(observation.finalUrl); assertNull(observation.contentType); assertNull(observation.receivedBytes)
    }
    @Test fun missingHtmlMetricsAreNullRatherThanFabricatedZeros() {
        val report = BbcDiagnosticReport(state = BbcDiagnosticState.EXTRACTION_FAILED)
        val json = report.json()
        assertTrue(json.contains("\"candidateParagraphs\": null")); assertTrue(json.contains("\"rssDescriptionCharacters\": null"))
        assertNull(report.verifiedBodyParagraphs); assertNull(report.javascriptRequired)
        assertFalse(report.markdown().contains("FULL_ARTICLE"))
    }
    @Test fun diagnosticDefaultsNeverClaimNativeReaderOrRoomSuccess() {
        val report = BbcDiagnosticReport()
        assertEquals("NOT_TESTED_NOT_MODIFIED", report.productionReader); assertEquals("NOT_RUN", report.nativePreview)
        assertEquals(BbcDiagnosticState.FETCHING_ARTICLE, report.state)
    }
}
