package com.example.healthconnectbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_CLIENT_ID = "drive_client_id"
private const val KEY_CLIENT_SECRET = "drive_client_secret"
private const val KEY_REFRESH_TOKEN = "drive_refresh_token"

class SleepDataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val fetcher = HealthConnectFetcher(applicationContext)
            val sleepData = fetcher.fetchSleepData()

            // ローカルにも保存しておく（デバッグ・バックアップ用）
            val externalDir = applicationContext.getExternalFilesDir(null)
            val sleepFile = File(externalDir, "sleep_data.json")
            sleepFile.writeText(sleepData)

            val internalFile = File(applicationContext.filesDir, "sleep_data_backup.json")
            internalFile.writeText(sleepData)

            // Google Drive にアップロード（認証情報が保存されていれば）
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val clientId = prefs.getString(KEY_CLIENT_ID, "") ?: ""
            val clientSecret = prefs.getString(KEY_CLIENT_SECRET, "") ?: ""
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""

            if (clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val uploader = DriveUploader(clientId, clientSecret, refreshToken)
                val result = uploader.uploadSleepData("sleep_data_$timestamp.json", sleepData)
                if (result.isFailure) {
                    return Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
