package com.example.healthconnectbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_ROUTINE_URL = "routine_trigger_url"
private const val KEY_ROUTINE_TOKEN = "routine_trigger_token"
private const val KEY_LAST_WAKE_END_TIME = "last_wake_end_time_epoch_millis"
private const val KEY_WAKE_BASELINE_SET = "wake_baseline_set"

/**
 * 15分ごとにHealth Connectの睡眠記録を軽くチェックし、
 * 「前回確認した時より新しい睡眠セッション終了（＝起床）」を検知したら
 * Claude Routineの APIトリガーを叩いて即座に通知セッションを起動する。
 *
 * Health Connectのデータ自体は端末内で完結しているため、このチェックは
 * クラウドAPIを一切呼ばず、Health Connect未対応時と同じくバッテリー消費も軽微。
 * Claude側にリクエストが飛ぶのは実際に起床を検知した時の1回だけなので、
 * 頻繁なチェック間隔にしてもトークン消費は増えない。
 */
class WakeDetectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val latestEndTime = HealthConnectFetcher(applicationContext).fetchLatestSleepEndTime()
                ?: return Result.success() // 睡眠記録がまだない。次回また確認。

            val latestMillis = latestEndTime.toEpochMilli()
            val lastNotifiedMillis = prefs.getLong(KEY_LAST_WAKE_END_TIME, -1L)
            val baselineSet = prefs.getBoolean(KEY_WAKE_BASELINE_SET, false)

            // 導入直後の初回チェックでは、既存の睡眠記録で誤って通知が飛ばないよう
            // 「現時点の最新記録」を基準値として保存するだけにする。
            if (!baselineSet) {
                prefs.edit()
                    .putLong(KEY_LAST_WAKE_END_TIME, latestMillis)
                    .putBoolean(KEY_WAKE_BASELINE_SET, true)
                    .apply()
                return Result.success()
            }

            if (latestMillis > lastNotifiedMillis) {
                val routineUrl = prefs.getString(KEY_ROUTINE_URL, "") ?: ""
                val routineToken = prefs.getString(KEY_ROUTINE_TOKEN, "") ?: ""

                if (routineUrl.isNotBlank() && routineToken.isNotBlank()) {
                    val wakeTimeJst = latestEndTime.atZone(ZoneId.of("Asia/Tokyo"))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    val text = "起床を検知しました（Health Connect睡眠記録の終了時刻: $wakeTimeJst）。" +
                        "今日の予定とお住まいの地域の天気を確認し、簡潔な「おはようございます」メッセージを通知してください。"

                    val result = RoutineTrigger.fire(routineUrl, routineToken, text)
                    if (result.isFailure) {
                        // 通信失敗時はKEY_LAST_WAKE_END_TIMEを更新せずリトライし、
                        // 次回のワーカー実行で再送を試みる。
                        return Result.retry()
                    }
                }

                prefs.edit().putLong(KEY_LAST_WAKE_END_TIME, latestMillis).apply()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
