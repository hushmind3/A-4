package com.dominion.mobile.ui.screens.chats

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.CallPhase
import com.dominion.mobile.platform.audio.CallForegroundService
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.tr

@Composable
fun CallScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val call = state.call
    if (call == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val assistant = state.character(call.characterId)
    fun connect() {
        try {
            CallForegroundService.start(context)
        } catch (_: Exception) {
            vm.failCall(call.sessionId, "Microphone foreground service could not start")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connect() else vm.failCall(call.sessionId, "Microphone permission denied")
    }
    LaunchedEffect(call.sessionId) {
        if (call.phase == CallPhase.PERMISSION) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                connect()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun end() {
        vm.endCall()
        CallForegroundService.stop(context)
        onBack()
    }
    fun minimize() {
        if (call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT, CallPhase.RECONNECTING)) {
            vm.minimizeCall(true)
            onBack()
        } else end()
    }
    BackHandler { minimize() }

    val label = when (call.phase) {
        CallPhase.PERMISSION -> tr(lang, "마이크 권한 확인", "Microphone permission")
        CallPhase.CONNECTING -> tr(lang, "음성 서버 연결 중", "Connecting to voice server")
        CallPhase.WAITING_PEER -> tr(lang, "음성 서버 연결됨 · 상대 대기 중", "Media connected · waiting for peer")
        CallPhase.PEER_PRESENT -> tr(lang, "상대 접속 · 음성 세션", "Peer joined · voice session")
        CallPhase.RECONNECTING -> tr(lang, "재연결 중", "Reconnecting")
        CallPhase.FAILED -> tr(lang, "음성 연결 실패", "Voice connection failed")
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF12141A)).statusBarsPadding()
            .navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { minimize() }) { Text(tr(lang, "닫기", "Close")) }
            Text(tr(lang, "음성 통화", "Voice call"), color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        assistant?.let { AssistantAvatar(Color(it.color), it.shape, size = 150.dp) }
        Spacer(Modifier.height(24.dp))
        Text(assistant?.name ?: "DOMINION", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(label, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp))
        if (call.phase == CallPhase.FAILED) {
            Text(call.error.orEmpty(), color = Color(0xFFFFB4AB), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = vm::toggleCallMute,
                enabled = call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT),
                modifier = Modifier.weight(1f)
            ) { Text(if (call.muted) tr(lang, "음소거 해제", "Unmute") else tr(lang, "음소거", "Mute")) }
            Button(onClick = { end() }, modifier = Modifier.weight(1f)) {
                Text(tr(lang, "통화 종료", "End call"))
            }
        }
    }
}
