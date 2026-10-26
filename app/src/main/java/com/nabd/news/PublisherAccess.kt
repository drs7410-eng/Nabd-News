package com.nabd.news

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** RFC-style group selection and longest-match allow/disallow, including wildcard/$ support. */
object RobotsRules {
    private data class Group(val agents: MutableList<String> = mutableListOf(), val rules: MutableList<Pair<Boolean, String>> = mutableListOf())
    fun allows(text: String, url: HttpUrl): Boolean {
        val groups = mutableListOf<Group>(); var group = Group(); var rulesStarted = false
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            val name = line.substringBefore(':').trim().lowercase(); val value = line.substringAfter(':', "").trim()
            if (name == "user-agent") {
                if (rulesStarted) { groups += group; group = Group(); rulesStarted = false }
                group.agents += value.lowercase()
            } else if (name in setOf("allow", "disallow") && group.agents.isNotEmpty()) {
                rulesStarted = true
                if (value.isNotEmpty()) group.rules += (name == "allow") to value
            }
        }
        groups += group
        val named = groups.filter { it.agents.any { a -> a != "*" && "nabdpersonal".contains(a) } }
        val selected = if (named.isNotEmpty()) named else groups.filter { "*" in it.agents }
        val path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val matches = selected.flatMap { it.rules }.filter { (_, pattern) ->
            val end = pattern.endsWith('$')
            val p = pattern.removeSuffix("$").split('*').joinToString(".*") { Regex.escape(it) }
            Regex("^$p" + if (end) "$" else "").containsMatchIn(path)
        }
        val max = matches.maxOfOrNull { it.second.replace("*", "").removeSuffix("$").length } ?: return true
        return matches.filter { it.second.replace("*", "").removeSuffix("$").length == max }.any { it.first }
    }
}

object PublisherScope {
    fun samePublisher(feed: String, article: String): Boolean {
        fun host(value: String) = value.toHttpUrlOrNull()?.host?.removePrefix("www.")
        val a = host(feed) ?: return false; val b = host(article) ?: return false
        val bbc = listOf("bbc.com", "bbc.co.uk", "bbci.co.uk")
        fun bbcHost(host: String) = bbc.any { host == it || host.endsWith(".$it") }
        return a == b || a.endsWith(".$b") || b.endsWith(".$a") || (bbcHost(a) && bbcHost(b))
    }
}
