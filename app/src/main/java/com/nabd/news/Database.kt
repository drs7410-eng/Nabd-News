package com.nabd.news

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
@Entity(tableName = "articles", indices = [Index(value = ["url"], unique = true)])
data class Article(@PrimaryKey val id: String = newId(), val title: String, val body: String, val url: String,
    val source: String, val category: String = "عام", val published: Long = System.currentTimeMillis(),
    val saved: Boolean = false, val kind: String = "خبر", val image: String = "",
    @ColumnInfo(defaultValue = "''") val summary: String = "",
    @ColumnInfo(defaultValue = "''") val fullContent: String = "",
    @ColumnInfo(defaultValue = "'[]'") val contentBlocks: String = "[]",
    @ColumnInfo(defaultValue = "'[]'") val images: String = "[]",
    @ColumnInfo(defaultValue = "''") val sourceItemId: String = "",
    @ColumnInfo(defaultValue = "''") val sourceId: String = "",
    @ColumnInfo(defaultValue = "'excerpt'") val contentOrigin: String = "excerpt",
    @ColumnInfo(defaultValue = "'not_requested'") val detailState: String = "not_requested",
    @ColumnInfo(defaultValue = "0") val detailFetchedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val detailReason: String = "",
    @ColumnInfo(defaultValue = "''") val extractorId: String = ""
) {
    @get:Ignore val availableText: String get() = fullContent.ifBlank { summary.ifBlank { body } }
    @get:Ignore val isExcerpt: Boolean get() = fullContent.isBlank() || contentOrigin == "excerpt" || detailState == "partial" || (contentOrigin == "rss_content" && ContentCompleteness.isShort(fullContent))
}
@Entity(tableName = "notes")
data class Note(@PrimaryKey val id: String = newId(), val title: String = "", val body: String = "", val tags: String = "",
    val category: String = "عام", val articleIds: String = "", val analysisId: String = "", val links: String = "",
    val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis(), val pinned: Boolean = false)
@Entity(tableName = "analyses", indices = [Index("cacheKey")])
data class Analysis(@PrimaryKey val id: String = newId(), val articleIds: String, val operation: String, val provider: String,
    val model: String, val result: String, val sources: String, val status: String, val cacheKey: String,
    val created: Long = System.currentTimeMillis(), val inputTokens: Long? = null, val outputTokens: Long? = null, val cost: Double? = null)
@Entity(tableName = "conversations")
data class Conversation(@PrimaryKey val id: String = newId(), val articleId: String, val title: String,
    val updated: Long = System.currentTimeMillis())
@Entity(tableName = "messages", indices = [Index("conversationId")])
data class Message(@PrimaryKey val id: String = newId(), val conversationId: String, val role: String, val text: String,
    val provider: String = "", val model: String = "", val created: Long = System.currentTimeMillis())
@Entity(tableName = "sources", indices = [Index(value = ["url"], unique = true)])
data class Source(@PrimaryKey val id: String = newId(), val name: String, val url: String, val category: String = "عام", val type: String = "RSS",
    @ColumnInfo(defaultValue = "0") val htmlAllowed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val termsUrl: String = "")
@Entity(tableName = "attachments", indices = [Index("noteId")])
data class Attachment(@PrimaryKey val id: String = newId(), val noteId: String, val uri: String, val mime: String)
@Entity(tableName = "usage")
data class Usage(@PrimaryKey val id: String = newId(), val provider: String, val model: String, val created: Long = System.currentTimeMillis(),
    val inputTokens: Long? = null, val outputTokens: Long? = null, val cost: Double? = null, val state: String = "completed")

