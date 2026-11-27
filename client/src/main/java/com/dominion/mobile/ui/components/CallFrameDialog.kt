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
    fun capture() {
        if (busy || submitted) return
        busy = true; error = ""; bytes = null; preview = null
        scope.launch {
            try {
                val media = CallForegroundService.mediaState.value
                check(media.sessionId == callId && vm.state.value.call?.sessionId == callId)
                val track = requireNotNull(if (source == "screen") media.localScreen else media.localVideo)
                val jpeg = CallFrameCapture.capture(track)
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
    StopOnBackground(onDismiss)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (source == "screen") "공유 화면 AI 분석" else "카메라 AI 분석") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 내 영상에서 한 프레임을 읽습니다. 촬영한 이미지와 요청을 확인한 후 현재 본체 대화방에 전송합니다.")
                preview?.let { Image(it.asImageBitmap(), "캡처 미리보기", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
                OutlinedButton(enabled = !busy && !submitted, onClick = ::capture) { Text(if (bytes == null) "현재 화면 캡처" else "다시 캡처") }
                OutlinedTextField(prompt, { prompt = it.take(4000) }, label = { Text("AI에게 물어볼 내용") }, maxLines = 3)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !submitted && bytes != null && prompt.isNotBlank(), onClick = {
                val jpeg = bytes ?: return@TextButton
                val expected = roomSession ?: return@TextButton
                busy = true; submitted = true
                scope.launch {
                    try {
                        val fresh = vm.roomExecution(roomId)
                        check(fresh.sessionId == expected)
                        onSubmitted(vm.sendCallFrame(callId, roomId, expected, prompt, jpeg))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "전송 결과를 확인하지 못했습니다. 대화방에서 먼저 확인하세요." }
                    finally { busy = false }
                }
            }) { Text("본체에 분석 요청") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } })
}
