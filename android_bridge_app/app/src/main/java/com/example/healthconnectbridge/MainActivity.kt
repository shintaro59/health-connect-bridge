package com.example.healthconnectbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { /* TODO: Check permissions */ }) {
            Text("パーミッション確認")
        }

        Button(
            onClick = { /* TODO: Fetch sleep data */ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("今すぐ睡眠データを取得")
        }

        Button(
            onClick = { /* TODO: Start background sync */ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("バックグラウンド同期を開始")
        }
    }
}
