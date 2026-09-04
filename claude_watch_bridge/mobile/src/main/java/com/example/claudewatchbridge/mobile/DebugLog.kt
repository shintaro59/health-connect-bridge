package com.example.claudewatchbridge.mobile

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val MAX_ENTRIES = 80

/**
 * 画面が真っ白で何も操作できない状態でも原因を追えるように、
 * WebViewのナビゲーション・console出力・エラーを溜めておいて
 * アプリ内に常時表示するためのログバッファ。PCもadbも不要にする狙い。
 */
object DebugLog {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    @Synchronized
    fun add(text: String) {
        val timestamped = "${formatter.format(Date())}  $text"
        val updated = (_entries.value + timestamped).takeLast(MAX_ENTRIES)
        _entries.value = updated
    }
}
