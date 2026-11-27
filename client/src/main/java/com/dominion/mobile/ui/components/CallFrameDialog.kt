package com.dominion.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.dominion.mobile.platform.audio.CallForegroundService
import com.dominion.mobile.platform.audio.CallFrameCapture
import com.dominion.mobile.platform.attachments.CameraCapture
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.dominion.mobile.data.VoiceTurn
import com.dominion.mobile.data.model.MessageStatus
import kotlinx.coroutines.withContext

@Composable
fun CallFrameDialog(vm: AppViewModel, callId: String, roomId: String, source: String,
    onDismiss: () -> Unit, onSubmitted: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var roomSession by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf(if (source == "screen") "공유 화면 내용을 확인하고 설명해줘." else "카메라에 보이는 내용을 확인하고 설명해줘.") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var repeatFrames by remember { mutableStateOf(false) }
    var interval by remember { mutableIntStateOf(15) }
    var running by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var capturedTrack by remember { mutableStateOf<io.livekit.android.room.track.VideoTrack?>(null) }
    fun currentTrack(): io.livekit.android.room.track.VideoTrack {
        val media = CallForegroundService.mediaState.value
        val active = vm.state.value.call
        check(media.sessionId == callId && active?.sessionId == callId && active.conversationId == roomId)
        return requireNotNull(if (source == "screen") media.localScreen else media.localVideo)
    }
    fun verifyScope(expected: String) {
        check(currentTrack() === capturedTrack) { "Capture track changed" }
        check(vm.state.value.conversation(roomId)?.sessionId == expected)
    }
    fun stopRepeating() {
        repeatJob?.cancel()
    }
    fun startRepeating() {
        if (busy || running || submitted) return
        val first = bytes ?: return
        val expected = roomSession ?: return
        val request = prompt.trim().take(3500)
        val pause = interval
        running = true; submitted = true; error = ""; result = ""
        repeatJob = scope.launch {
            try {
                // At most five observations; no queue of stale frames or automatic retries.
                for (index in 1..5) {
                    verifyScope(expected)
                    val snapshot = vm.roomExecution(roomId)
                    check(snapshot.sessionId == expected)
                    check(snapshot.approvals.orEmpty().none { it.actionableIn(expected) }) {
                        "Approval pending"
                    }
                    val frame = if (index == 1) first else CallFrameCapture.capture(currentTrack())
                    verifyScope(expected)
                    if (index > 1) {
                        preview = withContext(Dispatchers.IO) { CameraCapture.thumbnail(frame) }
                    }
                    progress = "관찰 " + index + "/5 · 본체에 전송"
                    val nonce = vm.sendCallFrame(callId, roomId, expected,
                        "연속 관찰 " + index + "/5. 현재 이미지에 실제로 보이는 내용만 근거로 응답하고, 이전 관찰과 비교하세요. 외부 변경은 하지 마세요.\n" + request,
                        frame)
                    withTimeout(15 * 60 * 1000L) {
                        while (true) {
                            verifyScope(expected)
                            if (!vm.refreshConversationNow(roomId)) { delay(1200); continue }
                            val execution = vm.roomExecution(roomId)
                            check(execution.sessionId == expected)
                            check(execution.approvals.orEmpty().none { it.actionableIn(expected) }) {
                                "Approval pending"
                            }
                            val room = requireNotNull(vm.state.value.conversation(roomId))
                            val owner = requireNotNull(VoiceTurn.owner(room.messages, roomId, nonce))
                            check(owner.status != MessageStatus.FAILED &&
                                owner.jobStatus !in setOf("failed","cancelled","needs_review","paused","interrupted","stopped"))
                            val replies = VoiceTurn.replies(room.messages, roomId, owner)
                            if (replies.isNotEmpty()) {
                                result = replies.joinToString("\n") { it.text }.take(1600)
                                progress = "관찰 " + index + "/5 · 응답 확인"
                                break
                            }
                            progress = "관찰 " + index + "/5 · 본체 분석 대기"
                            delay(1500)
                        }
                    }
                    if (index < 5) {
                        progress = "다음 관찰까지 " + pause + "초 · 이전 작업 완료"
                        delay(pause * 1000L)
                    }
                }
                progress = "5회 관찰 완료"
            } catch (e: CancellationException) {
                progress = "관찰 중단 · 이미 접수한 작업은 대화방에 남습니다."
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    error = "응답 제한 시간 초과 · 대화방에서 실제 작업 상태를 확인하세요."
                } else throw e
            } catch (_: Exception) {
                error = "관찰 중단 · 통화·화면 공유·세션 변경 또는 승인·작업 상태를 확인하세요. 자동 재전송하지 않습니다."
            } finally { running = false }
        }
    }
    fun capture() {
        if (busy || submitted) return
        busy = true; error = ""; bytes = null; preview = null
        scope.launch {
            try {
                val media = CallForegroundService.mediaState.value
                check(media.sessionId == callId && vm.state.value.call?.sessionId == callId)
                val track = currentTrack()
                val jpeg = CallFrameCapture.capture(track)
                check(currentTrack() === track)
                capturedTrack = track
                check(CallForegroundService.mediaState.value.sessionId == callId)
                val image = withContext(Dispatchers.IO) { requireNotNull(CameraCapture.thumbnail(jpeg)) }
                val current = vm.roomExecution(roomId)
                check(current.room?.id == roomId && !current.sessionId.isNullOrBlank())
                roomSession = current.sessionId
                preview = image
                bytes = jpeg
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "현재 로컬 영상 프레임이나 본체 대화방을 확인하지 못했습니다." }
            finally { busy = false }
        }
    }
    StopOnBackground { stopRepeating(); onDismiss() }
    DisposableEffect(Unit) { onDispose { stopRepeating() } }
    AlertDialog(onDismissRequest = { if (!busy && !running) onDismiss() },
        title = { Text(if (source == "screen") "공유 화면 AI 분석" else "카메라 AI 분석") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 내 영상에서 한 프레임을 읽습니다. 촬영한 이미지와 요청을 확인한 후 현재 본체 대화방에 전송합니다.")
                preview?.let { Image(it.asImageBitmap(), "캡처 미리보기", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
                OutlinedButton(enabled = !busy && !submitted, onClick = ::capture) { Text(if (bytes == null) "현재 화면 캡처" else "다시 캡처") }
                OutlinedTextField(prompt, { prompt = it.take(3500) }, label = { Text("AI에게 물어볼 내용") },
                    enabled = !running && !submitted, maxLines = 3)
                Row {
                    Checkbox(checked = repeatFrames, enabled = !busy && !submitted && !running,
                        onCheckedChange = { repeatFrames = it })
                    Text("연속 관찰 5회", modifier = Modifier.padding(top = 12.dp))
                }
                if (repeatFrames) {
                    Text("현재 영상의 새 프레임을 본체에 반복 전송·저장합니다. 앞선 응답이 끝난 뒤에만 다음 프레임을 전송합니다. 중단 또는 백그라운드 전환 시 더 이상 전송하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        listOf(5, 15, 30).forEach { seconds ->
                            FilterChip(selected = interval == seconds, enabled = !running && !submitted,
                                onClick = { interval = seconds }, label = { Text(seconds.toString() + "초") })
                        }
                    }
                    if (progress.isNotBlank()) Text(progress)
                    if (result.isNotBlank()) Text(result, maxLines = 5)
                }
                if (busy || running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !running && !submitted && bytes != null && prompt.isNotBlank(), onClick = {
                if (repeatFrames) { startRepeating(); return@TextButton }
                val jpeg = bytes ?: return@TextButton
                val expected = roomSession ?: return@TextButton
                busy = true; submitted = true
                scope.launch {
                    try {
                        val fresh = vm.roomExecution(roomId)
                        check(fresh.sessionId == expected)
                        verifyScope(expected)
                        onSubmitted(vm.sendCallFrame(callId, roomId, expected, prompt, jpeg))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "전송 결과를 확인하지 못했습니다. 대화방에서 먼저 확인하세요." }
                    finally { busy = false }
                }
            }) { Text(if (repeatFrames) "5회 관찰 시작" else "본체에 분석 요청") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = {
            if (running) stopRepeating() else onDismiss()
        }) { Text(if (running) "관찰 중지" else "닫기") } })
}
