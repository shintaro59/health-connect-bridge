package com.example.claudewatchbridge.mobile

import android.webkit.WebView
import org.json.JSONObject

/**
 * claude.aiのページに対してJavaScriptを注入するためのスクリプト集。
 *
 * 【重要な注意】
 * ここで使っているセレクタ（data-testid, class名など）は、実機でclaude.aiを開いて
 * 確認できていない前提のベストエフォートな実装。Anthropic側がフロントエンドの
 * DOM構造を変更すると、ここが動かなくなる可能性が高い。実機で動作確認した上で、
 * 開発者ツールで実際の要素を調べてセレクタを調整することを前提にしている。
 */
object ClaudeInjection {

    /**
     * ページ読み込み開始時（onPageStarted）にできるだけ早く注入する、
     * 未処理のJSエラーをネイティブ側（window.ClaudeBridge.onJsError）へ転送するフック。
     * 画面が真っ白になる系の不具合は大抵ここかconsole.errorのどちらかで拾える。
     */
    const val EARLY_ERROR_SCRIPT = """
        (function() {
            if (window.__claudeWatchBridgeErrorHookInstalled) return;
            window.__claudeWatchBridgeErrorHookInstalled = true;

            window.addEventListener('error', function(event) {
                if (window.ClaudeBridge) {
                    var msg = '[JSエラー] ' + (event.message || '') + ' @ ' + (event.filename || '') + ':' + (event.lineno || '');
                    window.ClaudeBridge.onJsError(msg);
                }
            });

            window.addEventListener('unhandledrejection', function(event) {
                if (window.ClaudeBridge) {
                    var reason = event.reason && (event.reason.message || event.reason.toString()) || String(event.reason);
                    window.ClaudeBridge.onJsError('[未処理のPromiseエラー] ' + reason);
                }
            });
        })();
    """

    /**
     * この端末のWebViewは、100vh/100dvhのようなビューポート相対単位のCSS計算が
     * 常に0になってしまう（claude.aiのCSSとは無関係の、まっさらな要素で試しても
     * 再現する、WebViewエンジン自体の不具合）ことが実機調査で判明した。
     *
     * 最初はhtml/bodyだけを固定pxで上書きすれば直ると考えていたが、実際には
     * body→(display:contents)→(display:contents)→div.root→div.grid...と、
     * vh/dvh依存の高さ0が何階層にもわたって連鎖していることが分かった。
     * どの階層がどのクラス名で壊れているかを個別に追いかけるより、
     * 「高さ0で子要素を持つ箱を見つけたら、親の実際の高さを継承させる」処理を
     * ツリー全体に再帰的に適用する方が確実（CSSのheight:100%継承をJS側で
     * 肩代わりするイメージ）。display:contentsの要素は箱を持たないので
     * スキップし、そのまま同じ高さ基準を子へ渡す。
     *
     * Reactの再描画で毎回インラインスタイルが上書き・削除される可能性があるため、
     * MutationObserverで継続的に再適用する（デバウンス付き）。
     */
    const val VIEWPORT_HEIGHT_FIX_SCRIPT = """
        (function() {
            function heal(el, parentHeightPx) {
                if (!el || el.nodeType !== 1) return;
                var cs = window.getComputedStyle(el);
                if (cs.display === 'none') return;
                if (cs.display === 'contents') {
                    for (var i = 0; i < el.children.length; i++) {
                        heal(el.children[i], parentHeightPx);
                    }
                    return;
                }
                var rect = el.getBoundingClientRect();
                if (rect.height === 0 && el.children.length > 0) {
                    el.style.setProperty('height', parentHeightPx + 'px', 'important');
                }
                var effectiveHeight = el.getBoundingClientRect().height || parentHeightPx;
                for (var j = 0; j < el.children.length; j++) {
                    heal(el.children[j], effectiveHeight);
                }
            }

            function applyViewportHeightFix() {
                var px = window.innerHeight;
                var html = document.documentElement;
                if (html) {
                    html.style.setProperty('height', px + 'px', 'important');
                    html.style.setProperty('min-height', px + 'px', 'important');
                }
                if (document.body) {
                    heal(document.body, px);
                }
            }

            applyViewportHeightFix();
            window.addEventListener('resize', applyViewportHeightFix);
            setTimeout(applyViewportHeightFix, 0);
            setTimeout(applyViewportHeightFix, 300);
            setTimeout(applyViewportHeightFix, 1000);
            setTimeout(applyViewportHeightFix, 3000);

            // Reactの再描画でインラインスタイルが巻き戻される場合に備えて、
            // DOM変化を監視して再適用する（1秒デバウンス）。
            var debounceTimer = null;
            var observer = new MutationObserver(function() {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(applyViewportHeightFix, 1000);
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        })();
    """

    /**
     * ページ読み込み完了時に一度だけ注入する監視スクリプト。
     * Claudeの返信（アシスタント側の最新メッセージ）が更新されるたびに、
     * ストリーミング表示が落ち着く（1.5秒間変化がなくなる）のを待ってから
     * ネイティブ側（window.ClaudeBridge.onNewMessage）へ通知する。
     */
    const val OBSERVER_SCRIPT = """
        (function() {
            if (window.__claudeWatchBridgeInstalled) return;
            window.__claudeWatchBridgeInstalled = true;

            function extractLatestAssistantText() {
                var candidates = document.querySelectorAll(
                    '[data-testid="assistant-message"], .font-claude-message, [data-is-streaming]'
                );
                if (candidates.length === 0) return null;
                var last = candidates[candidates.length - 1];
                return (last.innerText || last.textContent || '').trim();
            }

            var lastNotified = null;
            var debounceTimer = null;

            function onMutation() {
                var text = extractLatestAssistantText();
                if (!text) return;
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function() {
                    if (text !== lastNotified) {
                        lastNotified = text;
                        if (window.ClaudeBridge) {
                            window.ClaudeBridge.onNewMessage(text);
                        }
                    }
                }, 1500);
            }

            var observer = new MutationObserver(onMutation);
            observer.observe(document.body, { childList: true, subtree: true, characterData: true });
        })();
    """

    /**
     * ウォッチから届いたテキストを、claude.aiの入力欄に流し込んで送信する。
     * 入力欄がtextarea（value属性）かcontenteditableなdivかどちらの実装でも
     * 動くよう両対応にしている。
     */
    fun sendReply(webView: WebView, text: String) {
        val escapedText = JSONObject.quote(text)
        val script = """
            (function(text) {
                function setNativeValue(element, value) {
                    var proto = Object.getPrototypeOf(element);
                    var protoSetter = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (protoSetter && protoSetter.set) {
                        protoSetter.set.call(element, value);
                    } else {
                        element.value = value;
                    }
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                }

                var input = document.querySelector('div[contenteditable="true"]') || document.querySelector('textarea');
                if (!input) return false;

                if (input.tagName === 'TEXTAREA') {
                    setNativeValue(input, text);
                } else {
                    input.focus();
                    document.execCommand('insertText', false, text);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                }

                var sendButton = document.querySelector(
                    'button[aria-label*="Send"], button[aria-label*="送信"], button[data-testid="send-button"]'
                );
                if (sendButton && !sendButton.disabled) {
                    sendButton.click();
                    return true;
                }

                input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
                return true;
            })($escapedText);
        """
        webView.evaluateJavascript(script, null)
    }
}
