package com.nabd.news

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NabdDatabase::class.java)
    @Test fun versionOneUpgradePreservesIdsBookmarksNotesAndAnalysis() {
        val name = "article-migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO articles (id,title,body,url,source,category,published,saved,kind,image) VALUES ('stable-id','عنوان','مقتطف قديم','https://publisher.test/one','مصدر','عام',100,1,'خبر','')")
            execSQL("INSERT INTO notes (id,title,body,tags,category,articleIds,analysisId,links,created,updated,pinned) VALUES ('note-id','تدوينة','نص خاص','','عام','stable-id','analysis-id','',1,2,1)")
            execSQL("INSERT INTO analyses (id,articleIds,operation,provider,model,result,sources,status,cacheKey,created) VALUES ('analysis-id','stable-id','تلخيص','Mock','mock','نتيجة قديمة','','أدلة غير كافية','cache-key',1)")
            execSQL("INSERT INTO sources (id,name,url,category,type) VALUES ('source-id','مصدر','https://publisher.test/feed','عام','RSS')")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, NabdDatabase.MIGRATION_1_2, NabdDatabase.MIGRATION_2_3, NabdDatabase.MIGRATION_3_4, NabdDatabase.MIGRATION_4_5, NabdDatabase.MIGRATION_5_6, NabdDatabase.MIGRATION_6_7, NabdDatabase.MIGRATION_7_8).apply {
            query("SELECT id,summary,fullContent,contentOrigin,saved FROM articles").use {
                assertTrue(it.moveToFirst()); assertEquals("stable-id", it.getString(0)); assertEquals("مقتطف قديم", it.getString(1))
                assertEquals("", it.getString(2)); assertEquals("excerpt", it.getString(3)); assertEquals(1, it.getInt(4))
            }
            query("SELECT articleIds,analysisId,body FROM notes WHERE id='note-id'").use {
                assertTrue(it.moveToFirst()); assertEquals("stable-id", it.getString(0)); assertEquals("analysis-id", it.getString(1)); assertEquals("نص خاص", it.getString(2))
            }
            query("SELECT result FROM analyses WHERE id='analysis-id'").use { assertTrue(it.moveToFirst()); assertEquals("نتيجة قديمة", it.getString(0)) }
            query("SELECT htmlAllowed,termsUrl FROM sources").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("", it.getString(1)) }
            close()
        }
    }
}
