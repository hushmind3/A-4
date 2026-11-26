package com.dominion.mobile.ui.screens.chats

import android.Manifest
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
    onBack: () -> Unit
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
    var elapsed by remember(call.startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(call.sessionId, call.phase) {
        if (call.phase == CallPhase.PEER_PRESENT) {
            while (true) {
                elapsed = System.currentTimeMillis() - call.startedAt
                delay(1000)
            }
        }
    }

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
        if (call.video) {
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
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AssistantAvatar(accent, character.shape, size = 150.dp, breathing = true)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        character.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 연결 준비 필요" else "Video transport not connected",
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
                    Text(if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "나" else "Me", color = Color.White.copy(alpha = 0.75f))
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

            if (!call.video) {
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
                    Waveform(active = false, color = Color.White.copy(alpha = 0.75f))
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
                    glyph = if (call.speaker) Glyph.SPEAKER else Glyph.SPEAKER_OFF,
                    label = if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "스피커" else "Speaker",
                    active = false,
                    onClick = { context.toast(tr(lang, "스피커 전환 연동 필요", "Speaker routing not connected")) }
                )
                CallControl(
                    glyph = Glyph.VIDEO,
                    label = if (call.video) {
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 끄기" else "Video off"
                    } else {
                        if (LocalLang.current == com.dominion.mobile.data.model.Lang.KO) "영상 켜기" else "Video on"
                    },
                    active = false,
                    onClick = { context.toast(tr(lang, "영상통화 연동 필요", "Video transport not connected")) }
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
            AssistantAvatar(accent, character.shape, size = 160.dp, breathing = true)
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
private fun Waveform(active: Boolean, color: Color) {
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
                (0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin((i * 0.55f) + (t * kotlin.math.PI * 2).toFloat())))
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
