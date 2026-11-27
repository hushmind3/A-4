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
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.platform.audio.*
import com.dominion.mobile.ui.AppViewModel
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** One assistant turn at a time on the existing call track and native room.
 * No remote participant is recorded and no second microphone is opened.
 */
@Composable
fun CallAssistantDialog(vm: AppViewModel, callId: String, roomId: String,
    onDismiss: () -> Unit, onSpeechLevel: (Float) -> Unit, onSubmitted: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media by CallForegroundService.mediaState.collectAsState()
    var consent by remember { mutableStateOf(false) }
    var vision by remember { mutableStateOf(false) }
    var autoBargeIn by remember { mutableStateOf(false) }
    var reviewText by remember { mutableStateOf("") }
    var speechReview by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("준비") }
    var transcript by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<String?>(null) }
    var approvals by remember { mutableStateOf<List<com.dominion.mobile.data.remote.EngineApproval>>(emptyList()) }
    var reviewed by remember { mutableStateOf<com.dominion.mobile.data.remote.EngineApproval?>(null) }
    var deciding by remember { mutableStateOf(false) }
    var skipSpeech by remember { mutableStateOf(false) }
    var playback by remember { mutableStateOf<Job?>(null) }
    val stopCapture = remember { AtomicBoolean(false) }

    fun valid(expected: String): Boolean {
        val current = vm.state.value
        val call = current.call
        val m = CallForegroundService.mediaState.value
        return call?.sessionId == callId && call.conversationId == roomId &&
            call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT) &&
            !call.muted && current.conversation(roomId)?.sessionId == expected &&
            m.sessionId == callId && m.room?.remoteParticipants?.isEmpty() == true
    }
    fun stop() { stopCapture.set(true); work?.cancel() }
    fun begin() {
        if (!consent || busy || work?.isActive == true) return
        busy = true; error = ""
        val useVision = vision
        val useBargeIn = autoBargeIn
        work = scope.launch {
            try {
                val capabilities = vm.voiceStatus()
                check(capabilities.whisper?.available == true && capabilities.piper?.available == true)
                withTimeout(15_000L) {
                    while (!vm.refreshConversationNow(roomId)) delay(300)
                }
                val expected = requireNotNull(vm.roomExecution(roomId).sessionId)
                check(session == null || session == expected)
                session = expected
                check(valid(expected))
                coroutineScope {
                    val watchdog = launch {
                        while (isActive) {
                            check(valid(expected)) { "Call or participant changed" }
                            delay(100)
                        }
                    }
                    try {
                        var interruptedWave: ByteArray? = null
                        fun microphone(): LocalAudioTrack =
                            CallForegroundService.mediaState.value.room?.localParticipant
                                ?.getTrackPublication(Track.Source.MICROPHONE)
                                ?.takeUnless { it.muted }?.track as? LocalAudioTrack
                                ?: error("Local microphone unavailable")
                        fun sameTrack(track: LocalAudioTrack) = valid(expected) &&
                            CallForegroundService.mediaState.value.room?.localParticipant
                                ?.getTrackPublication(Track.Source.MICROPHONE)
                                ?.takeUnless { it.muted }?.track === track
                        while (isActive) {
                            check(valid(expected))
                            if (pending == null) {
                                val track = microphone()
                                stopCapture.set(false)
                                phase = "듣는 중"
                                val interruption = interruptedWave
                                interruptedWave = null
                                val wav = interruption ?: RemoteSpeechCapture.capture(track, stopCapture,
                                    valid = { sameTrack(track) }, onProgress = {}, autoEndpoint = true)
                                check(valid(expected))
                                phase = "본체 음성 인식 중"
                                transcript = vm.transcribeCallVoice(callId, roomId, expected, wav)
                                check(valid(expected))
                                if (interruption != null) {
                                    phase = "끼어들기 발화 확인"
                                    reviewText = transcript
                                    val review = CompletableDeferred<String?>()
                                    speechReview = review
                                    val confirmed = try { review.await() } finally { speechReview = null }
                                    if (confirmed.isNullOrBlank()) continue
                                    transcript = confirmed
                                    check(valid(expected))
                                }
                                val nonce = if (useVision) {
                                    val video = requireNotNull(CallForegroundService.mediaState.value.localVideo)
                                    val frame = CallFrameCapture.capture(video)
                                    check(valid(expected) && CallForegroundService.mediaState.value.localVideo === video)
                                    vm.sendCallFrame(callId, roomId, expected, transcript.take(4000), frame)
                                } else requireNotNull(vm.sendCallInstruction(callId, transcript))
                                pending = nonce; onSubmitted(nonce); answer = ""
                            }
                            val nonce = requireNotNull(pending)
                            while (isActive) {
                                check(valid(expected))
                                if (!vm.refreshConversationNow(roomId)) { phase = "동기화 중"; delay(1000); continue }
                                val execution = vm.roomExecution(roomId)
                                check(execution.sessionId == expected)
                                val owner = requireNotNull(VoiceTurn.owner(
                                    requireNotNull(vm.state.value.conversation(roomId)).messages, roomId, nonce))
                                check(owner.status != MessageStatus.FAILED &&
                                    owner.jobStatus !in setOf("failed","cancelled","paused","interrupted","stopped","needs_review"))
                                approvals = execution.approvals.orEmpty().filter { it.actionableIn(expected) }
                                if (approvals.isNotEmpty()) {
                                    phase = "본체 도구 승인 대기"
                                    delay(1500); continue
                                }
                                val replies = VoiceTurn.replies(
                                    requireNotNull(vm.state.value.conversation(roomId)).messages, roomId, owner)
                                if (replies.isEmpty()) { phase = "본체 작업·응답 대기"; delay(1200); continue }
                                answer = replies.joinToString("\n\n") { it.text }
                                phase = "본체 음성 응답 중"
                                skipSpeech = false
                                val reader = launch {
                                    for (chunk in replies.flatMap { WaveAudio.speechChunks(it.text) }) {
                                        check(valid(expected))
                                        val speech = vm.synthesizeCallVoice(callId, roomId, expected, chunk)
                                        check(valid(expected))
                                        if (useBargeIn) {
                                            val track = microphone()
                                            stopCapture.set(false)
                                            val interrupted = RemoteSpeechCapture.playWithBargeIn(track, stopCapture,
                                                valid = { sameTrack(track) },
                                                onBargeIn = { phase = "끼어들기 감지 · 듣는 중" }) {
                                                EngineVoiceAudio.play(context, speech, communication = true,
                                                    existingCallFocus = true, onSpeechLevel = onSpeechLevel)
                                            }
                                            if (interrupted != null) { interruptedWave = interrupted; break }
                                        } else EngineVoiceAudio.play(context, speech, communication = true,
                                            existingCallFocus = true, onSpeechLevel = onSpeechLevel)
                                    }
                                }
                                playback = reader
                                try { reader.join() } finally { playback = null }
                                if (skipSpeech) phase = "응답 읽기 중단 · 다음 발화"
                                pending = null
                                // Allow output tail to decay before the next capture.
                                if (interruptedWave == null) delay(400)
                                break
                            }
                        }
                    } finally { watchdog.cancelAndJoin() }
                }
            } catch (_: NoSpeechDetected) { phase = "발화 없음 · 다시 시작 가능" }
            catch (e: TimeoutCancellationException) { error = "대화 동기화 시간 초과" }
            catch (e: CancellationException) { phase = "일시정지 · 접수한 작업은 유지"; throw e }
            catch (_: Exception) { error = "통화·본체·음성 또는 작업 상태 변경 · 대화에서 확인하세요." }
            finally { speechReview?.cancel(); speechReview = null; busy = false; onSpeechLevel(0f) }
        }
    }

    StopOnBackground { stop(); onDismiss() }
    DisposableEffect(Unit) { onDispose { stop() } }
    AlertDialog(onDismissRequest = { stop(); onDismiss() }, title = { Text("연속 AI 음성·영상 대화") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 내 마이크 발화를 감지해 본체 작업으로 보내고 정확히 연결된 완료 응답을 읽습니다. 다른 참가자가 접속하면 중단합니다. 발화와 응답은 교대합니다.")
                Row {
                    Checkbox(consent, { consent = it }, enabled = !busy)
                    Text("내 음성을 본체로 전송하고 자동으로 작업 요청", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(vision, { vision = it }, enabled = !busy && media.localVideo != null)
                    Text("각 발화에 현재 내 카메라 이미지 첨부", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(autoBargeIn, { autoBargeIn = it }, enabled = !busy)
                    Text("재생 중 발화 감지로 응답 끊기 · 전사 확인 후 전송",
                        modifier = Modifier.padding(top = 12.dp))
                }
                if (autoBargeIn) Text("스피커 반향이 감지될 수 있어 끼어들기 발화는 전송 전 확인합니다.",
                    style = MaterialTheme.typography.bodySmall)
                Text(phase)
                speechReview?.let { review ->
                    OutlinedTextField(reviewText, { reviewText = it.take(32000) },
                        label = { Text("끼어들기 발화 확인") }, maxLines = 3)
                    Row {
                        TextButton(enabled = reviewText.isNotBlank(), onClick = { review.complete(reviewText.trim()) }) { Text("전송") }
                        TextButton(onClick = { review.complete(null) }) { Text("취소") }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (transcript.isNotBlank()) Text("나: " + transcript.take(900))
                if (answer.isNotBlank()) Text("AI: " + answer.take(1400))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (pending != null && !busy) {
                    Text("이미 접수된 요청은 재전송하지 않고 다시 조회합니다.")
                    TextButton(onClick = { pending = null }) { Text("기존 작업은 채팅에 두고 새 발화") }
                }
                if (busy && phase in setOf("듣는 중", "끼어들기 감지 · 듣는 중")) TextButton(onClick = { stopCapture.set(true) }) { Text("발화 종료") }
                if (busy && playback != null) TextButton(onClick = {
                    skipSpeech = true; playback?.cancel()
                }) { Text("응답 끊고 다음 발화") }
                approvals.take(5).forEach { approval ->
                    TextButton(enabled = !deciding, onClick = { reviewed = approval }) {
                        Text((approval.tool ?: "도구") + " · 승인 내용 확인")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = consent && !busy && (!vision || media.localVideo != null),
            onClick = ::begin) { Text(if (pending == null) "시작" else "기존 응답 확인") } },
        dismissButton = { TextButton(onClick = {
            if (busy) stop() else onDismiss()
        }) { Text(if (busy) "중단" else "닫기") } })

    reviewed?.let { item ->
        fun decide(allow: Boolean) {
            if (deciding) return
            val expected = session ?: return
            deciding = true
            scope.launch {
                try {
                    check(valid(expected) && item.actionableIn(expected))
                    vm.decideApproval(roomId, item, allow)
                    reviewed = null
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "승인 결과 확인 실패 · 본체에서 다시 확인하세요." }
                finally { deciding = false }
            }
        }
        AlertDialog(onDismissRequest = { if (!deciding) reviewed = null },
            title = { Text("본체 도구 실행 확인") },
            text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(item.tool.orEmpty())
                androidx.compose.foundation.text.selection.SelectionContainer { Text(item.args?.toString().orEmpty()) }
            } },
            confirmButton = { TextButton(enabled = !deciding && busy, onClick = { decide(true) }) { Text("실행 허용") } },
            dismissButton = { Row {
                TextButton(enabled = !deciding && busy, onClick = { decide(false) }) { Text("거부") }
                TextButton(enabled = !deciding, onClick = { reviewed = null }) { Text("닫기") }
            } })
    }
}
