package com.nabd.news

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class MediaRoomTest {
    private lateinit var db:NabdDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),NabdDatabase::class.java).build()}
    @After fun close(){db.close()}
    @Test fun refreshPreservesFeedbackAndBackupRestoresClips() = runBlocking {
        val clip=MediaClip("id","عنوان","https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube","s","قناة","تقنية",100)
        db.dao().installMediaSource(Source(id="s",name="قناة",url="https://youtube.com/feeds/videos.xml",type="VIDEO"),listOf(clip))
        db.dao().likeClip("id",true);db.dao().hideClip("id",true);db.dao().openClip("id",200)
        db.dao().mergeClips(listOf(clip.copy(title="عنوان محدث")))
        val saved=db.dao().allClips().single();assertTrue(saved.liked);assertTrue(saved.hidden);assertEquals(200L,saved.opened)
        val archive=BackupRepository(db).exportJson()
        db.dao().resetMediaLearning();assertFalse(db.dao().allClips().single().liked)
        BackupRepository(db).restore(archive)
        assertTrue(db.dao().allClips().single().liked);assertEquals("عنوان محدث",db.dao().allClips().single().title)
    }
    @Test fun favoritesSurviveRefreshBackupAndRecommendationReset() = runBlocking {
        val clip=MediaClip("favorite","عنوان","https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube","s","قناة","تقنية",100)
        db.dao().saveClip(clip)
        db.dao().favoriteClip(clip.id,true)
        db.dao().recordClipExposure(clip.id,15000,1)
        db.dao().mergeClips(listOf(clip.copy(title="تحديث")))
        var saved=db.dao().allClips().single()
        assertTrue(saved.favorite);assertEquals(15000L,saved.visibleMillis);assertEquals(1,saved.skips)
        val archive=BackupRepository(db).exportJson()
        db.dao().resetMediaLearning()
        saved=db.dao().allClips().single();assertTrue(saved.favorite);assertEquals(0,saved.impressions)
        db.dao().favoriteClip(clip.id,false)
        BackupRepository(db).restore(archive)
        assertTrue(db.dao().allClips().single().favorite)
    }

    @Test fun favoriteRemainsWhenDatabaseIsClosedAndOpenedAgain() = runBlocking {
        val context:Application=RuntimeEnvironment.getApplication()
        val name="favorite-restart-test.db"
        db.close();context.deleteDatabase(name)
        db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        val clip=MediaClip("disk","عنوان","https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube","s","قناة","تقنية",100)
        db.dao().saveClip(clip);db.dao().favoriteClip(clip.id,true)
        db.close();db=Room.databaseBuilder(context,NabdDatabase::class.java,name).build()
        assertTrue(db.dao().allClips().single().favorite)
        db.close();context.deleteDatabase(name)
        Unit
    }
    @Test fun oldVersionFourBackupGetsSafeDefaultsForNewPreferenceFields() = runBlocking {
        val clip=MediaClip("old","عنوان","https://youtu.be/abcdefghijk","https://www.youtube.com/embed/abcdefghijk","YouTube","s","قناة","تقنية",100)
        db.dao().saveClip(clip)
        val root=com.google.gson.JsonParser.parseString(BackupRepository(db).exportJson()).asJsonObject
        root.addProperty("version",4)
        root.getAsJsonArray("mediaClips").forEach { element -> listOf("favorite","visibleMillis","impressions","skips").forEach { element.asJsonObject.remove(it) } }
        BackupRepository(db).restore(root.toString())
        val restored=db.dao().allClips().single();assertFalse(restored.favorite);assertEquals(0L,restored.visibleMillis)
    }

}
