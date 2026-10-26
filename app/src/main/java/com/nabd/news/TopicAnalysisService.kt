package com.nabd.news

import com.google.gson.JsonParser

data class TopicContext(val text:String,val articleCount:Int,val totalArticles:Int,val postTexts:Int)
class TopicAnalysisService(private val db:NabdDatabase,private val ai:AiRepository,private val nano:NanoEngine) {
    private val dao=db.dao()
    suspend fun context(topicId:String,includePostText:Boolean):TopicContext {
        val topic=dao.sceneTopic(topicId)?:error("الموضوع غير موجود")
        val ids=dao.allTopicArticles().filter{it.topicId==topicId && it.mode>=0}.map{it.articleId}.toSet()
        val articles=dao.allArticles().filter{it.id in ids}.sortedByDescending{it.published}
        val selected=articles.take(8)
        val posts=dao.allSocialPosts().filter{it.topicId==topicId}
        val signals=dao.allTrendSignals().filter{it.topicId==topicId}.groupBy{it.source}.mapNotNull{it.value.maxByOrNull{s->s.observedAt}}
        val text=buildString {
            append("موضوع الرصد: ${topic.title}\nنوع الموضوع: ${topic.origin}\n")
            append("السياق أدناه يتضمن آخر ${selected.size} من ${articles.size} أخبار مرتبطة محليًا. التواريخ هي تواريخ المصادر عند توفرها، لا يوجد تصفح حي إلا إذا فُعّل البحث الخارجي فعلًا. وصف الموضوع والملاحظات الشخصية مستبعدة افتراضيًا.\n")
            signals.forEach { s -> append("<SOURCE kind='trend_signal'>${TrendEngine.label(s)} • ${s.region} • رُصد في ${s.observedAt} • تاريخ المصدر ${s.published} • حجم البحث التقريبي كما أرسله المصدر: ${s.traffic.ifBlank{"غير متاح"}} • ${s.sourceUrl}\n${if(s.source=="local_coverage")"عدد الأخبار المحفوظة ${s.newsCount}، نطاقات النشر ${s.sourceCount}، آخر 6 ساعات ${s.recentCount}، الست السابقة ${s.previousCount}." else "إشارة بحث فقط؛ لا توفر عدد مصادر مستقلة أو أخبار جلبها نبض."} ليست أعداد مشاهدات أو استقلالًا تحريريًا مثبتًا.</SOURCE>\n") }
            selected.forEach { a -> append("<SOURCE kind='news' id='${a.id}'>\n${a.source} • ${a.kind} • تاريخ النشر ${a.published}\n${a.url}\n${a.title}\nحالة النص: ${if(a.isExcerpt)"مقتطف أو جزئي، ليس مقالًا كاملًا" else a.contentOrigin}\n${a.availableText.ifBlank{"متن غير متاح؛ لا تخمنه من الرابط"}}\n</SOURCE>\n") }
            posts.forEach { p -> append("<SOURCE kind='saved_social_post'>${p.url}\nهوية الحساب الظاهرة في الرابط: ${p.handle.ifBlank{"غير متاحة"}}\n")
                if(includePostText && p.text.isNotBlank())append("نص أضافه أو شاركه المستخدم، لم يُجلب من X تلقائيًا:\n${p.text}\n")
                else append("رابط فقط؛ نص المنشور غير متاح أو لم يؤذن بإرساله. لا تخمن محتواه.\n")
                append("</SOURCE>\n")
            }
            append("أجب اعتمادًا على المعطيات فقط. لا تفسر سبب رواج موضوع دون دليل. ميّز الحقائق والتحليل والاحتمالات، ومصدر كل ادعاء. لا تعلن صحة ادعاء من رأي النموذج. دون أدلة خارجية موثوقة ومستقلة فالنتيجة تحليل للمصادر المتاحة وأدلة غير كافية. في المقارنة وضّح الأرقام والتواريخ والتصريحات المختلفة دون محو التعارض.")
        }
        require(text.toByteArray().size<=AiArticleContext.MAX_UTF8_BYTES){"السياق أكبر من الحد المحلي. افصل أخبارًا من الموضوع أو أرسل مجموعة أصغر؛ لم يُحذف جزء من النص."}
        return TopicContext(text,selected.size,articles.size,if(includePostText)posts.count{it.text.isNotBlank()} else 0)
    }
    suspend fun run(topicId:String,op:String,provider:String,question:String,posts:Boolean,notes:Boolean,web:Boolean,chat:Boolean=false):Analysis {
        val topic=dao.sceneTopic(topicId)?:error("الموضوع غير موجود")
        val articleIds=dao.allTopicArticles().filter{it.topicId==topicId&&it.mode>=0}.map{it.articleId}
        if(provider!="Gemini Nano")WorldRights.requireCloud(dao,articleIds)
        else articleIds.forEach { id->dao.worldArticle(id)?.let{m->require(dao.worldPublisher(m.publisherId)?.let{WorldRights.local(it,m)}==true){"لم يُسجل إذن المعالجة المحلية لأحد المصادر العالمية"}} }
        val context=context(topicId,posts)
        val noteIds=if(notes)dao.allTopicNotes().filter{it.topicId==topicId}.map{it.noteId}.toSet() else emptySet()
        val personal=dao.allNotes().filter{it.id in noteIds}
        val result=if(provider=="Gemini Nano") {
            require(op=="تلخيص موضوع"){"المحرك المحلي متاح لتجربة التلخيص فقط"}
            require(!notes && !posts){"المحرك المحلي التجريبي لا يضم الملاحظات أو نصوص X في هذه العملية"}
            val response=nano.generate(AiRequest("local-summary","local-summary-only",context.text))
            Analysis(articleIds="",operation=op,provider=provider,model="ML Kit beta",result=response.text,sources="",status="تلخيص محلي تجريبي؛ جودة العربية غير مضمونة",cacheKey=AiPolicy.hash(context.text+op+"nano"),cost=0.0).also{dao.saveAnalysis(it)}
        } else ai.analyze(emptyList(),op,provider,when(op) {
            "تحليل موضوع" -> "اعرض المعلومات الرئيسية وخلفية موثقة وخطًا زمنيًا بالتواريخ المتاحة والأطراف والتداعيات المحتملة، وحدود المعرفة. افصل الحقائق عن الاحتمالات.\n$question"
            "تلخيص موضوع" -> "لخص تطورات الموضوع في ثلاث نقاط مع عزو المعلومات للمصادر المتاحة دون اختلاق.\n$question"
            "مقارنة التغطيات" -> "قارن المعلومات المتفق عليها والمختلفة، وأسماء المصادر والتصريحات والأرقام والتواريخ، وما لم يُحسم.\n$question"
            else -> question
        },if(chat)"topic-chat:$topicId" else null,personal,web,
            monitorContext=context.text,contextOwnerId="topic:$topicId",contextTitle=topic.title)
        dao.saveTopicAnalysis(TopicAnalysisLink(topicId,result.id))
        return result
    }
}
object FactCheckCoordinator {
    /** Extract only claims explicitly returned by the chosen model. Never interpret them as verified facts. */
    fun parseClaims(result:String,topic:String):List<VerificationClaim> {
        val clean=result.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root=JsonParser.parseString(clean)
        val list=if(root.isJsonArray)root.asJsonArray else root.asJsonObject.getAsJsonArray("claims")?:error("لم تصل قائمة ادعاءات منظمة")
        return list.take(20).mapNotNull { element ->
            val obj=element.asJsonObject
            val text=obj.get("claim")?.takeIf{it.isJsonPrimitive}?.asString?.trim()?.take(2000).orEmpty()
            if(text.isBlank())null else VerificationClaim(id=AiPolicy.hash("$topic|$text"),topicId=topic,text=text,
                verifiable=if(obj.get("verifiable")?.isJsonPrimitive==true)runCatching{if(obj.get("verifiable").asBoolean)"yes" else "no"}.getOrDefault("unknown") else "unknown")
        }.distinctBy{it.id}
    }
    fun status(analysis:Analysis)=if(analysis.status.contains("أدلة خارجية"))"استشهادات خارجية تحتاج مراجعة؛ ليست حكمًا بالصحة" else "أدلة غير كافية — تحليل للمصادر المتاحة"
}

