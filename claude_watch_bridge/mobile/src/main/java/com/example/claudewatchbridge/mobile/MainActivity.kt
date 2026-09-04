package com.example.claudewatchbridge.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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

        // chrome://inspect でこのWebViewの中身をPCから覗けるようにする（デバッグ用）。
        // デバッグビルドでしか使わないので常時有効化して問題ない。
        WebView.setWebContentsDebuggingEnabled(true)

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

                // デフォルトのWebViewのUser-Agentには「; wv)」というマーカーが付いていて、
                // サイト側が「組み込みWebViewからのアクセス」と判定してログイン後の画面を
                // 出さない（Googleが自社サービスへの埋め込みWebViewログインをブロックするのは
                // 有名だが、他のサービスも同様の判定をしていることがある）。
                // 通常のモバイルChromeと見分けが付かないUser-Agentに書き換える。
                settings.userAgentString = settings.userAgentString
                    .replace("; wv", "")
                    .replace(Regex("Version/[\\d.]+ "), "")

                // ログインフローの一部（他サービス連携等）がCookieの読み書きを
                // 別オリジン経由で行うことがあるため、サードパーティCookieも許可しておく。
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // ログイン処理中にJavaScriptがwindow.open()で別ウィンドウを開こうとすると、
                // 普通のWebViewは何もせず握りつぶしてしまい、画面が真っ白なまま固まって見える
                // ことがある。同じWebView内でそのURLを開く形で代替する。
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message
                    ): Boolean {
                        val newWebView = WebView(view.context)
                        val transport = resultMsg.obj as WebView.WebViewTransport
                        transport.webView = newWebView
                        resultMsg.sendToTarget()
                        newWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView,
                                request: android.webkit.WebResourceRequest
                            ): Boolean {
                                // 新規ウィンドウ扱いにせず、元のWebViewでそのままそのURLを開く。
                                view.loadUrl(request.url.toString())
                                return true
                            }
                        }
                        return true
                    }
                }

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
