package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.VoiceTurn
import com.dominion.mobile.data.WaveAudio
import com.dominion.mobile.data.model.CallPhase
import com.dominion.mobile.platform.audio.CallForegroundService
import com.dominion.mobile.platform.audio.EngineVoiceAudio
import com.dominion.mobile.ui.AppViewModel
import io.livekit.android.room.track.Track
import kotlinx.coroutines.*

/** Read only the exact completed job response, locally on the existing call audio route. */
@Composable
fun CallResultSpeechDialog(vm: AppViewModel, callId: String, roomId: String, nonce: String,
    onDismiss: () -> Unit, onSpeechLevel: (Float) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val media by CallForegroundService.mediaState.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    val originalSession = remember { state.conversation(roomId)?.sessionId }
    fun safe(): Boolean {
        val s = vm.state.value
        val call = s.call
        val m = CallForegroundService.mediaState.value
        return originalSession != null && s.conversation(roomId)?.sessionId == originalSession &&
            call?.sessionId == callId && call.conversationId == roomId && call.muted &&
            call.phase in setOf(CallPhase.WAITING_PEER,CallPhase.PEER_PRESENT) &&
            m.sessionId == callId &&
            m.room?.localParticipant?.isMicrophoneEnabled == false
    }
    LaunchedEffect(Unit) {
        try { ready = vm.voiceStatus().piper?.available == true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "본체 음성 상태 조회 실패" }
    }
    fun begin() {
        if (busy || !ready || !safe()) return
        busy = true; answer = ""; error = ""; progress = "작업 결과 확인"
        work = scope.launch {
            try {
                coroutineScope {
                    val watchdog = launch {
                        while (isActive) {
                            check(safe()) { "Call or microphone state changed" }
                            delay(100)
                        }
                    }
                    try {
                        val replies = withTimeout(10_000L) {
                            while (!vm.refreshConversationNow(roomId)) delay(300)
                            check(safe() && vm.roomExecution(roomId).sessionId == originalSession)
                            val room = requireNotNull(vm.state.value.conversation(roomId))
                            val owner = requireNotNull(VoiceTurn.owner(room.messages,roomId,nonce))
                            VoiceTurn.replies(room.messages,roomId,owner)
                        }
                        check(replies.isNotEmpty()) { "No completed matching response" }
                        answer = replies.joinToString("\n\n") { it.text }
                        val chunks = replies.flatMap { WaveAudio.speechChunks(it.text) }
                        for ((i,chunk) in chunks.withIndex()) {
                            check(safe())
                            progress = "음성 합성·재생 " + (i+1) + "/" + chunks.size
                            val wav = vm.synthesizeCallVoice(callId, roomId, requireNotNull(originalSession), chunk)
                            check(safe())
                            // The active LiveKit call already owns its communication route/focus.
                            EngineVoiceAudio.play(context,wav,communication=true,existingCallFocus=true,
                                onSpeechLevel = onSpeechLevel)
                        }
                        progress = "재생 완료"
                    } finally { watchdog.cancelAndJoin() }
                }
            } catch (e: TimeoutCancellationException) {
                error = "동기화 제한 시간 초과 · 다시 조회하세요."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                error = "완료 응답 미확인, 통화·음소거 변경 또는 음성 재생 실패"
            } finally { busy = false }
        }
    }
    StopOnBackground { work?.cancel(); onDismiss() }
    DisposableEffect(Unit) { onDispose { work?.cancel() } }
    AlertDialog(onDismissRequest = { work?.cancel(); onDismiss() }, title = { Text("통화 중 업무 결과 듣기") },
        text = {
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 통화방의 선택한 작업과 정확히 일치하는 완료 답변만 읽습니다. 내 기기에서 재생하며 상대방에게 음성을 송출하지 않습니다.")
                val muted = state.call?.muted == true && media.room?.localParticipant?.isMicrophoneEnabled == false
                if (!muted) Text("마이크를 먼저 음소거하세요. 재생 중 음소거 해제·통화 변경 시 즉시 중단합니다.")
                if (!ready) Text("본체 Piper 준비 확인 필요")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (progress.isNotBlank()) Text(progress)
                if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
                if (answer.isNotBlank()) Text(answer)
            }
        },
        confirmButton = { TextButton(enabled = ready && !busy && state.call?.muted == true &&
            media.room?.localParticipant?.isMicrophoneEnabled == false, onClick = ::begin) { Text("결과 듣기") } },
        dismissButton = { TextButton(onClick = { work?.cancel(); onDismiss() }) { Text(if(busy) "재생 중단" else "닫기") } })
}
