package com.nabd.news

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/** Executes the actual Android Migration SQL against SQLite and compares it with both Room schemas. */
class ArticleMigrationSqlTest {
    private fun create(db: Connection, version: Int) {
        val schema = JsonParser.parseString(File("schemas/com.nabd.news.NabdDatabase/$version.json").readText()).asJsonObject.getAsJsonObject("database")
        schema.getAsJsonArray("entities").forEach { element ->
            val entity = element.asJsonObject; val name = entity.get("tableName").asString
            db.createStatement().use { it.execute(entity.get("createSql").asString.replace("\${TABLE_NAME}", name)) }
            entity.getAsJsonArray("indices")?.forEach { index -> db.createStatement().use { it.execute(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", name)) } }
        }
    }
    private fun columnSchema(db: Connection, table: String): List<List<String?>> = db.createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info(`$table`)").use { result -> buildList {
            while (result.next()) add(listOf(result.getString("name"), result.getString("type"), result.getString("notnull"), result.getString("dflt_value"), result.getString("pk")))
        } }.sortedBy { it.first() }
    }
    @Test fun actualMigrationPreservesContentAndMatchesRoomVersionTwo() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old, 1)
            old.createStatement().use {
                it.execute("INSERT INTO articles VALUES ('id','عنوان','مقتطف قديم','https://publisher.test/a','مصدر','عام',100,1,'خبر','')")
                it.execute("INSERT INTO sources VALUES ('source-id','مصدر','https://publisher.test/feed','عام','RSS')")
                it.execute("INSERT INTO notes VALUES ('note-id','عنوان','ملاحظتي الخاصة','tag','عام','id','analysis-id','',1,2,1)")
                it.execute("INSERT INTO analyses (id,articleIds,operation,provider,model,result,sources,status,cacheKey,created) VALUES ('analysis-id','id','تلخيص','Mock','model','نتيجة سابقة','','غير كاف','cache',1)")
                it.execute("INSERT INTO conversations VALUES ('chat','id','عنوان',1)")
                it.execute("INSERT INTO messages VALUES ('msg','chat','user','سؤال','','',1)")
                it.execute("INSERT INTO attachments VALUES ('attachment','note-id','content://local/document','text/plain')")
                it.execute("INSERT INTO usage (id,provider,model,created,state) VALUES ('usage','Mock','model',1,'completed')")
            }
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                if (method.name == "execSQL") { old.createStatement().use { it.execute(args!![0] as String) }; null }
                else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_1_2.migrate(proxy)
            old.createStatement().use { statement ->
                statement.executeQuery("SELECT id,summary,fullContent,contentOrigin,saved,sourceId FROM articles").use {
                    assertTrue(it.next()); assertEquals("id", it.getString(1)); assertEquals("مقتطف قديم", it.getString(2))
                    assertEquals("", it.getString(3)); assertEquals("excerpt", it.getString(4)); assertEquals(1, it.getInt(5)); assertEquals("source-id", it.getString(6))
                }
                statement.executeQuery("SELECT body,articleIds,analysisId FROM notes").use { assertTrue(it.next()); assertEquals("ملاحظتي الخاصة", it.getString(1)); assertEquals("id", it.getString(2)); assertEquals("analysis-id", it.getString(3)) }
            }
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh, 2)
                listOf("articles", "sources", "notes", "analyses", "conversations", "messages", "attachments", "usage").forEach { table ->
                    assertEquals("Migrated $table must match Room schema", columnSchema(fresh, table), columnSchema(old, table))
                    old.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM $table").use { r -> r.next(); assertEquals("Must preserve $table rows", 1, r.getInt(1)) } }
                }
            }
        }
    }
    @Test fun versionTwoUpgradePreservesArticleAndAddsPersistentQualityMatchingRoomThree() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old, 2)
            old.createStatement().use { it.execute("INSERT INTO articles (id,title,body,url,source,category,published,saved,kind,image,fullContent,contentOrigin) VALUES ('id','عنوان','مقتطف','https://publisher.test/a','مصدر','عام',100,1,'خبر','','مقال كامل محفوظ','article_page')") }
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                if (method.name == "execSQL") { old.createStatement().use { it.execute(args!![0] as String) }; null }
                else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_2_3.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh, 3)
                listOf("articles", "sources", "notes", "analyses", "conversations", "messages", "attachments", "usage", "feed_quality").forEach { table ->
                    assertEquals(columnSchema(fresh, table), columnSchema(old, table))
                }
            }
            old.createStatement().use { statement ->
                statement.executeQuery("SELECT fullContent,contentOrigin,saved FROM articles").use { r ->
                    assertTrue(r.next()); assertEquals("مقال كامل محفوظ", r.getString(1)); assertEquals("article_page", r.getString(2)); assertEquals(1, r.getInt(3))
                }
                statement.execute("INSERT INTO feed_quality VALUES ('source','https://publisher.test/feed',1000,'ok',5,2,1,2,3,900,200,'')")
                statement.executeQuery("SELECT imageCount,latestPublished FROM feed_quality").use { r -> assertTrue(r.next()); assertEquals(3, r.getInt(1)); assertEquals(900, r.getInt(2)) }
            }
        }
    }

    @Test fun upgradeThreeToFourMatchesRoomSchemaAndPreservesBbcBody() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old, 3)
            old.createStatement().use { it.execute("INSERT INTO articles (id,title,body,url,source,category,published,saved,kind,image,fullContent) VALUES ('id','عنوان','','https://www.bbc.com/arabic/articles/c6ly0gdxd5jlo','BBC','عام',100,1,'خبر','','نص محفوظ')") }
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                if (method.name == "execSQL") { old.createStatement().use { it.execute(args!![0] as String) }; null }
                else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_3_4.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh, 4)
                listOf("articles", "sources", "notes", "analyses", "conversations", "messages", "attachments", "usage", "feed_quality").forEach { assertEquals(columnSchema(fresh, it), columnSchema(old, it)) }
            }
            old.createStatement().use { s -> s.executeQuery("SELECT fullContent,detailReason,extractorId,saved FROM articles").use { r ->
                assertTrue(r.next()); assertEquals("نص محفوظ", r.getString(1)); assertEquals("", r.getString(2)); assertEquals("", r.getString(3)); assertEquals(1, r.getInt(4))
            } }
        }
    }

    @Test fun upgradeFourToFivePreservesNewsAndMatchesMediaSchema() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old, 4)
            old.createStatement().use { it.execute("INSERT INTO articles (id,title,body,url,source,category,published,saved,kind,image) VALUES ('kept','عنوان','','https://publisher.test/a','مصدر','عام',100,1,'خبر','')") }
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                if (method.name == "execSQL") { old.createStatement().use { it.execute(args!![0] as String) }; null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_4_5.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh,5)
                listOf("articles","notes","sources","media_clips").forEach { assertEquals(columnSchema(fresh,it),columnSchema(old,it)) }
            }
            old.createStatement().use { s -> s.executeQuery("SELECT saved FROM articles WHERE id='kept'").use { assertTrue(it.next());assertEquals(1,it.getInt(1)) } }
        }
    }

    @Test fun upgradeFiveToSixMatchesRoomWithoutLosingExistingClips() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old,5)
            old.createStatement().use { it.execute("INSERT INTO media_clips VALUES ('id','title','https://youtu.be/abcdefghijk','https://www.youtube.com/embed/abcdefghijk','YouTube','s','source','تقنية',100,'',1,0,200)") }
            val proxy=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL") { old.createStatement().use{it.execute(args!![0] as String)};null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_5_6.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh -> create(fresh,6); assertEquals(columnSchema(fresh,"media_clips"),columnSchema(old,"media_clips")) }
            old.createStatement().use { s -> s.executeQuery("SELECT liked,opened,favorite,visibleMillis FROM media_clips").use { assertTrue(it.next());assertEquals(1,it.getInt(1));assertEquals(200L,it.getLong(2));assertEquals(0,it.getInt(3));assertEquals(0L,it.getLong(4)) } }
        }
    }

    @Test fun upgradeSixToSevenMatchesEveryRoomTableAndPreservesExistingRows() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old,6)
            old.createStatement().use{it.execute("INSERT INTO notes VALUES ('kept','عنوان','نص شخصي','','عام','','','',1,2,1)")}
            val proxy=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL") { old.createStatement().use{it.execute(args!![0] as String)};null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_6_7.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh,7)
                val schema=JsonParser.parseString(File("schemas/com.nabd.news.NabdDatabase/7.json").readText()).asJsonObject.getAsJsonObject("database")
                schema.getAsJsonArray("entities").forEach{e->val table=e.asJsonObject.get("tableName").asString;assertEquals(table,columnSchema(fresh,table),columnSchema(old,table))}
            }
            old.createStatement().use{s->s.executeQuery("SELECT body,pinned FROM notes WHERE id='kept'").use{assertTrue(it.next());assertEquals("نص شخصي",it.getString(1));assertEquals(1,it.getInt(2))}}
        }
    }
    @Test fun upgradeSevenToEightMatchesRoomAndPreservesSceneAndNotebook() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { old ->
            create(old,7)
            old.createStatement().use{it.execute("INSERT INTO scene_topics VALUES ('topic','عنوان','وصف','SA','manual',1,2,1)");it.execute("INSERT INTO notes VALUES ('note','عنوان','نصي الخاص','','عام','','','',1,2,1)")}
            val proxy=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL") { old.createStatement().use{it.execute(args!![0] as String)};null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            NabdDatabase.MIGRATION_7_8.migrate(proxy)
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(fresh,8)
                val schema=JsonParser.parseString(File("schemas/com.nabd.news.NabdDatabase/8.json").readText()).asJsonObject.getAsJsonObject("database")
                schema.getAsJsonArray("entities").forEach{e->val table=e.asJsonObject.get("tableName").asString;assertEquals(table,columnSchema(fresh,table),columnSchema(old,table))}
            }
            old.createStatement().use{s->s.executeQuery("SELECT body FROM notes WHERE id='note'").use{assertTrue(it.next());assertEquals("نصي الخاص",it.getString(1))};s.executeQuery("SELECT saved FROM scene_topics WHERE id='topic'").use{assertTrue(it.next());assertEquals(1,it.getInt(1))}}
        }
    }
}
