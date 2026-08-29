package com.example.healthconnectbridge

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Claude Code Web の Routine「APIトリガー」を呼び出す。
 *
 * claude.ai/code/routines でRoutineに「API」トリガーを追加すると、
 * 専用のURLとBearerトークンが発行される。それをアプリの設定欄に
 * 貼り付けて使うことで、このアプリから直接POSTするだけで
 * 新しいClaude Codeセッションを即座に起動できる。
 *
 * text パラメータは Routine 側で <routine-fire-payload> として
 * 「信頼できない参考情報」扱いで渡される（Routine自身の保存済みプロンプトが
 * それを参照するよう書かれていて初めて使われる）。
 */
object RoutineTrigger {
    fun fire(url: String, bearerToken: String, text: String): Result<String> {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            // Routine /fire エンドポイントの必須ヘッダー。欠けると
            // 「anthropic-version: header is required」で400になる。
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("anthropic-beta", "experimental-cc-routine-2026-04-01")

            val body = JSONObject().apply { put("text", text) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                throw Exception("Routine起動失敗 ($code): $responseText")
            }
            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
