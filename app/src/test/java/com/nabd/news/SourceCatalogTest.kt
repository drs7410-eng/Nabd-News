package com.nabd.news

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.*
import org.junit.Test

class SourceCatalogTest {
    @Test fun stableIdsAndConfiguredFeedsAreUniqueAndHttps() {
        val entries = SourceCatalog.entries
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        val feeds = entries.mapNotNull { it.feed }
        assertEquals(feeds.size, feeds.distinct().size)
        feeds.forEach { assertTrue(it.toHttpUrlOrNull()?.isHttps == true) }
        entries.forEach { assertTrue(it.reference.toHttpUrlOrNull()?.isHttps == true); assertTrue(it.category.isNotBlank()) }
    }
    @Test fun unavailableArabicFeedsRemainExplicitInsteadOfInventingUrls() {
        assertNull(SourceCatalog.entries.single { it.id == "reuters-ar" }.feed)
        assertNull(SourceCatalog.entries.single { it.id == "cnbc-ar" }.feed)
    }
}
