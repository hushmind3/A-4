package com.dominion.mobile.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var automaticBargeIn by remember { mutableStateOf(false) }
    val bargeAvailable = remember { EngineVoiceAudio.supportsBargeIn() }
    var interruptReview by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var interruptText by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendingNonce by remember { mutableStateOf<String?>(null) }
    var capturedNonces by remember { mutableStateOf<List<String>>(emptyList()) }
    var reviewing by remember { mutableStateOf(false) }
    var reviewSession by remember { mutableStateOf<String?>(null) }
    var interruptAndListen by remember { mutableStateOf(false) }
    var approvals by remember { mutableStateOf<List<com.dominion.mobile.data.remote.EngineApproval>>(emptyList()) }
    var reviewedApproval by remember { mutableStateOf<com.dominion.mobile.data.remote.EngineApproval?>(null) }
    var decisionBusy by remember { mutableStateOf(false) }
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
        if (active || decisionBusy || job?.isActive == true || ready != true || vm.state.value.call != null) return
        active = true
        failure = null
        job = scope.launch {
            try {
                // Bind the dialog to a verified room session, not just a room name.
                // Avoid racing the visible chat's own foreground refresh.
                while (!vm.refreshConversationNow(roomId)) {
                    phase = "대화 동기화 중"
                    delay(1000)
                }
                val session = requireNotNull(vm.roomExecution(roomId).sessionId)
                check(session.isNotBlank())
                if (reviewSession != null) check(reviewSession == session)
                reviewSession = session
                fun ensureRoom() {
                    check(vm.state.value.call == null)
                    val room = requireNotNull(vm.state.value.conversation(roomId))
                    check(room.sessionId == session) { "Room session changed" }
                }
                var interruptedWave: ByteArray? = null
                do {
                    ensureRoom()
                    if (pendingNonce == null) {
                        finishRecording.set(false)
                        phase = "듣는 중"
                        val interrupted = interruptedWave
                        interruptedWave = null
                        val wav = interrupted ?: EngineVoiceAudio.record(context, finishRecording, autoEndpoint = true)
                        ensureRoom()
                        phase = "본체에서 음성 인식 중"
                        transcript = vm.transcribeVoice(wav)
                        ensureRoom()
                        if (interrupted != null) {
                            // Echo/noise can resemble speech. Do not send a false turn automatically.
                            phase = "끼어들기 발화 확인"
                            interruptText = transcript
                            val review = CompletableDeferred<String?>()
                            interruptReview = review
                            val confirmed = try { review.await() } finally { interruptReview = null }
                            if (confirmed.isNullOrBlank()) {
                                if (!autoContinue) break
                                continue
                            }
                            transcript = confirmed
                        }
                        phase = "본체에 요청 전송 중"
                        val nonce = requireNotNull(vm.sendMessage(roomId, transcript))
                        pendingNonce = nonce
                        capturedNonces = (capturedNonces + nonce).distinct()
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
                        approvals = snapshot.approvals.orEmpty().filter { it.actionableIn(session) }
                        if (approvals.isNotEmpty()) {
                            phase = "승인 요청 대기 · 실행 내용을 직접 확인하세요"
                            delay(1500)
                            continue
                        }
                        if (owner.jobStatus == "completed") {
                            val replies = VoiceTurn.replies(room.messages, roomId, owner)
                            if (replies.isNotEmpty()) {
                                answer = replies.joinToString("\n") { it.text }
                                phase = "본체 음성으로 응답 중"
                                // Split very long multi-agent replies independently.
                                playback@ for (reply in replies) {
                                    val chunks = WaveAudio.speechChunks(reply.text)
                                    for (chunk in chunks) {
                                        ensureRoom()
                                        val speech = vm.synthesizeVoice(chunk)
                                        if (automaticBargeIn && bargeAvailable) {
                                            interruptedWave = EngineVoiceAudio.playListening(context, speech)
                                            if (interruptedWave != null) break@playback
                                        } else EngineVoiceAudio.play(context, speech)
                                    }
                                }
                                pendingNonce = null
                                phase = if (interruptedWave != null) "발화 감지 · 응답 중단" else "응답 재생 완료"
                                break
                            }
                            // A completed job without a matching visible message is not
                            // an invented response. Keep its receipt for manual review.
                            phase = "작업 완료 · 일치하는 응답 기록 대기"
                        }
                        delay(1500)
                    }
                } while (autoContinue || interruptedWave != null)
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
    LaunchedEffect(active, interruptAndListen) {
        if (!active && interruptAndListen) {
            interruptAndListen = false
            job?.join()
            begin()
        }
    }
    StopOnBackground { interruptAndListen = false; stop(); onDismiss() }
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
                if (approvals.isNotEmpty()) {
                    Text("대화방 승인 요청 " + approvals.size + "개", style = MaterialTheme.typography.titleSmall)
                    approvals.take(3).forEach { item ->
                        TextButton(enabled = !decisionBusy, onClick = { reviewedApproval = item }) {
                            Text((item.tool ?: "도구") + " · 실행 내용 확인")
                        }
                    }
                }
                Row {
                    Checkbox(checked = autoContinue, onCheckedChange = { autoContinue = it })
                    Text("응답 후 자동으로 다음 발화 듣기", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(checked = automaticBargeIn, enabled = bargeAvailable && !active,
                        onCheckedChange = { automaticBargeIn = it })
                    Text(if (bargeAvailable) "자동 끼어들기 (반향 제거 · 발화 확인)"
                        else "자동 끼어들기 · 기기 반향 제거 미지원",
                        modifier = Modifier.padding(top = 12.dp))
                }
                interruptReview?.let { review ->
                    OutlinedTextField(interruptText, { interruptText = it.take(32000) },
                        label = { Text("새 발화 확인·편집") }, maxLines = 3)
                    Row {
                        TextButton(enabled = interruptText.isNotBlank(), onClick = { review.complete(interruptText.trim()) }) { Text("전송") }
                        TextButton(onClick = { review.complete(null) }) { Text("취소") }
                    }
                }
                if (active && phase == "듣는 중") OutlinedButton(onClick = { finishRecording.set(true) }) { Text("말하기 끝내기") }
                if (active && pendingNonce != null && phase == "본체 음성으로 응답 중") {
                    TextButton(onClick = {
                        interruptAndListen = true
                        stop()
                        pendingNonce = null
                        phase = "응답 읽기 중단 · 다음 발화 준비"
                    }) { Text("응답 끊고 새로 말하기") }
                }
                if (!active && capturedNonces.isNotEmpty() && reviewSession != null) {
                    TextButton(onClick = { reviewing = true }) { Text("이번 음성 대화 기록·요약·기억") }
                }
                if (pendingNonce != null && !active) {
                    Text("이미 접수된 요청은 대화에 남습니다. 다시 시작하면 새 요청을 보내지 않고 기존 요청부터 확인합니다.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { pendingNonce = null; phase = "새 발화 대기" }) { Text("기존 요청은 대화에 남기고 새 발화") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready == true && !active && !decisionBusy, onClick = {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (pendingNonce == null) "음성 대화 시작" else "기존 요청 다시 확인") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (active) { stop(); phase = "일시정지 · 본체 작업 유지" }
                else onDismiss()
            }) { Text(if (active) "듣기·재생 중단" else "닫기") }
        })
    if (reviewing) VoiceTranscriptDialog(vm, roomId, requireNotNull(reviewSession),
        capturedNonces, onDismiss = { reviewing = false })
    reviewedApproval?.let { item ->
        AlertDialog(onDismissRequest = { if (!decisionBusy) reviewedApproval = null },
            title = { Text("본체 도구 실행 확인") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).padding(4.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(item.tool.orEmpty())
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(item.args?.toString().orEmpty())
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !decisionBusy, onClick = {
                    decisionBusy = true
                    scope.launch {
                        try {
                            vm.decideApproval(roomId, item, true)
                            reviewedApproval = null
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { failure = "승인 결과 확인 실패 · 다시 조회하세요." }
                        finally { decisionBusy = false }
                    }
                }) { Text("실행 허용") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !decisionBusy, onClick = { reviewedApproval = null }) { Text("닫기") }
                    TextButton(enabled = !decisionBusy, onClick = {
                        decisionBusy = true
                        scope.launch {
                            try { vm.decideApproval(roomId, item, false); reviewedApproval = null }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { failure = "거부 결과 확인 실패 · 다시 조회하세요." }
                            finally { decisionBusy = false }
                        }
                    }) { Text("거부") }
                }
            })
    }
}
