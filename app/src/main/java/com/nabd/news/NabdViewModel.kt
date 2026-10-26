package com.nabd.news

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NabdViewModel @JvmOverloads constructor(app: Application, val worldLocal: WorldLocalTranslator = MlKitWorldTranslator()) : AndroidViewModel(app) {
    val db = NabdDatabase.open(app)
    val dao = db.dao()
    val settings = SecureSettings(app)
    private val network = Network()
    private val newsClient = network.client.newBuilder().callTimeout(35, java.util.concurrent.TimeUnit.SECONDS).readTimeout(25, java.util.concurrent.TimeUnit.SECONDS).cache(okhttp3.Cache(java.io.File(app.cacheDir, "news-http"), 20L * 1024 * 1024)).build()
    val news = NewsRepository(dao, newsClient)
    val media = MediaRepository(dao, newsClient)
    val clips = dao.clips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mediaBusy = MutableStateFlow(false)
    fun refreshMedia() { if(mediaBusy.value) return; task { mediaBusy.value=true; try { notices.emit(media.refresh()) } finally { mediaBusy.value=false } } }
    val nano = NanoEngine(app)
    val backup = BackupRepository(db)
    val ai = AiRepository(dao, settings, mapOf(
        "OpenAI" to OpenAiProvider({ settings.getKey("OpenAI") ?: throw AiFailure("أدخل مفتاح OpenAI أولًا") }, network.api("https://api.openai.com/v1/")),
        "Gemini" to GeminiProvider({ settings.getKey("Gemini") ?: throw AiFailure("أدخل مفتاح Gemini أولًا") }, network.api("https://generativelanguage.googleapis.com/v1beta/"))))
    val articles = dao.articles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes = dao.notes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val analyses = dao.analyses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val conversations = dao.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sources = dao.sources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val feedQuality = dao.feedQuality().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val checkingFeeds = MutableStateFlow<Set<String>>(emptySet())
    fun checkFeed(source: Source) {
        if (source.id in checkingFeeds.value) return
        checkingFeeds.update { it + source.id }
        task { try { news.audit(source) } finally { checkingFeeds.update { it - source.id } } }
    }
    val usage = dao.usage().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val configVersion = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val result = MutableStateFlow<Analysis?>(null)
    val loadingDetails = MutableStateFlow<Set<String>>(emptySet())
    fun loadDetails(id: String, force: Boolean = false) {
        if (id in loadingDetails.value) return
        task {
            loadingDetails.update { it + id }
            try { news.loadDetails(id, force) } finally { loadingDetails.update { it - id } }
        }
    }
    val world=WorldRepository(db,newsClient)
    val worldTranslation=WorldTranslationEngine(dao,worldLocal,ai,settings)
    val worldPublishers=dao.worldPublishers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val worldFeeds=dao.worldFeeds().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val worldArticles=dao.worldArticles().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val worldGlossary=dao.worldGlossary().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val worldTranslatedIds=dao.worldTranslatedIds().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val worldBusy=MutableStateFlow(false)
    val worldDownloads=MutableStateFlow<Set<String>>(emptySet())
    val worldModelStatus=MutableStateFlow<Map<String,String>>(emptyMap())
    fun initializeWorld()=task {world.seed()}
    fun refreshWorld() {if(worldBusy.value)return;task {worldBusy.value=true;try {
        val states=world.refresh();topics.rebuild()
        notices.emit(if(states.isEmpty())"فعّل مصدرًا تم التحقق من خلاصته وأذوناته أولًا" else "فُحصت ${states.size} خلاصات؛ ${states.count{it.error.isNotBlank()}} تعذر تحديثها. بقي المحفوظ متاحًا.")
    }finally{worldBusy.value=false}}}
    fun downloadWorldModel(language:String,wifi:Boolean) {
        if(language in worldDownloads.value)return
        worldDownloads.update{it+language}
        task{try{worldLocal.download(language,wifi);require(worldLocal.ready(language)){"انتهى التنزيل لكن النموذج لم يجهز؛ أعد فحص اتصال Google Play وتخزين الجهاز"};worldModelStatus.update{it+(language to "جاهز محليًا")};settings.set("worldAutoLocal","true");configVersion.value++;notices.emit("اكتمل تنزيل نموذج الترجمة؛ لا رسوم لكل عملية محلية")}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){worldModelStatus.update{it+(language to safeError(e))};notices.emit(safeError(e))}
            finally{worldDownloads.update{it-language}}}
    }
    fun checkWorldModel(language:String)=task {worldModelStatus.update{it+(language to if(worldLocal.ready(language))"جاهز محليًا" else "يحتاج تنزيلًا")}}
    val topics = TopicRepository(db)
    val googleTrends = GoogleTrendsDataSource(dao,newsClient)
    val social = SharedPostRepository(db)
    val topicAi = TopicAnalysisService(db,ai,nano)
    val sceneTopics=dao.sceneTopics().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val trendSignals=dao.trendSignals().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneLinks=dao.topicArticles().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val socialAccounts=dao.socialAccounts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val socialPosts=dao.socialPosts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val topicAnalyses=dao.topicAnalyses().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneClaims=dao.claims().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneEvidence=dao.claimEvidence().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneNotes=dao.topicNotes().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneTimeline=dao.topicTimeline().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneFetch=dao.sceneFetchStates().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sceneRefreshing=MutableStateFlow(false)
    val pendingSharedPost=MutableStateFlow<SharedPostDraft?>(null)
    fun acceptShare(value:String) {
        val parsed=SharedPostParser.post(value)
        if(parsed==null){task{notices.emit("لم تتضمن المشاركة رابط منشور X صالحًا")};return}
        val remainder=value.take(20000).replace(Regex("https://\\S+"),"").trim()
        pendingSharedPost.value=SharedPostDraft(parsed.first,parsed.second,remainder)
    }
    fun initializeScene()=task {
        if(settings.get("sceneLegacyXImported")!="true") { social.importLegacyAccounts();settings.set("sceneLegacyXImported","true") }
        if(dao.sceneFetchState("local-clustering")==null)topics.rebuild()
    }
    fun refreshScene(region:String,force:Boolean=false) {
        if(sceneRefreshing.value)return
        task { sceneRefreshing.value=true
            try {
                val regions=if(region=="WORLD")listOf("US","GB","AE")else listOf("SA")
                val states=regions.map{googleTrends.refresh(it,force)}
                topics.rebuild()
                notices.emit(if(states.any{it.error.isNotBlank()})"تم تحديث التجميع المحلي؛ تعذر فحص بعض اتجاهات البحث. راجع حالة المصادر." else "اكتمل فحص إشارات البحث والتغطية المحلية")
            } finally { sceneRefreshing.value=false }
        }
    }
    fun rebuildScene() { if(sceneRefreshing.value)return; task {sceneRefreshing.value=true;try{topics.rebuild()}finally{sceneRefreshing.value=false}} }
    fun runTopicAnalysis(topic:String,op:String,provider:String,question:String,posts:Boolean,notes:Boolean,web:Boolean,chat:Boolean=false,claimId:String?=null) {
        if(busy.value)return
        task { busy.value=true
            try {
                val answer=topicAi.run(topic,op,provider,question,posts,notes,web,chat)
                if(op=="استخراج الادعاءات") {
                    try { val prior=dao.allClaims().associateBy{it.id};FactCheckCoordinator.parseClaims(answer.result,topic).forEach{if(it.id !in prior)dao.saveClaim(it)} }
                    catch(_:Exception){notices.emit("حُفظت الإجابة، لكن تنسيق الادعاءات لم يكن صالحًا. أضفها يدويًا أو راجع النتيجة؛ لم يُكرر طلب API.")}
                }
                if(claimId!=null && op=="فحص الادعاءات")dao.allClaims().firstOrNull{it.id==claimId && it.topicId==topic}?.let { claim ->
                    dao.saveClaim(claim.copy(status=FactCheckCoordinator.status(answer),analysisId=answer.id))
                    answer.sources.lines().filter{ReaderNavigation.allowed(it)}.distinct().forEach{dao.saveClaimEvidence(ClaimEvidence(id=AiPolicy.hash("$claimId|$it"),claimId=claimId,url=it))}
                }
                result.value=answer
            } finally {busy.value=false}
        }
    }
    val models = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val nanoStatus = MutableStateFlow("لم يُفحص بعد؛ يتطلب جهازًا يدعم ML Kit GenAI وAICore")
    private val noteMutex = Mutex()
    init { task { if (settings.get("sourcesSeeded") != "true") { news.seed(); settings.set("sourcesSeeded", "true") } } }
    fun task(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { notices.emit(safeError(e)) }
    }
    fun refresh() { if (busy.value) return; task { busy.value = true; try { notices.emit(news.refresh());topics.rebuild() } finally { busy.value = false } } }
    fun test(provider: String) {
        if (busy.value) return
        task {
            busy.value = true
            settings.set("testState_$provider", "testing")
            try {
                val available = ai.provider(provider).models()
                models.value = models.value + (provider to available)
                settings.set("models_$provider", available.joinToString("\n"))
                settings.set("tested_$provider", System.currentTimeMillis().toString())
                settings.set("testState_$provider", "connected")
                notices.emit("نجح الوصول إلى قائمة النماذج؛ اختر نموذج نصوص. اختبار إنشاء الإجابة يتم عند أول تلخيص.")
            } catch (e: Exception) {
                settings.set("testState_$provider", "disconnected")
                throw e
            } finally { busy.value = false }
        }
    }
    fun analyze(items: List<Article>, op: String, provider: String, question: String, chat: String? = null, personal: List<Note> = emptyList(), web: Boolean = false) {
        if (busy.value) return
        task {
            busy.value = true
            try {
                val complete = items.map { requested ->
                    news.loadDetails(requested.id)
                    dao.article(requested.id) ?: error("لم يعد الخبر موجودًا")
                }
                result.value = ai.analyze(complete, op, provider, question, chat, personal, web)
            }
            finally { busy.value = false }
        }
    }
    suspend fun persistNote(note:Note) { noteMutex.withLock { dao.saveNote(note.copy(updated=System.currentTimeMillis())) } }
    fun saveNote(note: Note) = task { persistNote(note) }
    fun deleteNote(id: String) = task { noteMutex.withLock { dao.deleteNote(id) } }
    fun saveAnswer(analysis: Analysis) = task {
        val note=Note(title=analysis.operation,body=analysis.result,articleIds=analysis.articleIds,analysisId=analysis.id,links=analysis.sources,tags="ذكاء اصطناعي")
        noteMutex.withLock { dao.saveNote(note) }
        dao.allTopicAnalyses().filter{it.analysisId==analysis.id}.forEach{dao.saveTopicNote(TopicNote(it.topicId,note.id))}
    }
    fun toggleSaved(article: Article) = task { dao.setSaved(article.id, !article.saved) }
    fun checkNano() = task { nanoStatus.value = try { nano.status() } catch (_: Exception) { "غير متاح؛ راجع دعم جهازك وإصدار AICore. لا يوجد انتقال سحابي تلقائي." } }
    fun downloadNano() = task {
        nanoStatus.value = "جارٍ التحضير للتنزيل…"
        try { nano.download { nanoStatus.value = it }; nanoStatus.value = nano.status() }
        catch (_: Exception) { nanoStatus.value = "تعذر التنزيل. قد لا يدعم الجهاز المحرك المحلي." }
    }
    fun nanoSummary(article: Article) {
        if (busy.value) return
        task {
            busy.value = true
            try {
                news.loadDetails(article.id)
                val current = dao.article(article.id) ?: error("الخبر غير متاح")
                dao.worldArticle(current.id)?.let{meta->require(dao.worldPublisher(meta.publisherId)?.let{WorldRights.local(it,meta)}==true){"المصدر لا يسمح بالمعالجة المحلية المسجلة"}}
                AiArticleContext.validate(listOf(current))
                val text = nano.generate(AiRequest("local-summary", "local-summary-only", current.title + "\n" + current.fullContent)).text
                val a = Analysis(articleIds = article.id, operation = "تلخيص محلي تجريبي", provider = "Gemini Nano", model = "ML Kit beta1",
                    result = text, sources = article.url, status = "اختبار تجريبي — العربية غير مدعومة رسميًا", cacheKey = AiPolicy.hash(article.availableText + "nano"), cost = 0.0)
                dao.saveAnalysis(a); result.value = a
            } catch (_: Exception) { notices.emit("تعذر التلخيص المحلي. قد يكون الجهاز أو النص العربي غير مدعوم. لم يُرسل النص إلى السحابة.") }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { db.close(); super.onCleared() }
}

