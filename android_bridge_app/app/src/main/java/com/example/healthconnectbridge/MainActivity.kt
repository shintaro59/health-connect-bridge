package com.example.healthconnectbridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "health_connect_bridge_prefs"
private const val KEY_CLIENT_ID = "drive_client_id"
private const val KEY_CLIENT_SECRET = "drive_client_secret"
private const val KEY_REFRESH_TOKEN = "drive_refresh_token"
private const val KEY_ROUTINE_URL = "routine_trigger_url"
private const val KEY_ROUTINE_TOKEN = "routine_trigger_token"
const val SYNC_WORK_NAME = "health_connect_bridge_sync"
const val WAKE_CHECK_WORK_NAME = "health_connect_bridge_wake_check"

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
    var routineUrl by remember { mutableStateOf(prefs.getString(KEY_ROUTINE_URL, "") ?: "") }
    var routineToken by remember { mutableStateOf(prefs.getString(KEY_ROUTINE_TOKEN, "") ?: "") }
    var statusText by remember { mutableStateOf("準備完了") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var coarseLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

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

    val requestCoarseLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        coarseLocationGranted = granted
        statusText = if (granted) {
            "✅ 位置情報（使用中）が許可されました。続けて「常に許可」の設定もお願いします"
        } else {
            "⚠️ 位置情報の権限が許可されませんでした。天気の現在地確認はスキップされます"
        }
    }

    LaunchedEffect(Unit) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        permissionsGranted = granted.containsAll(requiredPermissions())
    }

    // 「設定で常に許可」から戻ってきた時に権限表示を再チェックする
    // （設定画面遷移はActivityの外なので、Composeの状態が自動更新されないため）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coarseLocationGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                backgroundLocationGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                        val sleepData = withContext(Dispatchers.IO) {
                            HealthConnectFetcher(context).fetchSleepData()
                        }

                        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
                            statusText = "⚠️ 睡眠データは取得できましたが、Drive認証情報が未設定のためアップロードをスキップしました"
                        } else {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val result = withContext(Dispatchers.IO) {
                                DriveUploader(clientId, clientSecret, refreshToken)
                                    .uploadSleepData("sleep_data_$timestamp.json", sleepData)
                            }
                            statusText = result.fold(
                                onSuccess = { "✅ Google Driveにアップロード完了 (fileId: $it)" },
                                onFailure = { "❌ アップロード失敗: ${it.message ?: it.javaClass.simpleName}" }
                            )
                        }
                    } catch (e: Exception) {
                        statusText = "❌ エラー: ${e.message ?: e.javaClass.simpleName}"
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

        Text(text = "位置情報（天気の判定に使用。PCは自宅固定でも本人は移動するため、都度スマホから取得）")
        Text(
            text = if (coarseLocationGranted && backgroundLocationGranted) {
                "✅ 位置情報: 許可済み（使用中＋常に）"
            } else if (coarseLocationGranted) {
                "⚠️ 「使用中のみ」許可済み。バックグラウンド検知で使うには「常に許可」も必要です"
            } else {
                "⚠️ 位置情報: 未許可"
            }
        )

        Button(
            onClick = {
                if (coarseLocationGranted) {
                    statusText = "✅ 位置情報（使用中）は許可済みです"
                } else {
                    requestCoarseLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("位置情報（使用中のみ）を許可")
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
                statusText = "設定画面を開きました。「位置情報」→「常に許可」を選択してください"
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("設定で「常に許可」にする")
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = "起床検知 → Claude Routine 起動（claude.ai/code/routines で発行したAPIトリガーのURLとトークン）")

        OutlinedTextField(
            value = routineUrl,
            onValueChange = { routineUrl = it },
            label = { Text("Routine トリガーURL") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = routineToken,
            onValueChange = { routineToken = it },
            label = { Text("Routine Bearerトークン") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_ROUTINE_URL, routineUrl)
                    .putString(KEY_ROUTINE_TOKEN, routineToken)
                    .apply()
                statusText = "✅ Routine連携情報を保存しました"
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("保存")
        }

        Button(
            onClick = {
                val request = PeriodicWorkRequestBuilder<WakeDetectionWorker>(15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WAKE_CHECK_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                statusText = "✅ 起床検知のバックグラウンドチェックを開始しました（15分ごと）"
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("起床検知を開始")
        }

        Button(
            onClick = {
                scope.launch {
                    if (routineUrl.isBlank() || routineToken.isBlank()) {
                        statusText = "⚠️ 先にRoutine連携情報を保存してください"
                    } else {
                        statusText = "テスト起動中（現在地取得中）..."
                        val result = withContext(Dispatchers.IO) {
                            // 本番の起床検知と同じ形式で現在地も載せる。
                            // これにより天気確認まで含めたエンドツーエンドの動作確認ができる。
                            val location = LocationHelper.currentLocation(context)
                            val locationText = when {
                                location == null ->
                                    "現在地: 取得できませんでした（位置情報の権限が未許可の可能性があります）"
                                location.label != null ->
                                    "現在地: ${location.label}（緯度${location.latitude}, 経度${location.longitude}）"
                                else ->
                                    "現在地: 緯度${location.latitude}, 経度${location.longitude}"
                            }
                            RoutineTrigger.fire(
                                routineUrl,
                                routineToken,
                                "これはHealth Connect Bridgeアプリからのテスト起動です（実際の起床検知ではありません）。\n" +
                                    "$locationText\n" +
                                    "動作確認のため、実際の起床時と同じ手順（予定・天気の確認、メール送信）を通しで実行してください。"
                            )
                        }
                        statusText = result.fold(
                            onSuccess = { "✅ Routineをテスト起動しました" },
                            onFailure = { "❌ 起動失敗: ${it.message ?: it.javaClass.simpleName}" }
                        )
                    }
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Routineを今すぐテスト起動")
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = statusText)
    }
}
