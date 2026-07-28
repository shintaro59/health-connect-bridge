package com.example.healthconnectbridge

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive への「Health Connect Bridge」フォルダ経由アップロード。
 * PC側 authenticate_google_drive.py で発行した refresh_token/client_id/client_secret を
 * 使ってアクセストークンをその都度リフレッシュするため、長期間ノーメンテで動作する
 * （ペーストしたアクセストークン自体は1時間で失効するため、リフレッシュトークン方式にしている）。
 */
class DriveUploader(
    private val clientId: String,
    private val clientSecret: String,
    private val refreshToken: String
) {
    companion object {
        private const val FOLDER_NAME = "Health Connect Bridge"
    }

    private fun refreshAccessToken(): String {
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val body = listOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token"
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }

        if (code !in 200..299) {
            throw Exception("トークン更新失敗 ($code): $responseText")
        }

        return JSONObject(responseText).getString("access_token")
    }

    private fun findFolderId(accessToken: String): String? {
        val query = URLEncoder.encode(
            "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8"
        )
        val url = URL("https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val files = JSONObject(responseText).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createFolder(accessToken: String): String {
        val url = URL("https://www.googleapis.com/drive/v3/files")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

        val metadata = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        conn.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) {
            throw Exception("フォルダ作成失敗 ($code): $responseText")
        }
        return JSONObject(responseText).getString("id")
    }

    private fun findOrCreateFolderId(accessToken: String): String {
        return findFolderId(accessToken) ?: createFolder(accessToken)
    }

    /** sleep_data を含むファイル名で新規ファイルとしてアップロードする（2段階: メタデータ作成→中身アップロード） */
    fun uploadSleepData(fileName: String, content: String): Result<String> {
        return try {
            val accessToken = refreshAccessToken()
            val folderId = findOrCreateFolderId(accessToken)

            // Step 1: メタデータでファイルを作成
            val createUrl = URL("https://www.googleapis.com/drive/v3/files")
            val createConn = createUrl.openConnection() as HttpURLConnection
            createConn.requestMethod = "POST"
            createConn.doOutput = true
            createConn.setRequestProperty("Authorization", "Bearer $accessToken")
            createConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", listOf(folderId))
            }
            createConn.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }

            val createCode = createConn.responseCode
            val createStream = if (createCode in 200..299) createConn.inputStream else createConn.errorStream
            val createResponse = createStream.bufferedReader().use { it.readText() }
            if (createCode !in 200..299) {
                throw Exception("ファイル作成失敗 ($createCode): $createResponse")
            }
            val fileId = JSONObject(createResponse).getString("id")

            // Step 2: 中身をアップロード
            val uploadUrl = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            val uploadConn = uploadUrl.openConnection() as HttpURLConnection
            uploadConn.requestMethod = "PATCH"
            uploadConn.doOutput = true
            uploadConn.setRequestProperty("Authorization", "Bearer $accessToken")
            uploadConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            uploadConn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }

            val uploadCode = uploadConn.responseCode
            if (uploadCode !in 200..299) {
                val err = uploadConn.errorStream.bufferedReader().use { it.readText() }
                throw Exception("アップロード失敗 ($uploadCode): $err")
            }

            Result.success(fileId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
