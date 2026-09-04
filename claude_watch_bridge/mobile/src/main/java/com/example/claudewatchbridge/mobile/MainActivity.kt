package com.example.claudewatchbridge.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        ClaudeWebView(modifier = Modifier.weight(1f))
                        Divider()
                        LogPanel(modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp))
                    }
                }
            }
        }
    }

    /**
     * DebugLogの中身を常時表示するだけのパネル。WebView側が真っ白でも
     * ここだけは必ず生きているので、PC無しでも今何が起きているかを追える。
     */
    @Composable
    private fun LogPanel(modifier: Modifier = Modifier) {
        val entries by DebugLog.entries.collectAsState()
        val listState = rememberLazyListState()

        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) {
                listState.animateScrollToItem(entries.size - 1)
            }
        }

        Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
            Text(
                text = "デバッグログ",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(entries) { line ->
                    Text(
                        text = line,
                        color = Color(0xFF00FF88),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ClaudeWebView(modifier: Modifier = Modifier) {
        AndroidView(modifier = modifier, factory = { context ->
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

                    // ページ内のconsole.log/error/warnをそのまま拾えるようにする。
                    // Reactアプリが内部で握りつぶしているエラーも、大抵はここか
                    // window.onerror（下のEARLY_ERROR_SCRIPT）のどちらかに出てくる。
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        val text = "[console.${consoleMessage.messageLevel()}] ${consoleMessage.message()}" +
                            " (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                        Log.d("ClaudeWatchBridge", text)
                        DebugLog.add(text)
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
                        }
                        return true
                    }
                }

                addJavascriptInterface(ClaudeJsBridge(this@MainActivity), "ClaudeBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        DebugLog.add("→ 遷移開始: $url")
                        // onPageFinishedを待つと、そこに到達する前に発生したエラーを取り逃すため、
                        // ページ読み込み開始時点でエラー捕捉フックを先に仕込んでおく。
                        view.evaluateJavascript(ClaudeInjection.EARLY_ERROR_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLog.add("✓ 読み込み完了: $url")
                        view.evaluateJavascript(ClaudeInjection.OBSERVER_SCRIPT, null)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        val text = "[読み込みエラー${if (request.isForMainFrame) "・メインフレーム" else ""}] " +
                            "${error.description} (${request.url})"
                        Log.e("ClaudeWatchBridge", text)
                        DebugLog.add(text)
                        if (request.isForMainFrame) {
                            Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        // trueを返すと自分でloadUrlしない限りその遷移を握りつぶすことになるため、
                        // ここでは何もせずfalse（デフォルトの、WebView自身が遷移する挙動）を返す。
                        // ログだけ残して、意図しない外部アプリへのIntentディスパッチが起きていないか追えるようにする。
                        DebugLog.add("shouldOverrideUrlLoading: ${request.url}")
                        return false
                    }
                }

                loadUrl(CLAUDE_URL)
                DebugLog.add("loadUrl($CLAUDE_URL) を呼び出しました")
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

        /** ClaudeInjection.EARLY_ERROR_SCRIPT が捕まえた未処理のJSエラーを画面に出す。 */
        @JavascriptInterface
        fun onJsError(text: String) {
            mainHandler.post {
                Log.e("ClaudeWatchBridge", text)
                DebugLog.add(text)
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}
