package com.example.claudewatchbridge.wear

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private lateinit var voiceInputLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private var onVoiceResult: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceInputLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            onVoiceResult?.invoke(text)
        }

        setContent {
            MaterialTheme {
                ClaudeWatchScreen(
                    onReplyRequested = { onDone -> startVoiceInput(onDone) }
                )
            }
        }
    }

    private fun startVoiceInput(onResult: (String?) -> Unit) {
        onVoiceResult = onResult
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Claudeへの返信")
        }
        voiceInputLauncher.launch(intent)
    }

    private fun sendReplyToPhone(text: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, PATH_REPLY, text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    @Composable
    private fun ClaudeWatchScreen(onReplyRequested: ((String?) -> Unit) -> Unit) {
        val latestMessage by LatestMessageStore.latestMessage.collectAsState()
        var lastSentPreview by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = latestMessage,
                textAlign = TextAlign.Center,
                maxLines = 6,
            )

            lastSentPreview?.let { sent ->
                Text(
                    text = "送信済み：$sent",
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }

            Chip(
                onClick = {
                    onReplyRequested { spokenText ->
                        if (!spokenText.isNullOrBlank()) {
                            sendReplyToPhone(spokenText)
                            lastSentPreview = spokenText
                        }
                    }
                },
                label = { Text("返信する") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}
