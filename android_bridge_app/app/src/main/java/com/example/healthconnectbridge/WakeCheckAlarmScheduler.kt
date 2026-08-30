package com.example.healthconnectbridge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

private const val ALARM_REQUEST_CODE = 4201
private const val INTERVAL_MILLIS = 15 * 60 * 1000L

/**
 * WorkManagerのPeriodicWorkRequestは「いつか、システムの都合が良い時に」実行される
 * という設計で、Doze/アプリスタンバイバケットの対象だと丸1日以上まったく実行されない
 * ことがある（実際に2日連続で実行回数0だった）。
 *
 * AlarmManager.setExactAndAllowWhileIdle() はDozeを貫通して指定時刻に確実に発火する
 * よう設計された仕組みなので、こちらを使い、発火のたびに次の15分後を自分で再予約する
 * チェーン方式にする。
 */
object WakeCheckAlarmScheduler {

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WakeCheckAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + INTERVAL_MILLIS
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        } catch (e: SecurityException) {
            // 正確なアラームの権限が無い場合。呼び出し側で権限確認を促す。
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}
