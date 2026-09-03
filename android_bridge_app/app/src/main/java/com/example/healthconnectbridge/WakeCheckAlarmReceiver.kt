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
 *
 * 【重要】ここで enqueue するジョブに Constraints（NetworkType.CONNECTED等）を付けてはいけない。
 * 一度「Dozeから叩き起こされた直後はネットワークが復帰しきっていない」問題を制約付きで
 * 解消しようとしたが、制約付きのWorkRequestはOneTimeWorkRequestであってもJobSchedulerの
 * 「都合の良いタイミングまで実行開始を遅らせる」対象になり得ることが判明し、
 * 実際に「アラームは15分おきに発火し続けているのに、実処理が丸4日間一度も完走しない」
 * という、まさにPeriodicWorkRequestをやめた理由と同じ症状が再発した。
 * ネットワーク待ちは WakeDetectionWorker#doWork 側で自前ポーリングする方式に変更済み。
 */
class WakeCheckAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ALARM_FIRED_AT, System.currentTimeMillis()).apply()

        // チェーンが有効な間だけ次を予約し続ける（「起床検知を停止」相当の操作がなければ継続）。
        if (prefs.getBoolean(KEY_ALARM_CHAIN_ENABLED, false)) {
            WakeCheckAlarmScheduler.scheduleNext(context)
        }

        // 制約なし。enqueueした瞬間にOSへ「今すぐ実行してほしい」依頼が行くだけの、
        // 一番遅延の少ない形にする。
        val request = OneTimeWorkRequestBuilder<WakeDetectionWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
