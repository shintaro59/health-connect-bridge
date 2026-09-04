package com.example.claudewatchbridge.mobile

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** スマホ↔ウォッチ間でやり取りするData Layer APIのメッセージパス。 */
const val PATH_INCOMING_MESSAGE = "/claude_watch_bridge/incoming"
const val PATH_REPLY = "/claude_watch_bridge/reply"

/**
 * Claudeからの新しい返信を、ペアリング済みの全ウォッチへ軽量テキストとして送る。
 * ウォッチ側はこれを受け取ってプレビュー表示・返信ボタンの起点にする。
 */
object WatchRelay {
    fun sendToWatches(context: Context, text: String) {
        val preview = if (text.length > 500) text.take(500) else text
        val payload = preview.toByteArray(Charsets.UTF_8)

        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, PATH_INCOMING_MESSAGE, payload)
            }
        }
    }
}
