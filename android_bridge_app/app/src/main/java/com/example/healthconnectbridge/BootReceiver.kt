package com.example.healthconnectbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_ALARM_CHAIN_ENABLED = "alarm_chain_enabled"

/**
 * AlarmManagerの単発アラーム（setExactAndAllowWhileIdle）はWorkManagerの定期ジョブと違い、
 * 端末再起動で消える。起床検知が有効なままだった場合、再起動後に自分でチェーンを再開する。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ALARM_CHAIN_ENABLED, false)) {
            WakeCheckAlarmScheduler.scheduleNext(context)
        }
    }
}
