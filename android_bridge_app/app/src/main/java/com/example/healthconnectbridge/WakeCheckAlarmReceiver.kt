package com.example.healthconnectbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_LAST_ALARM_FIRED_AT = "last_alarm_fired_at_epoch_millis"
private const val KEY_ALARM_CHAIN_ENABLED = "alarm_chain_enabled"

/**
 * AlarmManagerからの発火を受け取るたびに、
 * ① 次の15分後のアラームを即座に再予約し（チェーンを絶やさない）、
 * ② 実際の起床チェック（WakeDetectionWorkerと同じロジック）をWorkManager経由で1回実行する。
 *
 * BroadcastReceiver#onReceiveは長時間処理してはいけないため、実処理はWorkManagerに委譲する。
 */
class WakeCheckAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ALARM_FIRED_AT, System.currentTimeMillis()).apply()

        // チェーンが有効な間だけ次を予約し続ける（「起床検知を停止」相当の操作がなければ継続）。
        if (prefs.getBoolean(KEY_ALARM_CHAIN_ENABLED, false)) {
            WakeCheckAlarmScheduler.scheduleNext(context)
        }

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<WakeDetectionWorker>().build()
        )
    }
}
