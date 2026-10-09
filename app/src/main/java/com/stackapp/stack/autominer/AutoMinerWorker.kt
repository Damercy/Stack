package com.stackapp.stack.autominer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stackapp.stack.tap.RoomTapStore
import java.util.concurrent.TimeUnit

class AutoMinerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = RoomTapStore(applicationContext)
        store.applyAutoMiner(System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "stack_auto_miner"

        fun sync(context: Context, active: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!active) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<AutoMinerWorker>(
                AUTO_MINER_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
