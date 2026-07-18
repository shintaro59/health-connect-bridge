package com.example.healthconnectbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class SleepDataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val fetcher = HealthConnectFetcher(applicationContext)
            val sleepData = fetcher.fetchSleepData()

            val externalDir = applicationContext.getExternalFilesDir(null)
            val sleepFile = File(externalDir, "sleep_data.json")
            sleepFile.writeText(sleepData)

            val internalFile = File(applicationContext.filesDir, "sleep_data_backup.json")
            internalFile.writeText(sleepData)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
