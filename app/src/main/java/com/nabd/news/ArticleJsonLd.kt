package com.nabd.news

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object ArticleJsonLd {
    fun articles(scripts: List<String>, page: String): List<JsonObject> {
        val identified = mutableListOf<JsonObject>()
        val unscoped = mutableListOf<JsonObject>()
        fun identity(obj: JsonObject): String? = obj.get("url")?.takeIf { it.isJsonPrimitive }?.asString
            ?: obj.get("mainEntityOfPage")?.let { if (it.isJsonPrimitive) it.asString else if (it.isJsonObject) it.asJsonObject.get("@id")?.asString else null }
            ?: obj.get("@id")?.takeIf { it.isJsonPrimitive }?.asString
        fun walk(node: JsonElement, depth: Int, rootScope: Boolean) {
            if (depth > 20) return
            if (node.isJsonArray) node.asJsonArray.forEach { walk(it, depth + 1, rootScope) }
            else if (node.isJsonObject) {
                val obj = node.asJsonObject
                val type = obj.get("@type")?.toString().orEmpty()
                val id = identity(obj)
                if (listOf("Article", "NewsArticle", "BlogPosting", "ReportageNewsArticle").any { type.contains("\"$it\"") }) {
                    if (id != null && ArticleIdentity.same(id, page)) identified += obj
                    else if (id == null && rootScope) unscoped += obj
                }
                obj.entrySet().filter { it.value.isJsonArray || it.value.isJsonObject }.forEach { child ->
                    val mainEntity = child.key == "mainEntity" && type.contains("WebPage") && id != null && ArticleIdentity.same(id, page)
                    walk(child.value, depth + 1, (rootScope && child.key == "@graph") || mainEntity)
                }
            }
        }
        scripts.filter { it.length <= 300_000 }.forEach { script -> runCatching { walk(JsonParser.parseString(script), 0, true) } }
        // Never borrow an unidentified nested/related Article's image or articleBody.
        return identified.ifEmpty { if (unscoped.size == 1) unscoped else emptyList() }
    }

    fun images(article: JsonObject): List<String> {
        val images = mutableListOf<String>()
        fun parse(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            when {
                node.isJsonPrimitive && node.asJsonPrimitive.isString -> images += node.asString
                node.isJsonArray -> node.asJsonArray.forEach(::parse)
                node.isJsonObject -> parse(node.asJsonObject.get("url") ?: node.asJsonObject.get("contentUrl"))
            }
        }
        parse(article.get("image"))
        return images
    }
}
