package com.nabd.news

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real phone test requires a real permitted full article installed through the app and downloaded models.
 * No synthetic source, bundled article, automatic model download, or cloud AI request is substituted.
 */
class WorldTranslationDeviceTest {
    @Test fun realPermittedFullArticleUsesActualOnDeviceTranslationAndOfflineCache()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("worldLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=NabdDatabase.open(context);val report=linkedMapOf<String,Any?>()
        try {
            val pubs=db.dao().allWorldPublishers().associateBy{it.id};val news=db.dao().allArticles().associateBy{it.id}
            val meta=db.dao().allWorldArticles().firstOrNull{m->pubs[m.publisherId]?.let{WorldRights.local(it,m)}==true&&news[m.articleId]?.let{!it.isExcerpt&&it.fullContent.isNotBlank()}==true}
                ?:error("فعّل مصدرًا حقيقيًا مسموحًا وافتح مقالًا كاملًا أولًا؛ لا يوجد بديل تجريبي")
            val article=news[meta.articleId]!!;val local=MlKitWorldTranslator()
            report["url"]=article.url;report["publisher"]=pubs[meta.publisherId]?.name;report["language"]=meta.language;report["originalCharacters"]=article.fullContent.length
            require(local.ready(meta.language)){"نزّل نموذج اللغة والعربية من إعدادات نبض العالم أولًا"}
            val parts=WorldTextParts.article(article).filter{it.original.isNotBlank()};require(parts.size<=400){"اختر مقالًا أقصر للاختبار المرجعي"}
            val engine=WorldTranslationEngine(db.dao(),local)
            val results=parts.map{engine.translate(article.id,it.slot,it.original)}
            assertTrue(results.all{it.state=="ready"})
            assertTrue(results.joinToString(""){it.translated}.count{it in '\u0600'..'\u06ff'}>20)
            val count=db.dao().allWorldTranslations().size
            parts.forEach{engine.translate(article.id,it.slot,it.original)}
            assertEquals("Second open must reuse translations",count,db.dao().allWorldTranslations().size)
            report["translationParts"]=results.size;report["translatedCharacters"]=results.sumOf{it.translated.length};report["cacheReuse"]=true;report["automatedNumericAndQuoteGuards"]=true;report["humanEditorialQualityReview"]= "REQUIRED";report["success"]=true
        }catch(e:Throwable){report["success"]=false;report["reason"]=safeError(e);throw e}
        finally {
            java.io.File(context.getExternalFilesDir(null),"nabd-world-device-report.json").writeText(com.google.gson.Gson().toJson(report));db.close()
        }
    }
}
