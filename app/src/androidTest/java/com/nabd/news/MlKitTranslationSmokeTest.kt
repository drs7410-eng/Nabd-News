package com.nabd.news

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Real ML Kit + Room, using technical QA text, NOT a publisher article or proof of editorial quality. */
class MlKitTranslationSmokeTest {
    @Test fun actualArabicModelTranslatesProtectedTextAndReusesRoomCache()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".qa")){"Use isolated -PqaBuild=true"}
        check(InstrumentationRegistry.getArguments().getString("modelDownloadConsent")=="true"){
            "Explicit model download consent required: -Pandroid.testInstrumentationRunnerArguments.modelDownloadConsent=true"
        }
        val local=MlKitWorldTranslator()
        withTimeout(180000){local.download("en",false)}
        assertTrue("Actual English/Arabic model unavailable",local.ready("en"))
        val plain=withTimeout(60000){local.translate("This is a technical software test.","en")}
        assertTrue("Actual inference did not produce Arabic",plain.count{it in '\u0600'..'\u06ff'}>=10)
        val db=Room.inMemoryDatabaseBuilder(context,NabdDatabase::class.java).build()
        try {
            val text="A software test contains the number 25 and the date 2026-10-26."
            val a=Article(id="mlkit-smoke",title="Technical QA text",body=text,fullContent=text,url="https://publisher.test/qa",source="QA ONLY",contentOrigin="manual")
            db.dao().saveWorldPublisher(WorldPublisher("qa-sdk","QA ONLY","اختبار تقني","https://publisher.test/","اختبار","en",permission="recorded",termsUrl="https://publisher.test/terms",allowCache=true,allowLocal=true))
            db.dao().addArticles(listOf(a));db.dao().saveWorldArticle(WorldArticle(a.id,"qa-sdk","qa","en"))
            db.dao().saveWorldGlossary(WorldGlossary(original="software",arabic="برمجيات"))
            val result=withTimeout(90000){WorldTranslationEngine(db.dao(),local).translate(a.id,"block-0",text)}
            assertEquals("ready",result.state);assertNotEquals(text,result.translated)
            assertTrue(result.translated.count{it in '\u0600'..'\u06ff'}>=8)
            assertTrue(result.translated.contains("برمجيات"));EditorialProtection.validate(text,result.translated)
            val unavailable=object:WorldLocalTranslator by local {
                override suspend fun translate(text:String,language:String):String=error("Inference must not run on cached reopen")
            }
            val cached=WorldTranslationEngine(db.dao(),unavailable).translate(a.id,"block-0",text)
            assertEquals(result.id,cached.id);assertEquals(result.translated,cached.translated)
        }finally{db.close()}
    }
}
