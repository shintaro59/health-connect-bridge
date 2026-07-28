package com.example.healthconnectbridge

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_CLIENT_ID = "drive_client_id"
private const val KEY_CLIENT_SECRET = "drive_client_secret"
private const val KEY_REFRESH_TOKEN = "drive_refresh_token"
const val SYNC_WORK_NAME = "health_connect_bridge_sync"

fun requiredPermissions(): Set<String> = setOf(
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf(prefs.getString(KEY_CLIENT_ID, "") ?: "") }
    var clientSecret by remember { mutableStateOf(prefs.getString(KEY_CLIENT_SECRET, "") ?: "") }
    var refreshToken by remember { mutableStateOf(prefs.getString(KEY_REFRESH_TOKEN, "") ?: "") }
    var statusText by remember { mutableStateOf("準備完了") }
    var permissionsGranted by remember { mutableStateOf(false) }

    val healthConnectClient = remember { HealthConnectClient.getOrCreate(context) }

    val requestPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionsGranted = granted.containsAll(requiredPermissions())
        statusText = if (permissionsGranted) {
            "✅ Health Connect の権限が許可されました"
        } else {
            "⚠️ 一部の権限が許可されませんでした（付与済み: ${granted.size}/${requiredPermissions().size}）"
        }
    }

    LaunchedEffect(Unit) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        permissionsGranted = granted.containsAll(requiredPermissions())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = if (permissionsGranted) "Health Connect: 許可済み ✅" else "Health Connect: 未許可 ⚠️")

        Button(
            onClick = {
                scope.launch {
                    val granted = healthConnectClient.permissionController.getGrantedPermissions()
                    if (granted.containsAll(requiredPermissions())) {
                        permissionsGranted = true
                        statusText = "✅ 権限は既に許可されています"
                    } else {
                        requestPermissions.launch(requiredPermissions())
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("パーミッション確認")
        }

        Button(
            onClick = {
                scope.launch {
                    statusText = "取得中..."
                    try {
                        val fetcher = HealthConnectFetcher(context)
                        val sleepData = fetcher.fetchSleepData()

                        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
                            statusText = "⚠️ 睡眠データは取得できましたが、Drive認証情報が未設定のためアップロードをスキップしました"
                        } else {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val uploader = DriveUploader(clientId, clientSecret, refreshToken)
                            val result = uploader.uploadSleepData("sleep_data_$timestamp.json", sleepData)
                            statusText = result.fold(
                                onSuccess = { "✅ Google Driveにアップロード完了 (fileId: $it)" },
                                onFailure = { "❌ アップロード失敗: ${it.message}" }
                            )
                        }
                    } catch (e: Exception) {
                        statusText = "❌ エラー: ${e.message}"
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("今すぐ睡眠データを取得")
        }

        Button(
            onClick = {
                if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
                    statusText = "⚠️ 先にDrive認証情報を保存してください"
                } else {
                    val request = PeriodicWorkRequestBuilder<SleepDataWorker>(6, TimeUnit.HOURS)
                        .build()
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        SYNC_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
                    statusText = "✅ バックグラウンド同期を開始しました（6時間ごと）"
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("バックグラウンド同期を開始")
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = "Google Drive 認証情報（PC側 authenticate_google_drive.py 実行後の token.json から転記）")

        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = { Text("client_id") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = clientSecret,
            onValueChange = { clientSecret = it },
            label = { Text("client_secret") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = refreshToken,
            onValueChange = { refreshToken = it },
            label = { Text("refresh_token") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_CLIENT_ID, clientId)
                    .putString(KEY_CLIENT_SECRET, clientSecret)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .apply()
                statusText = "✅ Drive認証情報を保存しました"
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("保存")
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = statusText)
    }
}
