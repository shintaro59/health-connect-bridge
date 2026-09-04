package com.example.claudewatchbridge.mobile

import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * WatchMessageListenerService（ウォッチからの返信を受け取るサービス）は
 * MainActivityとは別のライフサイクルで動くため、直接WebViewを持てない。
 * ここに「今生きているWebViewへの弱参照」を置いておき、サービス側から
 * 「ウォッチでこう入力されたので、claude.aiに送信して」と頼めるようにする。
 *
 * アプリがバックグラウンドでWebViewがまだ生成されていないタイミングで
 * 返信が届いた場合は pendingReply に一時保存しておき、MainActivity側の
 * WebViewが用意でき次第、そちらから吸い出して送信する。
 */
object ClaudeWebBridgeState {
    private var webViewRef: WeakReference<WebView>? = null
    private var pendingReply: String? = null

    @Synchronized
    fun attachWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
        // アプリが後から前面に戻ってWebViewが用意できたタイミングでのアタッチもあるため、
        // 保留中の返信があればここで流し込む。
        pendingReply?.let { text ->
            pendingReply = null
            ClaudeInjection.sendReply(webView, text)
        }
    }

    @Synchronized
    fun detachWebView(webView: WebView) {
        if (webViewRef?.get() === webView) {
            webViewRef = null
        }
    }

    @Synchronized
    fun deliverReply(text: String) {
        val webView = webViewRef?.get()
        if (webView != null) {
            ClaudeInjection.sendReply(webView, text)
        } else {
            // WebViewがまだ準備できていない（アプリ未起動等）。次のattachで流し込む。
            pendingReply = text
        }
    }
}