@Dao
interface NabdDao {
    @Query("SELECT * FROM world_publishers ORDER BY name") fun worldPublishers():Flow<List<WorldPublisher>>
    @Query("SELECT * FROM world_publishers") suspend fun allWorldPublishers():List<WorldPublisher>
    @Query("SELECT * FROM world_publishers WHERE id=:id") suspend fun worldPublisher(id:String):WorldPublisher?
    @Upsert suspend fun saveWorldPublisher(value:WorldPublisher)
    @Query("SELECT * FROM world_feeds ORDER BY attempted DESC") fun worldFeeds():Flow<List<WorldFeed>>
    @Query("SELECT * FROM world_feeds") suspend fun allWorldFeeds():List<WorldFeed>
    @Upsert suspend fun saveWorldFeed(value:WorldFeed)
    @Query("SELECT * FROM world_articles") fun worldArticles():Flow<List<WorldArticle>>
    @Query("SELECT * FROM world_articles") suspend fun allWorldArticles():List<WorldArticle>
    @Query("SELECT * FROM world_articles WHERE articleId=:id") suspend fun worldArticle(id:String):WorldArticle?
    @Upsert suspend fun saveWorldArticle(value:WorldArticle)
    @Query("SELECT DISTINCT articleId FROM world_translations WHERE state='ready'") fun worldTranslatedIds():Flow<List<String>>
    @Query("SELECT * FROM world_translations WHERE articleId=:article AND slot=:slot AND textHash=:hash AND language=:lang AND glossaryHash=:glossary AND engine=:engine AND state='ready' ORDER BY created DESC LIMIT 1") suspend fun matchingWorldTranslation(article:String,slot:String,hash:String,lang:String,glossary:String,engine:String):WorldTranslation?
    @Query("SELECT * FROM world_translations WHERE articleId=:article ORDER BY created DESC") fun worldTranslations(article:String):Flow<List<WorldTranslation>>
    @Query("SELECT * FROM world_translations WHERE slot IN ('title','summary') AND state='ready' ORDER BY created DESC") fun worldHeadlines():Flow<List<WorldTranslation>>
    @Query("SELECT * FROM world_translations") suspend fun allWorldTranslations():List<WorldTranslation>
    @Query("SELECT * FROM world_translations WHERE id=:id") suspend fun worldTranslation(id:String):WorldTranslation?
    @Upsert suspend fun saveWorldTranslation(value:WorldTranslation)
    @Query("SELECT * FROM world_glossary ORDER BY original") fun worldGlossary():Flow<List<WorldGlossary>>
    @Query("SELECT * FROM world_glossary") suspend fun allWorldGlossary():List<WorldGlossary>
    @Upsert suspend fun saveWorldGlossary(value:WorldGlossary)
    @Query("DELETE FROM world_glossary WHERE id=:id") suspend fun deleteWorldGlossary(id:String)
    @Query("UPDATE sources SET htmlAllowed=:allowed,termsUrl=:terms WHERE id=:id AND type='WORLD'") suspend fun worldSourceAccess(id:String,allowed:Boolean,terms:String)
    @Query("SELECT * FROM feed_quality") fun feedQuality(): Flow<List<FeedQuality>>
    @Upsert suspend fun saveFeedQuality(quality: FeedQuality)

