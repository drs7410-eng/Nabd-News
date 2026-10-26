package com.nabd.news

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser

class BackupRepository(private val db: NabdDatabase) {
    private val gson = Gson()
    private val dao = db.dao()
    data class Archive(val format: String = "nabd-local", val version: Int = 7, val articles: List<Article>, val notes: List<Note>,
        val analyses: List<Analysis>, val conversations: List<Conversation>, val messages: List<Message>, val sources: List<Source>, val mediaClips: List<MediaClip> = emptyList(),
        val sceneTopics: List<SceneTopic> = emptyList(),
        val trendSignals: List<TrendSignal> = emptyList(),
        val topicArticles: List<TopicArticle> = emptyList(),
        val socialAccounts: List<FollowedSocialAccount> = emptyList(),
        val socialPosts: List<SavedSocialPost> = emptyList(),
        val topicAnalyses: List<TopicAnalysisLink> = emptyList(),
        val claims: List<VerificationClaim> = emptyList(),
        val claimEvidence: List<ClaimEvidence> = emptyList(),
        val topicNotes: List<TopicNote> = emptyList(),
        val topicTimeline: List<TopicTimelineEvent> = emptyList(),
        val worldPublishers: List<WorldPublisher> = emptyList(),
        val worldFeeds: List<WorldFeed> = emptyList(),
        val worldArticles: List<WorldArticle> = emptyList(),
        val worldTranslations: List<WorldTranslation> = emptyList(),
        val worldGlossary: List<WorldGlossary> = emptyList())
    suspend fun exportJson(): String = db.withTransaction { gson.toJson(Archive(articles = dao.allArticles(), notes = dao.allNotes(), analyses = dao.allAnalyses(),
        conversations = dao.allConversations(), messages = dao.allMessages(), sources = dao.allSources(), mediaClips = dao.allClips(),
        sceneTopics = dao.allSceneTopics(), trendSignals = dao.allTrendSignals(), topicArticles = dao.allTopicArticles(), socialAccounts = dao.allSocialAccounts(), socialPosts = dao.allSocialPosts(), topicAnalyses = dao.allTopicAnalyses(), claims = dao.allClaims(), claimEvidence = dao.allClaimEvidence(), topicNotes = dao.allTopicNotes(), topicTimeline = dao.allTopicTimeline(), worldPublishers=dao.allWorldPublishers(), worldFeeds=dao.allWorldFeeds(), worldArticles=dao.allWorldArticles(), worldTranslations=dao.allWorldTranslations(), worldGlossary=dao.allWorldGlossary())) }
    suspend fun markdown(): String = buildString {
        append("# دفتر نبض\n\n")
        dao.allNotes().sortedByDescending { it.updated }.forEach {
            append("## ${it.title}\n\n${it.body}\n\nالوسوم: ${it.tags}\n\nالمصادر: ${it.links}\n\nآخر تعديل: ${java.time.Instant.ofEpochMilli(it.updated)}\n\n---\n\n")
        }
    }
    suspend fun restore(json: String) {
        require(json.length <= 20_000_000) { "ملف النسخة الاحتياطية أكبر من الحد المسموح (20 MB)" }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("format")?.asString == "nabd-local" && root.get("version")?.asInt in listOf(1, 2, 3, 4, 5, 6, 7)) { "صيغة النسخة غير مدعومة" }
        listOf("sceneTopics","trendSignals","topicArticles","socialAccounts","socialPosts","topicAnalyses","claims","claimEvidence","topicNotes","topicTimeline").forEach { if(!root.has(it)) root.add(it, com.google.gson.JsonArray()) }
        listOf("worldPublishers","worldFeeds","worldArticles","worldTranslations","worldGlossary").forEach{if(!root.has(it))root.add(it,com.google.gson.JsonArray())}
        if(!root.has("mediaClips")) root.add("mediaClips", com.google.gson.JsonArray())
        root.getAsJsonArray("mediaClips")?.forEach { item ->
            val obj = item.asJsonObject
            if(!obj.has("favorite")) obj.addProperty("favorite", false)
            listOf("visibleMillis", "impressions", "skips").forEach { if(!obj.has(it)) obj.addProperty(it, 0) }
        }
        // Gson does not invoke Kotlin defaults for missing fields. Upgrade v1 backups explicitly.
        root.getAsJsonArray("articles")?.forEach { item ->
            val a = item.asJsonObject
            mapOf("summary" to (a.get("body")?.asString ?: ""), "fullContent" to "", "contentBlocks" to "[]", "images" to "[]",
                "detailReason" to "", "extractorId" to "", "sourceItemId" to "", "sourceId" to "", "contentOrigin" to "excerpt", "detailState" to "not_requested").forEach { (key, value) -> if (!a.has(key)) a.addProperty(key, value) }
            if (!a.has("detailFetchedAt")) a.addProperty("detailFetchedAt", 0L)
        }
        root.getAsJsonArray("sources")?.forEach { item ->
            val a = item.asJsonObject
            if (!a.has("htmlAllowed")) a.addProperty("htmlAllowed", false)
            if (!a.has("termsUrl")) a.addProperty("termsUrl", "")
        }
        // Validate required non-null fields before Gson instantiates Kotlin entities.
        val fields = mapOf("articles" to listOf("id","title","body","url","source","category","published","saved","kind","image","summary","fullContent","contentBlocks","images","sourceItemId","sourceId","contentOrigin","detailState","detailFetchedAt","detailReason","extractorId"),
            "notes" to listOf("id","title","body","tags","category","articleIds","analysisId","links","created","updated","pinned"),
            "analyses" to listOf("id","articleIds","operation","provider","model","result","sources","status","cacheKey","created"),
            "conversations" to listOf("id","articleId","title","updated"),
            "messages" to listOf("id","conversationId","role","text","provider","model","created"),
            "sources" to listOf("id","name","url","category","type","htmlAllowed","termsUrl"),
            "mediaClips" to listOf("id","title","url","playUrl","provider","sourceId","source","topic","published","thumbnail","liked","hidden","opened","favorite","visibleMillis","impressions","skips"))
        fields.forEach { (table, columns) ->
            val list = root.getAsJsonArray(table) ?: throw IllegalArgumentException("نسخة ناقصة: $table")
            require(list.size() <= 30000) { "عدد سجلات النسخة كبير جدًا" }
            val ids = mutableSetOf<String>()
            list.forEach { element ->
                val item = element.asJsonObject
                require(columns.all { item.has(it) && !item.get(it).isJsonNull && item.get(it).isJsonPrimitive }) { "سجل غير صالح في النسخة" }
                require(ids.add(item.get("id").asString) && item.get("id").asString.isNotBlank()) { "معرّف مكرر في النسخة" }
            }
        }
        val sceneFields = mapOf(
            "worldPublishers" to listOf("id","name","arabicName","website","country","language","sections","termsUrl","rightsNote","permission","allowCache","allowLocal","allowCloud","allowHtml","allowImages","enabled"),
            "worldFeeds" to listOf("id","publisherId","url","category","language","status","attempted","succeeded","httpCode","error","etag","modified","articleCount","fullCount","imageCount","latestPublished"),
            "worldArticles" to listOf("articleId","publisherId","feedId","language","languageBasis","author","attribution","restriction"),
            "worldTranslations" to listOf("id","articleId","slot","language","target","textHash","glossaryHash","engine","engineVersion","original","translated","state","error","created","analysisId"),
            "worldGlossary" to listOf("id","original","arabic","domain","language","reference","notes","active","updated"),
            "sceneTopics" to listOf("id","title","description","region","origin","created","updated","saved"),
            "trendSignals" to listOf("id","topicId","source","region","observedAt","published","traffic","sourceUrl","newsLinks","newsCount","sourceCount","recentCount","previousCount"),
            "topicArticles" to listOf("topicId","articleId","mode","lastTitle","lastPublished"),
            "socialAccounts" to listOf("id","handle","name","category","groupName","favorite"),
            "socialPosts" to listOf("id","url","handle","title","text","personalNote","topicId","created"),
            "topicAnalyses" to listOf("topicId","analysisId","created"),
            "claims" to listOf("id","topicId","text","verifiable","status","analysisId","created"),
            "claimEvidence" to listOf("id","claimId","url","stance","published","description"),
            "topicNotes" to listOf("topicId","noteId"),
            "topicTimeline" to listOf("id","topicId","title","at","kind","reference"),
        )
        sceneFields.forEach { (table, columns) ->
            val rows=root.getAsJsonArray(table)
            require(rows.size()<=30000) { "عدد سجلات الرصد كبير جدًا" }
            val keys=mutableSetOf<String>()
            rows.forEach { element ->
                val item=element.asJsonObject
                require(columns.all{item.has(it) && !item[it].isJsonNull && item[it].isJsonPrimitive}) { "سجل رصد غير صالح" }
                val keyFields=if(table=="worldArticles")listOf("articleId")else if("id" in columns) listOf("id") else columns.take(2)
                require(keys.add(keyFields.joinToString("|"){item[it].asString}) && keyFields.all{item[it].asString.isNotBlank()}) { "معرّف رصد مكرر أو فارغ" }
            }
        }
        val archive = gson.fromJson(root, Archive::class.java)
        require(archive.articles.all { it.url.startsWith("https://") || it.url.startsWith("http://") }) { "رابط خبر غير صالح" }
        require(archive.sources.all { it.url.startsWith("https://") }) { "المصادر تتطلب HTTPS" }
        require(archive.messages.all { it.role == "user" || it.role == "assistant" }) { "دور رسالة غير صالح" }
        require(archive.mediaClips.all { MediaPolicy.validPlayer(MediaTarget(it.provider,it.playUrl)) && ReaderNavigation.allowed(it.url) }) { "رابط مشغل غير صالح في النسخة" }
        require(archive.socialPosts.all { SharedPostParser.post(it.url)!=null }) { "رابط منشور غير صالح" }
        require(archive.socialAccounts.all { SharedPostParser.account(it.handle)!=null }) { "حساب X غير صالح" }
        require(archive.claimEvidence.all { ReaderNavigation.allowed(it.url) }) { "رابط دليل غير صالح" }
        require(archive.topicArticles.all { it.mode in -1..1 }) { "حالة ربط غير صالحة" }
        require(archive.worldPublishers.all{WorldUrls.valid(it.website) && (!it.allowCache || WorldUrls.valid(it.termsUrl))}) { "روابط أذونات أو مواقع عالمية غير صالحة" }
        require(archive.worldFeeds.all{WorldUrls.valid(it.url)}) { "رابط RSS عالمي غير صالح" }
        require(archive.worldTranslations.all{it.textHash==AiPolicy.hash(it.original) && it.engine in setOf("MLKit","OpenAI","Gemini") && it.state in setOf("ready","failed","cancelled","translating")}) { "بصمة ترجمة أو محرك غير صالح" }
        archive.worldTranslations.filter{it.state=="ready"}.forEach{EditorialProtection.validate(it.original,it.translated)}
        db.withTransaction {
            val existing = dao.allArticles().associateBy { it.url }
            val idMap = archive.articles.associate { it.id to (existing[it.url]?.id ?: it.id) }
            fun remap(ids: String) = ids.split(',').joinToString(",") { idMap[it] ?: it }
            dao.addArticles(archive.articles)
            archive.notes.forEach { dao.saveNote(it.copy(articleIds = remap(it.articleIds))) }
            archive.analyses.forEach { dao.saveAnalysis(it.copy(articleIds = remap(it.articleIds))) }
            archive.conversations.forEach { dao.saveConversation(it.copy(articleId = idMap[it.articleId] ?: it.articleId)) }
            archive.messages.forEach { dao.saveMessage(it) }
            archive.sources.forEach { dao.saveSource(it) }
            archive.mediaClips.forEach { dao.saveClip(it) }
            archive.sceneTopics.forEach { dao.saveSceneTopic(it) }
            dao.addTrendSignals(archive.trendSignals)
            archive.topicArticles.forEach { dao.saveTopicArticle(it.copy(articleId = idMap[it.articleId] ?: it.articleId)) }
            archive.socialAccounts.forEach { dao.saveSocialAccount(it) }
            archive.socialPosts.forEach { dao.saveSocialPost(it) }
            archive.topicAnalyses.forEach { dao.saveTopicAnalysis(it) }
            archive.claims.forEach { dao.saveClaim(it) }
            archive.claimEvidence.forEach { dao.saveClaimEvidence(it) }
            archive.topicNotes.forEach { dao.saveTopicNote(it) }
            archive.topicTimeline.forEach { dao.saveTopicTimeline(it) }
            archive.worldPublishers.forEach{dao.saveWorldPublisher(it)}
            archive.worldFeeds.forEach{dao.saveWorldFeed(it)}
            archive.worldArticles.forEach{dao.saveWorldArticle(it.copy(articleId=idMap[it.articleId]?:it.articleId))}
            archive.worldTranslations.forEach{dao.saveWorldTranslation(it.copy(articleId=idMap[it.articleId]?:it.articleId,id=AiPolicy.hash("translation-v1|${idMap[it.articleId]?:it.articleId}|${it.slot}|${it.language}|${it.target}|${it.textHash}|${it.glossaryHash}|${it.engine}|${it.engineVersion}")))}
            archive.worldGlossary.forEach{dao.saveWorldGlossary(it)}
        }
    }
}

