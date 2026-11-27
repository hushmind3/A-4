package com.dominion.mobile.ui.screens.chats

import android.Manifest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dominion.mobile.data.model.CallPhase
import com.dominion.mobile.ui.tr
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.audio.CallForegroundService
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.util.formatDuration
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

@Composable
fun CallScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val call = state.call
    val character = call?.let { state.character(it.characterId) }

    if (call == null || character == null) {
        LaunchedEffect(Unit) {
            delay(800)
            onBack()
        }
        return
    }

    fun connect() {
        try {
            CallForegroundService.start(context)
        } catch (_: Exception) {
            vm.failCall(call.sessionId, "Microphone foreground service could not start")
        }
    }
    val media by CallForegroundService.mediaState.collectAsState()
    val mediaReady = media.sessionId == call.sessionId && call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val mic = granted[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = !call.requestVideo || granted[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (mic && camera) connect() else vm.failCall(call.sessionId, "통화에 필요한 마이크 또는 카메라 권한이 거부되었습니다.")
    }
    var confirmScreenShare by remember(call.sessionId) { mutableStateOf(false) }
    var requestingProjection by remember(call.sessionId) { mutableStateOf(false) }
    val projectionPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        requestingProjection = false
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null &&
            mediaReady && vm.state.value.call?.sessionId == call.sessionId) {
            runCatching { CallForegroundService.screen(context, call.sessionId, true, result.data) }
                .onFailure { context.toast("화면 공유 시작 실패") }
        } else context.toast("화면 공유가 시작되지 않았습니다.")
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && mediaReady) runCatching { CallForegroundService.camera(context, true) }
            .onFailure { context.toast("카메라 전환 실패") }
        else if (!granted) context.toast("카메라 권한이 필요합니다.")
    }
    LaunchedEffect(call.sessionId) {
        if (call.phase == CallPhase.PERMISSION) {
            val required = if (call.requestVideo) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else listOf(Manifest.permission.RECORD_AUDIO)
            val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) connect() else permission.launch(missing.toTypedArray())
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
    var elapsed by remember(call.startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(call.sessionId, call.phase) {
        if (call.phase == CallPhase.PEER_PRESENT) {
            while (true) {
                elapsed = System.currentTimeMillis() - call.startedAt
                delay(1000)
            }
        }
    }

    var requestInstruction by remember(call.sessionId) { mutableStateOf(false) }
    var reviewTextWork by remember(call.sessionId) { mutableStateOf(false) }
    var remoteTranscript by remember(call.sessionId) { mutableStateOf(false) }
    var localCommand by remember(call.sessionId) { mutableStateOf(false) }
    var frameSource by remember(call.sessionId) { mutableStateOf<String?>(null) }
    var lastInstruction by remember(call.sessionId) { mutableStateOf<String?>(null) }
    val instruction = state.conversation(call.conversationId)?.messages?.firstOrNull {
        it.id == lastInstruction || (lastInstruction != null && it.clientMessageId == lastInstruction)
    }
    LaunchedEffect(call.sessionId) { vm.minimizeCall(false) }
    val accent = Color(character.color)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.42f),
                        Color(0xFF12141A),
                        Color(0xFF08090C)
                    )
                )
            )
    ) {
        if (call.video || (mediaReady && (media.remoteVideo != null || media.remoteScreen != null))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 76.dp, bottom = 200.dp, start = 18.dp, end = 18.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.34f), Color(0xFF1B1D24), Color(0xFF101114))
                        )
                    )
            ) {
                val remoteTrack = media.remoteScreen ?: media.remoteVideo
                val mediaRoom = media.room
                if (mediaReady && remoteTrack != null && mediaRoom != null) {
                    CallVideoView(mediaRoom, remoteTrack, Modifier.fillMaxSize())
                } else Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AssistantAvatar(accent, character.shape, size = 150.dp, breathing = true, photo = character.avatarPhoto)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        character.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "상대 영상 대기 중" else "Waiting for remote video",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(width = 82.dp, height = 112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A3E48), Color(0xFF1F2229)))),
                    contentAlignment = Alignment.Center
                ) {
                    val localTrack = media.localVideo
                    if (mediaReady && localTrack != null && mediaRoom != null) CallVideoView(mediaRoom, localTrack, Modifier.fillMaxSize())
                    else Text(tr(lang, "카메라 꺼짐", "Camera off"), color = Color.White.copy(alpha = 0.75f))
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { minimize() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌄", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (call.video) {
                            if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 통화" else "Video call"
                        } else {
                            if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "음성 통화" else "Voice call"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                    Text(if (call.phase == CallPhase.PEER_PRESENT) formatDuration(elapsed) else "—", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (call.phase == CallPhase.PEER_PRESENT) Color(0xFF4CC699) else Color.Gray))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            if (!call.video && !(mediaReady && (media.remoteVideo != null || media.remoteScreen != null))) {
                Spacer(Modifier.weight(0.6f))
                CallAvatar(accent, character)
                Spacer(Modifier.height(26.dp))
                Text(
                    character.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (call.phase == CallPhase.FAILED) call.error ?: label else label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (!call.video) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Waveform(active = mediaReady, color = Color.White.copy(alpha = 0.75f), level = if (mediaReady) media.audioLevel else 0f)
                }
            }

            if (mediaReady && media.error != null) Text(media.error.orEmpty(), color = Color(0xFFFFB4AB), modifier = Modifier.padding(8.dp))
            if (mediaReady) {
                if (media.room?.remoteParticipants?.isNotEmpty() == true) {
                    androidx.compose.material3.TextButton(onClick = { remoteTranscript = true }) {
                        Text("상대 음성 전사", color = Color.White)
                    }
                }
                androidx.compose.material3.TextButton(
                    enabled = !media.screenBusy && !requestingProjection,
                    onClick = {
                        if (media.sharingScreen) {
                            runCatching { CallForegroundService.screen(context, call.sessionId, false) }
                                .onFailure { context.toast("화면 공유 중지 실패") }
                        } else confirmScreenShare = true
                    }) {
                    Text(if (media.sharingScreen) "화면 공유 중 · 중지" else if (media.screenBusy) "화면 공유 변경 중" else "화면 공유",
                        color = if (media.sharingScreen) Color(0xFFFFC46B) else Color.White)
                }
                if (media.remoteScreen != null) Text("상대 화면 수신 중", color = Color.White.copy(alpha = 0.85f))
                if (media.localVideo != null || media.localScreen != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (media.localVideo != null) androidx.compose.material3.TextButton(onClick = { frameSource = "camera" }) {
                            Text("카메라 AI 분석", color = Color.White)
                        }
                        if (media.localScreen != null) androidx.compose.material3.TextButton(onClick = { frameSource = "screen" }) {
                            Text("공유 화면 AI 분석", color = Color.White)
                        }
                    }
                }
            }
            if (call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT, CallPhase.RECONNECTING)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    androidx.compose.material3.TextButton(onClick = { requestInstruction = true }) {
                        Text(tr(lang, "작업 요청", "Assign work"), color = Color.White)
                    }
                    if (mediaReady && !call.muted) androidx.compose.material3.TextButton(onClick = { localCommand = true }) {
                        Text("음성 명령", color = Color.White)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        vm.minimizeCall(true)
                        onOpenChat(call.conversationId)
                    }) { Text(tr(lang, "대화·결과 확인", "Chat and results"), color = Color.White) }
                    androidx.compose.material3.TextButton(onClick = { reviewTextWork = true }) {
                        Text("업무 기록", color = Color.White)
                    }
                }
                instruction?.let { row ->
                    Text(tr(lang, "요청 상태: ", "Request: ") + when (row.status) {
                        com.dominion.mobile.data.model.MessageStatus.SENDING -> tr(lang, "전송 중", "Sending")
                        com.dominion.mobile.data.model.MessageStatus.FAILED -> tr(lang, "실패 · 대화에서 재시도", "Failed · retry in chat")
                        else -> row.jobStatus ?: tr(lang, "서버 접수", "Accepted")
                    }, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControl(
                    glyph = if (call.muted) Glyph.MIC else Glyph.MIC,
                    label = if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "음소거" else "Mute",
                    active = call.muted,
                    onClick = { if (call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT)) vm.toggleCallMute() }
                )
                CallControl(
                    glyph = if (media.speaker) Glyph.SPEAKER else Glyph.SPEAKER_OFF,
                    label = if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "스피커" else "Speaker",
                    active = mediaReady && media.speaker,
                    onClick = { if (mediaReady) runCatching { CallForegroundService.speaker(context, !media.speaker) }.onFailure { context.toast("스피커 전환 실패") } }
                )
                CallControl(
                    glyph = Glyph.VIDEO,
                    label = if (call.video) {
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 끄기" else "Video off"
                    } else {
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 켜기" else "Video on"
                    },
                    active = call.video,
                    onClick = {
                        if (mediaReady && !media.cameraBusy) {
                            if (!call.video && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            else runCatching { CallForegroundService.camera(context, !call.video) }.onFailure { context.toast("카메라 전환 실패") }
                        }
                    }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEE1717))
                            .clickable {
                                end()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.PHONE, Color.White, size = 25.dp, modifier = Modifier.rotate(135f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "종료" else "End",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
    if (localCommand) {
        com.dominion.mobile.ui.components.RemoteSpeechDialog(vm, call.sessionId, call.conversationId,
            onDismiss = { localCommand = false },
            onSubmitted = { id -> lastInstruction = id; localCommand = false }, localCommand = true)
    }
    if (remoteTranscript) {
        com.dominion.mobile.ui.components.RemoteSpeechDialog(vm, call.sessionId, call.conversationId,
            onDismiss = { remoteTranscript = false },
            onSubmitted = { id -> lastInstruction = id; remoteTranscript = false })
    }
    if (reviewTextWork) {
        val room = state.conversation(call.conversationId)
        val session = room?.sessionId
        val nonces = room?.messages.orEmpty().filter {
            it.sender == com.dominion.mobile.data.model.MessageSender.USER &&
                it.createdAt >= call.startedAt
        }.map { it.clientMessageId ?: it.id }
        if (!session.isNullOrBlank()) com.dominion.mobile.ui.components.VoiceTranscriptDialog(
            vm, call.conversationId, session, nonces, label = "통화 중 채팅 업무 기록",
            onDismiss = { reviewTextWork = false })
        else androidx.compose.material3.AlertDialog(onDismissRequest = { reviewTextWork = false },
            title = { Text("업무 기록") }, text = { Text("본체 대화방 기록을 먼저 동기화하세요.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { reviewTextWork = false }) { Text("닫기") } })
    }
    frameSource?.let { source ->
        com.dominion.mobile.ui.components.CallFrameDialog(vm, call.sessionId, call.conversationId, source,
            onDismiss = { frameSource = null },
            onSubmitted = { id -> lastInstruction = id; frameSource = null })
    }
    if (confirmScreenShare) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmScreenShare = false },
        title = { Text("현재 통화방에 화면을 공유할까요?") },
        text = { Text("선택한 앱 또는 화면이 통화방 참가자에게 전송됩니다. AI의 화면 이해 여부는 본체 참가자 기능에 달려 있습니다. 민감한 정보는 공유하지 마세요. 공유는 언제든 중지할 수 있습니다.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            confirmScreenShare = false
            if (mediaReady && !requestingProjection && !media.sharingScreen) {
                requestingProjection = true
                runCatching {
                    val manager = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                    projectionPermission.launch(manager.createScreenCaptureIntent())
                }.onFailure { requestingProjection = false; context.toast("시스템 화면 공유 요청 실패") }
            }
        }) { Text("공유 범위 선택") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmScreenShare = false }) { Text("취소") } })
    if (requestInstruction) com.dominion.mobile.ui.components.TextPromptDialog(
        title = tr(lang, "통화 중 작업 요청", "Assign work during call"),
        label = tr(lang, "현재 대화방으로 전달할 요청", "Request for the current room"),
        confirmLabel = tr(lang, "본체에 보내기", "Send to engine"),
        singleLine = false,
        onConfirm = { text ->
            val id = vm.sendCallInstruction(call.sessionId, text)
            if (id != null) { lastInstruction = id; requestInstruction = false }
            else context.toast(tr(lang, "요청을 대기열에 넣지 못했습니다.", "Could not queue request"))
        },
        onDismiss = { requestInstruction = false })
}

@Composable
private fun CallAvatar(accent: Color, character: com.dominion.mobile.data.model.Character) {
    val transition = rememberInfiniteTransition(label = "call")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            repeat(3) { i ->
                val phase = (pulse + i / 3f) % 1f
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f * (1f - phase)),
                    radius = size.minDimension * (0.28f + phase * 0.22f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Box(
            Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            AssistantAvatar(accent, character.shape, size = 160.dp, breathing = true, photo = character.avatarPhoto)
        }
    }
}

@Composable
private fun CallControl(
    glyph: Glyph,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, if (active) Color(0xFF111318) else Color.White, size = 23.dp)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun Waveform(active: Boolean, color: Color, level: Float = 0f) {
    val transition = rememberInfiniteTransition(label = "wave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "waveT"
    )
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val bars = 28
        val gap = size.width / bars
        for (i in 0 until bars) {
            val wave = if (active) {
                (0.14f + level.coerceIn(0f, 1f) * 0.86f * kotlin.math.abs(kotlin.math.sin((i * 0.55f) + (t * kotlin.math.PI * 2).toFloat())))
            } else 0.14f
            val h = size.height * wave * 0.7f
            drawLine(
                color = color.copy(alpha = if (active) 0.55f else 0.2f),
                start = Offset(i * gap + gap / 2, size.height / 2 - h / 2),
                end = Offset(i * gap + gap / 2, size.height / 2 + h / 2),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CallVideoView(room: Room, track: VideoTrack, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember(room, track, context) {
        TextureViewRenderer(context).also { room.initVideoRenderer(it) }
    }
    DisposableEffect(track, renderer) {
        track.addRenderer(renderer)
        onDispose {
            runCatching { track.removeRenderer(renderer) }
            renderer.release()
        }
    }
    androidx.compose.runtime.key(room, track) {
        AndroidView(factory = { renderer }, modifier = modifier)
    }
}
