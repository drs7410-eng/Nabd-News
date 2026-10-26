package com.nabd.news

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TopicRepository(private val db:NabdDatabase) {
    private val dao=db.dao();private val lock=Mutex()
    suspend fun create(title:String,description:String=""):SceneTopic {
        require(title.isNotBlank()){ "أدخل عنوان الموضوع" }
        return SceneTopic(newId(),title.trim().take(500),description.take(10000),saved=true).also{dao.saveSceneTopic(it)}
    }
    suspend fun link(topic:String,article:String,include:Boolean) {
        require(dao.sceneTopic(topic)!=null && dao.article(article)!=null)
        val a=dao.article(article)!!
        dao.saveTopicArticle(TopicArticle(topic,article,if(include)1 else -1,a.title,a.published))
    }
    suspend fun note(topic:String,title:String,body:String):Note {
        require(dao.sceneTopic(topic)!=null && body.isNotBlank()){"اكتب ملاحظة للموضوع"}
        val note=Note(title=title.ifBlank{"ملاحظة موضوع"},body=body,category="نبض المشهد",tags="موضوع",links="",articleIds=dao.allTopicArticles().filter{it.topicId==topic && it.mode>=0}.joinToString(","){it.articleId})
        db.withTransaction { dao.saveNote(note);dao.saveTopicNote(TopicNote(topic,note.id)) };return note
    }
    suspend fun rebuild(now:Long=System.currentTimeMillis()):Int=lock.withLock {
        val articles=dao.allArticles()
        val groups=withContext(Dispatchers.Default){NewsClusteringEngine().cluster(articles,now)}
        val existingTopics=dao.allSceneTopics().associateBy{it.id}
        val links=dao.allTopicArticles();val used=mutableSetOf<String>()
        db.withTransaction {
            groups.filter{it.articles.size>=2}.forEach { group ->
                val ids=group.articles.map{it.id}.toSet()
                val reusable=links.filter{it.articleId in ids && it.mode>=0 && existingTopics[it.topicId]?.origin=="local" && it.topicId !in used}
                    .groupingBy{it.topicId}.eachCount().maxByOrNull{it.value}?.key
                val id=reusable?:AiPolicy.hash("event|${group.articles.minBy{it.published}.id}")
                used+=id
                val prior=existingTopics[id]
                val region=if(group.articles.any{ArabicTopicText.saudi(it.title)})"SA" else "UNKNOWN"
                val topic=prior?.copy(updated=now)?:SceneTopic(id,group.articles.first().title,region=region,origin="local",created=now,updated=now)
                dao.saveSceneTopic(topic)
                group.articles.forEach { article -> if(links.none{it.topicId==id && it.articleId==article.id && it.mode!=0})dao.saveTopicArticle(links.firstOrNull{it.topicId==id && it.articleId==article.id}?:TopicArticle(id,article.id)) }
            }
            // Search topics are broader than event clusters; show this distinction in the UI.
            dao.allSceneTopics().filter{it.origin=="google" || it.saved}.forEach { topic ->
                val terms=ArabicTopicText.tokens(topic.title)
                if(terms.isNotEmpty()) articles.filter{it.published>=now-7*86400000L && it.published<=now+300000}.forEach { article ->
                    val headline=ArabicTopicText.tokens(article.title)
                    val phrase=ArabicTopicText.normalize(article.title).contains(ArabicTopicText.normalize(topic.title))
                    val matched=phrase || (terms.size>=3 && (terms intersect headline).size>=maxOf(3,(terms.size*0.6).toInt()))
                    if(matched && links.none{it.topicId==topic.id && it.articleId==article.id && it.mode!=0})dao.saveTopicArticle(links.firstOrNull{it.topicId==topic.id && it.articleId==article.id}?:TopicArticle(topic.id,article.id))
                }
            }
            val allLinks=dao.allTopicArticles().filter{it.mode>=0};val byId=articles.associateBy{it.id}
            dao.allSceneTopics().forEach { topic ->
                val related=allLinks.filter{it.topicId==topic.id}.mapNotNull{byId[it.articleId]}
                related.forEach { a ->
                    val prior=allLinks.firstOrNull{it.topicId==topic.id && it.articleId==a.id} ?: return@forEach
                    if(prior.lastTitle.isNotBlank() && (prior.lastTitle!=a.title || prior.lastPublished!=a.published)) {
                        dao.saveTopicTimeline(TopicTimelineEvent(id=AiPolicy.hash("update|${topic.id}|${a.id}|${a.title}|${a.published}"),topicId=topic.id,title="رصد تغير في عنوان أو تاريخ المصدر: ${a.title}",at=now,kind="source_update",reference=a.url))
                    }
                    dao.saveTopicArticle(prior.copy(lastTitle=a.title,lastPublished=a.published))
                }
                if(related.isNotEmpty())dao.addTrendSignals(listOf(TrendEngine.coverage(topic.id,related,now)))
            }
            dao.saveSceneFetchState(SceneFetchState("local-clustering",now,now,200))
        }
        groups.count{it.articles.size>=2}
    }
}
object SharedPostParser {
    fun account(value:String):String? {
        val cleaned=value.trim().removePrefix("@").takeIf{it.matches(Regex("[A-Za-z0-9_]{1,15}"))}
        val link=MediaPolicy.xLink(if(cleaned!=null)"https://x.com/$cleaned" else value.trim()) ?: return null
        return link.takeIf{!it.contains("/status/")}?.substringAfter("x.com/")
    }
    fun post(value:String):Pair<String,String>? {
        val links=Regex("https://(?:www\\.)?(?:x\\.com|twitter\\.com)/[^\\s<>\\\"]+",RegexOption.IGNORE_CASE).findAll(value.take(20000))
        return links.mapNotNull { match ->
            val candidate=MediaPolicy.xLink(match.value.trimEnd('.',',','،',')',']'))?:return@mapNotNull null
            if(!candidate.contains("/status/"))null else candidate to candidate.substringAfter("x.com/").substringBefore('/')
        }.firstOrNull()
    }
}
data class SharedPostDraft(val url:String,val handle:String,val sharedText:String="")
class SharedPostRepository(private val db:NabdDatabase) {
    private val dao=db.dao()
    suspend fun save(draft:SharedPostDraft,title:String,text:String,note:String,topic:String):SavedSocialPost {
        val parsed=SharedPostParser.post(draft.url)?:error("الرابط ليس منشور X عامًا صالح البنية")
        require(topic.isBlank() || dao.sceneTopic(topic)!=null){"الموضوع غير موجود"}
        val id=AiPolicy.hash("x-post|${parsed.first.substringAfter("/status/")}")
        val old=dao.allSocialPosts().firstOrNull{it.id==id}
        val post=SavedSocialPost(id,parsed.first,if(parsed.second=="i")"" else parsed.second,title.trim().take(1000),text.take(30000),note.take(10000),topic,old?.created?:System.currentTimeMillis())
        dao.saveSocialPost(post);return post
    }
    suspend fun saveAccount(handle:String,name:String,category:String,group:String,favorite:Boolean,id:String?=null) {
        val user=SharedPostParser.account(handle)?:error("أدخل اسم مستخدم أو رابط حساب X صالحًا")
        val prior=dao.allSocialAccounts().firstOrNull{it.handle.equals(user,true)}
        dao.saveSocialAccount(FollowedSocialAccount(prior?.id?:id?:AiPolicy.hash("x-account|${user.lowercase()}"),user,name.ifBlank{user}.take(200),category.take(100),group.ifBlank{"عام"}.take(100),favorite))
        // A renamed handle in an edit remains the same local account; don't duplicate a target handle.
        if(id!=null && prior!=null && prior.id!=id)dao.deleteSocialAccount(id)
    }
    suspend fun importLegacyAccounts() {
        dao.allSources().filter{it.type=="X"}.forEach { s ->
            val user=SharedPostParser.account(s.url)
            if(user!=null && dao.allSocialAccounts().none{it.handle.equals(user,true)})saveAccount(user,s.name,"أخبار","من مصادر نبض",false)
        }
    }
}