    @Query("SELECT * FROM articles ORDER BY published DESC") fun articles(): Flow<List<Article>>
    @Query("SELECT * FROM articles ORDER BY published DESC") suspend fun allArticles(): List<Article>
    @Query("SELECT * FROM articles WHERE id=:id") suspend fun article(id: String): Article?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addArticles(items: List<Article>)
    @Update suspend fun updateArticle(article: Article)
    @Query("UPDATE articles SET saved=:saved WHERE id=:id") suspend fun setSaved(id: String, saved: Boolean)
    @Query("SELECT * FROM articles WHERE url=:url LIMIT 1") suspend fun articleByUrl(url: String): Article?
    @Upsert suspend fun updateSource(source: Source)
    @Query("UPDATE articles SET fullContent=:text, contentBlocks=:blocks, images=:images, image=:image, contentOrigin=:origin, detailState=:state, detailFetchedAt=:time, detailReason=:reason, extractorId=:extractorId WHERE id=:id")
    suspend fun saveDetails(id: String, text: String, blocks: String, images: String, image: String, origin: String, state: String, time: Long, reason: String = "", extractorId: String = "")
    @Transaction suspend fun installSource(source: Source, articles: List<Article>, quality: FeedQuality) {
        saveSource(source); mergeArticles(articles); saveFeedQuality(quality)
    }
    @Transaction suspend fun mergeArticles(items: List<Article>) {
        val existing = allArticles()
        val byUrl = existing.associateBy { ArticleIdentity.canonical(it.url) }.toMutableMap()
        val byGuid = existing.filter { it.sourceId.isNotBlank() && it.sourceItemId.isNotBlank() }.associateBy { it.sourceId to it.sourceItemId }.toMutableMap()
        items.forEach { incoming ->
            val old = byUrl[ArticleIdentity.canonical(incoming.url)] ?: if (incoming.sourceId.isNotBlank() && incoming.sourceItemId.isNotBlank()) byGuid[incoming.sourceId to incoming.sourceItemId] else null
            val merged = if (old == null) incoming else incoming.copy(
                id = old.id, saved = old.saved,
                body = incoming.body.ifBlank { old.body }, summary = incoming.summary.ifBlank { old.summary },
                sourceItemId = incoming.sourceItemId.ifBlank { old.sourceItemId },
                published = if (incoming.published > 0) incoming.published else old.published,
                kind = if (old.kind != "خبر") old.kind else incoming.kind,
                url = if (ArticleIdentity.same(old.url, incoming.url)) old.url else incoming.url,
                fullContent = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.fullContent else incoming.fullContent.ifBlank { old.fullContent },
                contentBlocks = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.contentBlocks else if (incoming.fullContent.isNotBlank()) incoming.contentBlocks else old.contentBlocks,
                contentOrigin = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.contentOrigin else if (incoming.fullContent.isNotBlank()) incoming.contentOrigin else old.contentOrigin,
                images = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.images else if (incoming.images != "[]") incoming.images else old.images,
                image = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.image else incoming.image.ifBlank { old.image },
                detailState = if (old.contentOrigin in setOf("article_page", "manual") && old.fullContent.isNotBlank()) old.detailState else if (incoming.fullContent.isNotBlank()) "ready" else if (old.detailState == "legacy") "not_requested" else old.detailState,
                detailFetchedAt = old.detailFetchedAt, detailReason = old.detailReason, extractorId = old.extractorId)
            if (old == null) {
                addArticles(listOf(merged))
            } else {
                updateArticle(merged)
            }
            byUrl[ArticleIdentity.canonical(incoming.url)] = merged
            if (merged.sourceId.isNotBlank() && merged.sourceItemId.isNotBlank()) byGuid[merged.sourceId to merged.sourceItemId] = merged
        }
    }
    @Query("SELECT * FROM media_clips ORDER BY published DESC") fun clips(): Flow<List<MediaClip>>
    @Query("SELECT * FROM media_clips") suspend fun allClips(): List<MediaClip>
    @Upsert suspend fun saveClip(clip: MediaClip)
    @Query("UPDATE media_clips SET favorite=:favorite WHERE id=:id") suspend fun favoriteClip(id: String, favorite: Boolean)
    @Query("UPDATE media_clips SET visibleMillis=visibleMillis+:millis, impressions=impressions+1, skips=skips+:skip WHERE id=:id") suspend fun recordClipExposure(id: String, millis: Long, skip: Int)
    @Query("UPDATE media_clips SET liked=:liked WHERE id=:id") suspend fun likeClip(id: String, liked: Boolean)
    @Query("UPDATE media_clips SET hidden=:hidden WHERE id=:id") suspend fun hideClip(id: String, hidden: Boolean)
    @Query("UPDATE media_clips SET opened=:opened WHERE id=:id") suspend fun openClip(id: String, opened: Long)
    @Query("UPDATE media_clips SET liked=0, hidden=0, opened=0, visibleMillis=0, impressions=0, skips=0") suspend fun resetMediaLearning()
    @Transaction suspend fun mergeClips(items: List<MediaClip>) {
        val old = allClips().associateBy { it.id }
        items.forEach { clip ->
            val prior = old[clip.id]
            saveClip(if(prior == null) clip else clip.copy(liked=prior.liked, hidden=prior.hidden, opened=prior.opened, favorite=prior.favorite, visibleMillis=prior.visibleMillis, impressions=prior.impressions, skips=prior.skips))
        }
    }
    @Transaction suspend fun installMediaSource(source: Source, clips: List<MediaClip>) {
        saveSource(source); mergeClips(clips)
    }
    @Query("SELECT * FROM scene_topics ORDER BY saved DESC,updated DESC") fun sceneTopics(): Flow<List<SceneTopic>>
    @Transaction suspend fun mergeTrendSnapshot(topics:List<SceneTopic>,signals:List<TrendSignal>,now:Long) {
        topics.forEach { topic -> val prior=sceneTopic(topic.id);saveSceneTopic(if(prior==null)topic else prior.copy(updated=now)) }
        addTrendSignals(signals)
    }
    @Query("SELECT * FROM scene_topics") suspend fun allSceneTopics(): List<SceneTopic>
    @Query("SELECT * FROM scene_topics WHERE id=:id") suspend fun sceneTopic(id:String): SceneTopic?
    @Upsert suspend fun saveSceneTopic(topic:SceneTopic)
    @Query("UPDATE scene_topics SET saved=:saved WHERE id=:id") suspend fun followSceneTopic(id:String,saved:Boolean)
    @Query("SELECT * FROM trend_signals ORDER BY observedAt DESC") fun trendSignals():Flow<List<TrendSignal>>
    @Query("SELECT * FROM trend_signals") suspend fun allTrendSignals():List<TrendSignal>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun addTrendSignals(signals:List<TrendSignal>)
    @Query("SELECT * FROM topic_articles") fun topicArticles():Flow<List<TopicArticle>>
    @Query("SELECT * FROM topic_articles") suspend fun allTopicArticles():List<TopicArticle>
    @Upsert suspend fun saveTopicArticle(link:TopicArticle)
    @Query("SELECT * FROM followed_social_accounts ORDER BY favorite DESC,name") fun socialAccounts():Flow<List<FollowedSocialAccount>>
    @Query("SELECT * FROM followed_social_accounts") suspend fun allSocialAccounts():List<FollowedSocialAccount>
    @Upsert suspend fun saveSocialAccount(account:FollowedSocialAccount)
    @Query("DELETE FROM followed_social_accounts WHERE id=:id") suspend fun deleteSocialAccount(id:String)
    @Query("SELECT * FROM saved_social_posts ORDER BY created DESC") fun socialPosts():Flow<List<SavedSocialPost>>
    @Query("SELECT * FROM saved_social_posts") suspend fun allSocialPosts():List<SavedSocialPost>
    @Upsert suspend fun saveSocialPost(post:SavedSocialPost)
    @Query("DELETE FROM saved_social_posts WHERE id=:id") suspend fun deleteSocialPost(id:String)
    @Query("SELECT * FROM topic_analyses") fun topicAnalyses():Flow<List<TopicAnalysisLink>>
    @Query("SELECT * FROM topic_analyses") suspend fun allTopicAnalyses():List<TopicAnalysisLink>
    @Upsert suspend fun saveTopicAnalysis(link:TopicAnalysisLink)
    @Query("SELECT * FROM verification_claims ORDER BY created DESC") fun claims():Flow<List<VerificationClaim>>
    @Query("SELECT * FROM verification_claims") suspend fun allClaims():List<VerificationClaim>
    @Upsert suspend fun saveClaim(claim:VerificationClaim)
    @Query("DELETE FROM verification_claims WHERE id=:id") suspend fun deleteClaimRow(id:String)
    @Query("DELETE FROM claim_evidence WHERE claimId=:id") suspend fun deleteClaimEvidenceFor(id:String)
    @Transaction suspend fun deleteClaim(id:String) { deleteClaimEvidenceFor(id);deleteClaimRow(id) }
    @Query("SELECT * FROM claim_evidence") fun claimEvidence():Flow<List<ClaimEvidence>>
    @Query("SELECT * FROM claim_evidence") suspend fun allClaimEvidence():List<ClaimEvidence>
    @Upsert suspend fun saveClaimEvidence(evidence:ClaimEvidence)
    @Query("SELECT * FROM topic_notes") fun topicNotes():Flow<List<TopicNote>>
    @Query("SELECT * FROM topic_notes") suspend fun allTopicNotes():List<TopicNote>
    @Upsert suspend fun saveTopicNote(link:TopicNote)
    @Query("SELECT * FROM topic_timeline ORDER BY at") fun topicTimeline():Flow<List<TopicTimelineEvent>>
    @Query("SELECT * FROM topic_timeline") suspend fun allTopicTimeline():List<TopicTimelineEvent>
    @Upsert suspend fun saveTopicTimeline(event:TopicTimelineEvent)
    @Query("SELECT * FROM scene_fetch_state") fun sceneFetchStates():Flow<List<SceneFetchState>>
    @Query("SELECT * FROM scene_fetch_state WHERE id=:id") suspend fun sceneFetchState(id:String):SceneFetchState?
    @Query("SELECT * FROM scene_fetch_state") suspend fun allSceneFetchStates():List<SceneFetchState>
    @Upsert suspend fun saveSceneFetchState(state:SceneFetchState)
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated DESC") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Upsert suspend fun saveNote(note: Note)
    @Query("DELETE FROM notes WHERE id=:id") suspend fun deleteNote(id: String)
    @Query("SELECT * FROM analyses ORDER BY created DESC") fun analyses(): Flow<List<Analysis>>
    @Query("SELECT * FROM analyses") suspend fun allAnalyses(): List<Analysis>
    @Query("SELECT * FROM analyses WHERE cacheKey=:key ORDER BY created DESC LIMIT 1") suspend fun cached(key: String): Analysis?
    @Upsert suspend fun saveAnalysis(analysis: Analysis)
    @Query("DELETE FROM analyses WHERE id=:id") suspend fun deleteAnalysis(id: String)
    @Query("SELECT * FROM conversations ORDER BY updated DESC") fun conversations(): Flow<List<Conversation>>
    @Query("SELECT * FROM conversations") suspend fun allConversations(): List<Conversation>
    @Upsert suspend fun saveConversation(conversation: Conversation)
    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY created ASC, rowid ASC") fun messages(id: String): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY created ASC, rowid ASC") suspend fun contextMessages(id: String): List<Message>
    @Query("SELECT * FROM messages ORDER BY created ASC") suspend fun allMessages(): List<Message>
    @Upsert suspend fun saveMessage(message: Message)
    @Query("SELECT * FROM sources ORDER BY name") fun sources(): Flow<List<Source>>
    @Query("SELECT * FROM sources") suspend fun allSources(): List<Source>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveSource(source: Source)
    @Query("DELETE FROM sources WHERE id=:id") suspend fun deleteSource(id: String)
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<Attachment>
    @Upsert suspend fun saveAttachment(attachment: Attachment)
    @Query("SELECT * FROM usage ORDER BY created DESC") fun usage(): Flow<List<Usage>>
    @Query("SELECT * FROM usage WHERE provider=:provider AND created>=:since") suspend fun usageSince(provider: String, since: Long): List<Usage>
    @Upsert suspend fun saveUsage(usage: Usage)
}
@Database(entities = [Article::class, Note::class, Analysis::class, Conversation::class, Message::class, Source::class, Attachment::class, Usage::class, FeedQuality::class, MediaClip::class, SceneTopic::class, TrendSignal::class, TopicArticle::class, FollowedSocialAccount::class, SavedSocialPost::class, TopicAnalysisLink::class, VerificationClaim::class, ClaimEvidence::class, TopicNote::class, TopicTimelineEvent::class, SceneFetchState::class, WorldPublisher::class, WorldFeed::class, WorldArticle::class, WorldTranslation::class, WorldGlossary::class], version = 8, exportSchema = true)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun dao(): NabdDao
    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db:SupportSQLiteDatabase) { WorldSchema.statements.forEach(db::execSQL) } }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) { SceneSchema.statements.forEach(db::execSQL) }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("favorite", "visibleMillis", "impressions", "skips").forEach {
                    db.execSQL("ALTER TABLE media_clips ADD COLUMN `$it` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `media_clips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `playUrl` TEXT NOT NULL, `provider` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `source` TEXT NOT NULL, `topic` TEXT NOT NULL, `published` INTEGER NOT NULL, `thumbnail` TEXT NOT NULL, `liked` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `opened` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("summary" to "TEXT NOT NULL DEFAULT ''", "fullContent" to "TEXT NOT NULL DEFAULT ''",
                    "contentBlocks" to "TEXT NOT NULL DEFAULT '[]'", "images" to "TEXT NOT NULL DEFAULT '[]'",
                    "sourceItemId" to "TEXT NOT NULL DEFAULT ''", "sourceId" to "TEXT NOT NULL DEFAULT ''", "contentOrigin" to "TEXT NOT NULL DEFAULT 'excerpt'",
                    "detailState" to "TEXT NOT NULL DEFAULT 'not_requested'", "detailFetchedAt" to "INTEGER NOT NULL DEFAULT 0")
                    .forEach { (name, type) -> db.execSQL("ALTER TABLE articles ADD COLUMN `$name` $type") }
                db.execSQL("UPDATE articles SET summary=body, detailState='legacy'")
                db.execSQL("UPDATE articles SET sourceId=COALESCE((SELECT id FROM sources WHERE sources.name=articles.source AND (SELECT COUNT(*) FROM sources s WHERE s.name=articles.source)=1), '')")
                db.execSQL("ALTER TABLE sources ADD COLUMN htmlAllowed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sources ADD COLUMN termsUrl TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `feed_quality` (`sourceId` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `checkedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `sampleSize` INTEGER NOT NULL, `extendedCount` INTEGER NOT NULL, `shortCount` INTEGER NOT NULL, `excerptCount` INTEGER NOT NULL, `imageCount` INTEGER NOT NULL, `latestPublished` INTEGER NOT NULL, `httpCode` INTEGER NOT NULL, `error` TEXT NOT NULL, PRIMARY KEY(`sourceId`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN detailReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN extractorId TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, NabdDatabase::class.java, "nabd.db")
            .enableMultiInstanceInvalidation()
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
    }
}

