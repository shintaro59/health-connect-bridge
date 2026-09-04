package com.example.healthconnectbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_ROUTINE_URL = "routine_trigger_url"
private const val KEY_ROUTINE_TOKEN = "routine_trigger_token"
private const val KEY_LAST_WAKE_END_TIME = "last_wake_end_time_epoch_millis"
private const val KEY_WAKE_BASELINE_SET = "wake_baseline_set"
private const val KEY_LAST_WORKER_STARTED_AT = "last_worker_started_at_epoch_millis"
private const val KEY_LAST_ERROR = "last_worker_error"
private const val KEY_LAST_ERROR_AT = "last_worker_error_at_epoch_millis"
private const val NETWORK_WAIT_TIMEOUT_MILLIS = 15_000L
private const val NETWORK_WAIT_POLL_INTERVAL_MILLIS = 1_000L

/**
 * Dozeから叩き起こされた直後はまだネットワークが復帰しきっていないことがある。
 * WorkManagerのConstraints（NetworkType.CONNECTED）で待たせる方式は、制約付きジョブが
 * JobSchedulerの都合でDoze中は実行開始自体を延々遅らされてしまう問題があったため、
 * ここで自前に短時間（最大15秒）ポーリングする。見つからなければ諦めてそのまま進み、
 * 実際の通信（RoutineTrigger.fire）が失敗すればResult.retry()に任せる。
 */
private suspend fun waitForNetwork(context: Context) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
    var waitedMillis = 0L
    while (waitedMillis < NETWORK_WAIT_TIMEOUT_MILLIS) {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (isConnected) return
        delay(NETWORK_WAIT_POLL_INTERVAL_MILLIS)
        waitedMillis += NETWORK_WAIT_POLL_INTERVAL_MILLIS
    }
}

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
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // doWork自体が本当に呼ばれているか（＝ジョブの実行開始そのもの）を、
        // 例外の有無に関わらず必ず記録する。これが更新されないなら、WorkManagerが
        // ジョブを実行すらしていないということなので、原因の切り分けに使う。
        prefs.edit().putLong(KEY_LAST_WORKER_STARTED_AT, System.currentTimeMillis()).apply()

        return try {
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
                    waitForNetwork(applicationContext)

                    val wakeTimeJst = latestEndTime.atZone(ZoneId.of("Asia/Tokyo"))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    // PCは自宅固定でも本人（スマホ）は移動するため、天気確認用に
                    // 起床時点の現在地をベストエフォートで添える（取れない場合は省略）。
                    val location = LocationHelper.currentLocation(applicationContext)
                    val locationText = when {
                        location == null ->
                            "現在地: 取得できませんでした（位置情報の権限が未許可の可能性があります。天気は確認せずに進めてください）"
                        location.label != null ->
                            "現在地: ${location.label}（緯度${location.latitude}, 経度${location.longitude}）"
                        else ->
                            "現在地: 緯度${location.latitude}, 経度${location.longitude}"
                    }

                    val text = "起床を検知しました（Health Connect睡眠記録の終了時刻: $wakeTimeJst）。\n" +
                        "$locationText\n" +
                        "今日の予定と、上記の現在地の天気を確認し、簡潔な「おはようございます」メッセージを通知してください。"

                    val result = RoutineTrigger.fire(routineUrl, routineToken, text)
                    if (result.isFailure) {
                        // 通信失敗時はKEY_LAST_WAKE_END_TIMEを更新せずリトライし、
                        // 次回のワーカー実行で再送を試みる。原因が分からないと
                        // 何度失敗しても気付けないため、理由を必ず記録しておく。
                        recordError(prefs, result.exceptionOrNull())
                        return Result.retry()
                    }
                }

                prefs.edit().putLong(KEY_LAST_WAKE_END_TIME, latestMillis).apply()
            }

            Result.success()
        } catch (e: Exception) {
            // ここで握りつぶすと「なぜ最終チェック時刻が更新されないか」を
            // 診断画面から一切追えなくなってしまうため、必ず記録してから続行する。
            recordError(prefs, e)
            Result.retry()
        }
    }

    private fun recordError(prefs: android.content.SharedPreferences, error: Throwable?) {
        val text = if (error == null) "（詳細不明のエラー）" else "${error.javaClass.simpleName}: ${error.message}"
        prefs.edit()
            .putString(KEY_LAST_ERROR, text)
            .putLong(KEY_LAST_ERROR_AT, System.currentTimeMillis())
            .apply()
    }
}
