package com.example.claudewatchbridge.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PhoneMessageListenerService（バックグラウンドで動くサービス）が受信した
 * 「Claudeからの最新の返信」を、MainActivityのCompose UIから購読できるように
 * 保持しておくだけのシンプルな状態置き場。
 */
object LatestMessageStore {
    private val _latestMessage = MutableStateFlow("まだメッセージがありません")
    val latestMessage: StateFlow<String> = _latestMessage

    fun update(text: String) {
        _latestMessage.value = text
    }
}

/** スマホ↔ウォッチ間でやり取りするData Layer APIのメッセージパス（mobile側と同じ値）。 */
const val PATH_INCOMING_MESSAGE = "/claude_watch_bridge/incoming"
const val PATH_REPLY = "/claude_watch_bridge/reply"
