package com.nabd.news

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class RobotsRulesTest {
    @Test fun longestAllowOverridesParentDisallow() {
        val rules = "User-agent: *\nDisallow: /news/\nAllow: /news/public/"
        assertFalse(RobotsRules.allows(rules, "https://publisher.test/news/private/a".toHttpUrl()))
        assertTrue(RobotsRules.allows(rules, "https://publisher.test/news/public/a".toHttpUrl()))
    }
    @Test fun wildcardAndEndAnchorWork() {
        val rules = "User-agent: *\nDisallow: /*?private=$"
        assertFalse(RobotsRules.allows(rules, "https://publisher.test/a?private=".toHttpUrl()))
        assertTrue(RobotsRules.allows(rules, "https://publisher.test/a?private=no".toHttpUrl()))
    }
    @Test fun publisherPermissionDoesNotExtendToUnrelatedArticleHosts() {
        assertTrue(PublisherScope.samePublisher("https://feeds.bbci.co.uk/arabic/rss.xml", "https://www.bbc.com/arabic/articles/one"))
        assertFalse(PublisherScope.samePublisher("https://publisher.test/feed", "https://unrelated.test/news"))
        assertFalse(PublisherScope.samePublisher("https://bbc.com.evil.test/feed", "https://bbc.com/news"))
    }
    @Test fun specificAgentGroupWinsAndEmptyDisallowPermits() {
        val rules = "User-agent: *\nDisallow: /\nUser-agent: NabdPersonal\nDisallow:"
        assertTrue(RobotsRules.allows(rules, "https://publisher.test/news".toHttpUrl()))
    }
}
