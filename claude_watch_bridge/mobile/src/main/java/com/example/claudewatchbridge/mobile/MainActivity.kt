package com.example.claudewatchbridge.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private const val CLAUDE_URL = "https://claude.ai"

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 結果は問わない */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClaudeWebView()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ClaudeWebView() {
        AndroidView(factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // claude.aiへ普段Chromeでログインしているのと同じ状態を維持したいので、
                // Cookie等は標準のWebViewストレージにそのまま任せる（別途クリアはしない）。

                addJavascriptInterface(ClaudeJsBridge(this@MainActivity), "ClaudeBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(ClaudeInjection.OBSERVER_SCRIPT, null)
                    }
                }

                loadUrl(CLAUDE_URL)
                ClaudeWebBridgeState.attachWebView(this)
            }
        })
    }

    /**
     * claude.aiのページ内JavaScript（ClaudeInjection.OBSERVER_SCRIPT）から
     * window.ClaudeBridge.onNewMessage(text) の形で呼ばれるコールバック。
     * WebViewのJavaScriptInterfaceは呼び出しがバックグラウンドスレッドになるため、
     * 通知・Data Layer送信はメインスレッドへポストしてから行う。
     */
    private class ClaudeJsBridge(private val activity: MainActivity) {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onNewMessage(text: String) {
            mainHandler.post {
                NotificationHelper.showNewReply(activity, text)
                WatchRelay.sendToWatches(activity, text)
            }
        }
    }
}
