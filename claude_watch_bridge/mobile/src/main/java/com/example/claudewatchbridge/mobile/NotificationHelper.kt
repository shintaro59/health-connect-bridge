package com.example.claudewatchbridge.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "claude_reply"
private const val NOTIFICATION_ID = 1001

object NotificationHelper {

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claudeからの返信",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * 新しいClaudeの返信を検知したときにスマホ側へ出す通知。
     * ここで出す通知は、Wear OSがペアリング済みであれば自動的にウォッチ側にも
     * ブリッジされる（Wear OSの標準的な通知ブリッジ機能）。
     * ただし今回はプレビュー表示の一貫性のため、専用の軽量メッセージも
     * MobileMainActivity側からDataLayer経由で別途ウォッチへ送っている。
     */
    fun showNewReply(context: Context, text: String) {
        val preview = if (text.length > 200) text.take(200) + "…" else text
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Claudeから返信が届きました")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
