package com.nabd.news

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SceneRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val settings=SecureSettings(applicationContext)
        if(settings.get("sceneBackground","false")!="true")return Result.success()
        val db=NabdDatabase.open(applicationContext)
        return try {
            val client=Network().client.newBuilder().callTimeout(35,TimeUnit.SECONDS).build()
            val state=GoogleTrendsDataSource(db.dao(),client).refresh("SA")
            TopicRepository(db).rebuild()
            if(state.error.isNotBlank() && state.httpCode in listOf(0,429,500,502,503) && runAttemptCount<2)Result.retry() else Result.success()
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){if(runAttemptCount<2)Result.retry() else Result.failure()}
        finally{db.close()}
    }
}
object SceneScheduler {
    fun set(context:Context,enabled:Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled){manager.cancelUniqueWork("nabd-scene-observer");return}
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()
        val request=PeriodicWorkRequestBuilder<SceneRefreshWorker>(6,TimeUnit.HOURS).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork("nabd-scene-observer",ExistingPeriodicWorkPolicy.KEEP,request)
    }
}

