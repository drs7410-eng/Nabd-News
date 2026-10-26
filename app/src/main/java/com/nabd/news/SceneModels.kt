package com.nabd.news

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="scene_topics")
data class SceneTopic(@PrimaryKey val id:String,val title:String,val description:String="",val region:String="UNKNOWN",val origin:String="manual",val created:Long=System.currentTimeMillis(),val updated:Long=created,val saved:Boolean=false)
@Entity(tableName="trend_signals")
data class TrendSignal(@PrimaryKey val id:String,val topicId:String,val source:String,val region:String,val observedAt:Long,val published:Long,val traffic:String="",val sourceUrl:String="",val newsLinks:String="[]",val newsCount:Int=0,val sourceCount:Int=0,val recentCount:Int=0,val previousCount:Int=0)
@Entity(tableName="topic_articles",primaryKeys=["topicId","articleId"])
data class TopicArticle(val topicId:String,val articleId:String,val mode:Int=0,val lastTitle:String="",val lastPublished:Long=0) // 0 automatic, 1 manual, -1 explicitly excluded
@Entity(tableName="followed_social_accounts")
data class FollowedSocialAccount(@PrimaryKey val id:String,val handle:String,val name:String,val category:String="أخبار",val groupName:String="عام",val favorite:Boolean=false)
@Entity(tableName="saved_social_posts")
data class SavedSocialPost(@PrimaryKey val id:String,val url:String,val handle:String,val title:String="",val text:String="",val personalNote:String="",val topicId:String="",val created:Long=System.currentTimeMillis())
@Entity(tableName="topic_analyses",primaryKeys=["topicId","analysisId"])
data class TopicAnalysisLink(val topicId:String,val analysisId:String,val created:Long=System.currentTimeMillis())
@Entity(tableName="verification_claims")
data class VerificationClaim(@PrimaryKey val id:String=newId(),val topicId:String,val text:String,val verifiable:String="unknown",val status:String="أدلة غير كافية",val analysisId:String="",val created:Long=System.currentTimeMillis())
@Entity(tableName="claim_evidence")
data class ClaimEvidence(@PrimaryKey val id:String=newId(),val claimId:String,val url:String,val stance:String="context",val published:Long=0,val description:String="")
@Entity(tableName="topic_notes",primaryKeys=["topicId","noteId"])
data class TopicNote(val topicId:String,val noteId:String)
@Entity(tableName="topic_timeline")
data class TopicTimelineEvent(@PrimaryKey val id:String=newId(),val topicId:String,val title:String,val at:Long,val kind:String="manual",val reference:String="")
@Entity(tableName="scene_fetch_state")
data class SceneFetchState(@PrimaryKey val id:String,val attempted:Long,val succeeded:Long=0,val httpCode:Int=0,val error:String="",val etag:String="",val modified:String="")
data class SignalNewsReference(val title:String,val url:String,val source:String)

/** New tables only. Used by the migration and compared against Room's exported schema in tests. */
object SceneSchema {
    val statements=listOf(
        "CREATE TABLE IF NOT EXISTS scene_topics (id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,description TEXT NOT NULL,region TEXT NOT NULL,origin TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,saved INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS trend_signals (id TEXT NOT NULL PRIMARY KEY,topicId TEXT NOT NULL,source TEXT NOT NULL,region TEXT NOT NULL,observedAt INTEGER NOT NULL,published INTEGER NOT NULL,traffic TEXT NOT NULL,sourceUrl TEXT NOT NULL,newsLinks TEXT NOT NULL,newsCount INTEGER NOT NULL,sourceCount INTEGER NOT NULL,recentCount INTEGER NOT NULL,previousCount INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS topic_articles (topicId TEXT NOT NULL,articleId TEXT NOT NULL,mode INTEGER NOT NULL,lastTitle TEXT NOT NULL,lastPublished INTEGER NOT NULL,PRIMARY KEY(topicId,articleId))",
        "CREATE TABLE IF NOT EXISTS followed_social_accounts (id TEXT NOT NULL PRIMARY KEY,handle TEXT NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,groupName TEXT NOT NULL,favorite INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS saved_social_posts (id TEXT NOT NULL PRIMARY KEY,url TEXT NOT NULL,handle TEXT NOT NULL,title TEXT NOT NULL,text TEXT NOT NULL,personalNote TEXT NOT NULL,topicId TEXT NOT NULL,created INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS topic_analyses (topicId TEXT NOT NULL,analysisId TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(topicId,analysisId))",
        "CREATE TABLE IF NOT EXISTS verification_claims (id TEXT NOT NULL PRIMARY KEY,topicId TEXT NOT NULL,text TEXT NOT NULL,verifiable TEXT NOT NULL,status TEXT NOT NULL,analysisId TEXT NOT NULL,created INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS claim_evidence (id TEXT NOT NULL PRIMARY KEY,claimId TEXT NOT NULL,url TEXT NOT NULL,stance TEXT NOT NULL,published INTEGER NOT NULL,description TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS topic_notes (topicId TEXT NOT NULL,noteId TEXT NOT NULL,PRIMARY KEY(topicId,noteId))",
        "CREATE TABLE IF NOT EXISTS topic_timeline (id TEXT NOT NULL PRIMARY KEY,topicId TEXT NOT NULL,title TEXT NOT NULL,at INTEGER NOT NULL,kind TEXT NOT NULL,reference TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS scene_fetch_state (id TEXT NOT NULL PRIMARY KEY,attempted INTEGER NOT NULL,succeeded INTEGER NOT NULL,httpCode INTEGER NOT NULL,error TEXT NOT NULL,etag TEXT NOT NULL,modified TEXT NOT NULL)"
    )
}

