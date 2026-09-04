package com.example.claudewatchbridge.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * ウォッチ側（wearモジュール）から送られてくる返信テキストを受け取るサービス。
 * MainActivityが前面にない状態でも、OSがこのサービスを起動して呼び出してくれる。
 */
class WatchMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_REPLY) return
        val text = String(event.data, Charsets.UTF_8)
        if (text.isBlank()) return
        ClaudeWebBridgeState.deliverReply(text)
    }
}
