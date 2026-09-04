package com.example.healthconnectbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_LAST_ALARM_FIRED_AT = "last_alarm_fired_at_epoch_millis"
private const val KEY_ALARM_CHAIN_ENABLED = "alarm_chain_enabled"
private const val ENQUEUE_CONFIRM_TIMEOUT_MILLIS = 5_000L

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
 *
 * 【重要】さらにその後、制約を外してもなお「ジョブ（doWork）が一度も実行されていない」
 * ことが診断画面から判明した。原因は、WorkManager.enqueue()がジョブを内部データベースへ
 * 永続化する処理自体が非同期であるため、onReceiveが戻った直後にOSがこのプロセスを
 * 終了させてしまうと、永続化が完了する前にジョブそのものが消えてしまうこと。
 * goAsync()でPendingResultを保持し、enqueue()のOperationが完了する（永続化された）
 * ことを確認してからプロセスを解放するようにする。
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
        val operation = WorkManager.getInstance(context).enqueue(request)

        // enqueue()がジョブを永続化し終える（＝WorkManagerが実際に実行対象として
        // 認識する）まで、プロセスを生かしておくためのgoAsync()。
        val pendingResult = goAsync()
        var finished = false
        lateinit var observer: Observer<Operation.State>
        observer = Observer { state ->
            if (!finished && (state is Operation.State.SUCCESS || state is Operation.State.FAILURE)) {
                finished = true
                operation.state.removeObserver(observer)
                pendingResult.finish()
            }
        }
        operation.state.observeForever(observer)

        // 万一Operationの状態変化通知が来なかった場合の保険。ここで待ちすぎると
        // BroadcastReceiverとして不健全なので、短いタイムアウトで必ず解放する。
        Handler(Looper.getMainLooper()).postDelayed({
            if (!finished) {
                finished = true
                operation.state.removeObserver(observer)
                pendingResult.finish()
            }
        }, ENQUEUE_CONFIRM_TIMEOUT_MILLIS)
    }
}
