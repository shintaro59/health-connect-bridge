package com.example.healthconnectbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
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
 *
 * Dozeから叩き起こされた直後はまだネットワークが復帰しきっていないことがあり、
 * 制約無しだと「即座に試みて通信エラーで失敗、そのまま記録もされない」ということが
 * 実際に起きたため、ネットワークが使えるようになるまでWorkManager側で待たせる制約を付ける。
 */
class WakeCheckAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ALARM_FIRED_AT, System.currentTimeMillis()).apply()

        // チェーンが有効な間だけ次を予約し続ける（「起床検知を停止」相当の操作がなければ継続）。
        if (prefs.getBoolean(KEY_ALARM_CHAIN_ENABLED, false)) {
            WakeCheckAlarmScheduler.scheduleNext(context)
        }

        val request = OneTimeWorkRequestBuilder<WakeDetectionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
