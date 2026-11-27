package com.dominion.mobile.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.VoiceTurn
import com.dominion.mobile.data.WaveAudio
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.platform.audio.EngineVoiceAudio
import com.dominion.mobile.platform.audio.NoSpeechDetected
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun VoiceConversationDialog(vm: AppViewModel, roomId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var phase by remember { mutableStateOf("준비 중") }
    var transcript by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(false) }
    var autoContinue by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendingNonce by remember { mutableStateOf<String?>(null) }
    val finishRecording = remember { AtomicBoolean(false) }

    LaunchedEffect(vm) {
        try {
            val capabilities = vm.voiceStatus()
            ready = capabilities.whisper?.available == true && capabilities.piper?.available == true
            phase = if (ready == true) "준비 완료" else "본체 Whisper 또는 Piper 준비 필요"
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { ready = false; phase = "본체 음성 상태 조회 실패" }
    }

    fun stop() {
        finishRecording.set(true)
        job?.cancel()
        // Do not clear pendingNonce: a request might already be executing on the engine.
    }

    fun begin() {
        if (active || job?.isActive == true || ready != true || vm.state.value.call != null) return
        active = true
        failure = null
        job = scope.launch {
            try {
                // Bind the dialog to a verified room session, not just a room name.
                vm.refreshConversationNow(roomId)
                val session = requireNotNull(vm.roomExecution(roomId).sessionId)
                check(session.isNotBlank())
                fun ensureRoom() {
                    check(vm.state.value.call == null)
                    val room = requireNotNull(vm.state.value.conversation(roomId))
                    check(room.sessionId == session) { "Room session changed" }
                }
                do {
                    ensureRoom()
                    if (pendingNonce == null) {
                        finishRecording.set(false)
                        phase = "듣는 중"
                        val wav = EngineVoiceAudio.record(context, finishRecording, autoEndpoint = true)
                        ensureRoom()
                        phase = "본체에서 음성 인식 중"
                        transcript = vm.transcribeVoice(wav)
                        ensureRoom()
                        phase = "본체에 요청 전송 중"
                        pendingNonce = requireNotNull(vm.sendMessage(roomId, transcript))
                        answer = ""
                    }
                    val nonce = requireNotNull(pendingNonce)
                    // Long jobs have no fixed one-minute cutoff. Leaving the voice UI
                    // never cancels or automatically resubmits a native job.
                    while (true) {
                        ensureRoom()
                        val fresh = vm.refreshConversationNow(roomId)
                        if (!fresh) { phase = "대화 동기화 재시도 중"; delay(1800); continue }
                        val snapshot = vm.roomExecution(roomId)
                        check(snapshot.sessionId == session)
                        ensureRoom()
                        val room = requireNotNull(vm.state.value.conversation(roomId))
                        val owner = VoiceTurn.owner(room.messages, roomId, nonce)
                            ?: error("Request no longer available")
                        if (owner.status == MessageStatus.FAILED) error("Request not confirmed")
                        phase = when (owner.jobStatus) {
                            "queued" -> "본체 작업 대기"
                            "running" -> "본체 작업 실행 중"
                            "completed" -> "완성된 응답 확인 중"
                            else -> "본체 접수 확인 중"
                        }
                        if (owner.jobStatus in setOf("failed", "cancelled", "needs_review", "interrupted", "paused"))
                            error("Job needs review in chat")
                        if (snapshot.approvals.orEmpty().isNotEmpty()) {
                            phase = "승인 요청 대기 · 대화 화면에서 확인"
                        }
                        if (owner.jobStatus == "completed") {
                            val replies = VoiceTurn.replies(room.messages, roomId, owner)
                            if (replies.isNotEmpty()) {
                                answer = replies.joinToString("\n") { it.text }
                                phase = "본체 음성으로 응답 중"
                                // Split very long multi-agent replies independently.
                                for (reply in replies) {
                                    val chunks = WaveAudio.speechChunks(reply.text)
                                    for (chunk in chunks) {
                                        ensureRoom()
                                        EngineVoiceAudio.play(context, vm.synthesizeVoice(chunk))
                                    }
                                }
                                pendingNonce = null
                                phase = "응답 재생 완료"
                                break
                            }
                            // A completed job without a matching visible message is not
                            // an invented response. Keep its receipt for manual review.
                            phase = "작업 완료 · 일치하는 응답 기록 대기"
                        }
                        delay(1500)
                    }
                } while (autoContinue)
            } catch (e: CancellationException) { throw e }
            catch (_: NoSpeechDetected) { phase = "음성이 감지되지 않아 일시정지" }
            catch (_: Exception) {
                failure = if (pendingNonce != null)
                    "요청 상태 또는 음성 재생을 확인하지 못했습니다. 대화에서 실제 작업을 확인하거나 같은 요청을 다시 조회하세요."
                else "녹음·본체 연결·음성 변환을 확인하세요."
            } finally { active = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else failure = "마이크 권한이 필요합니다."
    }
    StopOnBackground { stop(); onDismiss() }
    DisposableEffect(Unit) { onDispose { stop() } }

    AlertDialog(onDismissRequest = { stop(); onDismiss() },
        title = { Text("본체 연속 음성 대화") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("시작하면 발화 종료 후 현재 방에 실제 요청을 보내고, 본체 답변을 음성으로 재생합니다. 녹음과 재생은 교대로 진행됩니다.",
                    style = MaterialTheme.typography.bodySmall)
                Text(phase, style = MaterialTheme.typography.titleSmall)
                if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (transcript.isNotBlank()) Text("나: " + transcript.take(600), maxLines = 4)
                if (answer.isNotBlank()) Text("AI: " + answer.take(900), maxLines = 5)
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row {
                    Checkbox(checked = autoContinue, onCheckedChange = { autoContinue = it })
                    Text("응답 후 자동으로 다음 발화 듣기", modifier = Modifier.padding(top = 12.dp))
                }
                if (active && phase == "듣는 중") OutlinedButton(onClick = { finishRecording.set(true) }) { Text("말하기 끝내기") }
                if (pendingNonce != null && !active) {
                    Text("이미 접수된 요청은 대화에 남습니다. 다시 시작하면 새 요청을 보내지 않고 기존 요청부터 확인합니다.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { pendingNonce = null; phase = "새 발화 대기" }) { Text("기존 요청은 대화에 남기고 새 발화") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready == true && !active, onClick = {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (pendingNonce == null) "음성 대화 시작" else "기존 요청 다시 확인") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (active) { stop(); phase = "일시정지 · 본체 작업 유지" }
                else onDismiss()
            }) { Text(if (active) "듣기·재생 중단" else "닫기") }
        })
}
