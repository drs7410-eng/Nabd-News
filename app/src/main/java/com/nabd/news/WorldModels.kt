package com.nabd.news

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="world_publishers")
data class WorldPublisher(@PrimaryKey val id:String,val name:String,val arabicName:String,val website:String,val country:String,val language:String,
    val sections:String="[]",val termsUrl:String="",val rightsNote:String="",val permission:String="unreviewed",val allowCache:Boolean=false,
    val allowLocal:Boolean=false,val allowCloud:Boolean=false,val allowHtml:Boolean=false,val allowImages:Boolean=false,val enabled:Boolean=false)
@Entity(tableName="world_feeds")
data class WorldFeed(@PrimaryKey val id:String,val publisherId:String,val url:String,val category:String="العالم",val language:String="en",val status:String="candidate",
    val attempted:Long=0,val succeeded:Long=0,val httpCode:Int=0,val error:String="",val etag:String="",val modified:String="",val articleCount:Int=0,val fullCount:Int=0,val imageCount:Int=0,val latestPublished:Long=0)
@Entity(tableName="world_articles")
data class WorldArticle(@PrimaryKey val articleId:String,val publisherId:String,val feedId:String,val language:String,val languageBasis:String="publisher_declared",val author:String="",val attribution:String="",val restriction:String="")
@Entity(tableName="world_translations")
data class WorldTranslation(@PrimaryKey val id:String,val articleId:String,val slot:String,val language:String,val target:String="ar",val textHash:String,val glossaryHash:String,
    val engine:String,val engineVersion:String,val original:String,val translated:String="",val state:String="ready",val error:String="",val created:Long=System.currentTimeMillis(),val analysisId:String="")
@Entity(tableName="world_glossary")
data class WorldGlossary(@PrimaryKey val id:String=newId(),val original:String,val arabic:String,val domain:String="عام",val language:String="en",val reference:String="",val notes:String="",val active:Boolean=true,val updated:Long=System.currentTimeMillis())

object WorldSchema {
    val statements=listOf(
        "CREATE TABLE IF NOT EXISTS world_publishers (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,arabicName TEXT NOT NULL,website TEXT NOT NULL,country TEXT NOT NULL,language TEXT NOT NULL,sections TEXT NOT NULL,termsUrl TEXT NOT NULL,rightsNote TEXT NOT NULL,permission TEXT NOT NULL,allowCache INTEGER NOT NULL,allowLocal INTEGER NOT NULL,allowCloud INTEGER NOT NULL,allowHtml INTEGER NOT NULL,allowImages INTEGER NOT NULL,enabled INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS world_feeds (id TEXT NOT NULL PRIMARY KEY,publisherId TEXT NOT NULL,url TEXT NOT NULL,category TEXT NOT NULL,language TEXT NOT NULL,status TEXT NOT NULL,attempted INTEGER NOT NULL,succeeded INTEGER NOT NULL,httpCode INTEGER NOT NULL,error TEXT NOT NULL,etag TEXT NOT NULL,modified TEXT NOT NULL,articleCount INTEGER NOT NULL,fullCount INTEGER NOT NULL,imageCount INTEGER NOT NULL,latestPublished INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS world_articles (articleId TEXT NOT NULL PRIMARY KEY,publisherId TEXT NOT NULL,feedId TEXT NOT NULL,language TEXT NOT NULL,languageBasis TEXT NOT NULL,author TEXT NOT NULL,attribution TEXT NOT NULL,restriction TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS world_translations (id TEXT NOT NULL PRIMARY KEY,articleId TEXT NOT NULL,slot TEXT NOT NULL,language TEXT NOT NULL,target TEXT NOT NULL,textHash TEXT NOT NULL,glossaryHash TEXT NOT NULL,engine TEXT NOT NULL,engineVersion TEXT NOT NULL,original TEXT NOT NULL,translated TEXT NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL,created INTEGER NOT NULL,analysisId TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS world_glossary (id TEXT NOT NULL PRIMARY KEY,original TEXT NOT NULL,arabic TEXT NOT NULL,domain TEXT NOT NULL,language TEXT NOT NULL,reference TEXT NOT NULL,notes TEXT NOT NULL,active INTEGER NOT NULL,updated INTEGER NOT NULL)"
    )
}
val worldCategories=listOf("العالم","الاقتصاد والأسواق","السياسة","التقنية والذكاء الاصطناعي","العلوم","الصحة","الطاقة","الأعمال","الرأي والتحليل","الثقافة")
