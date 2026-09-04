package com.example.claudewatchbridge.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_INCOMING_MESSAGE) return
        val text = String(event.data, Charsets.UTF_8)
        if (text.isBlank()) return
        LatestMessageStore.update(text)
    }
}
