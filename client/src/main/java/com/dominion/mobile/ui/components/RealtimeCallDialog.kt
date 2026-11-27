package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.remote.MobileCapabilities
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException

@Composable
fun RealtimeCallDialog(vm: AppViewModel, onStart: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val cloud = vm.localExecution
    var capabilities by remember { mutableStateOf<MobileCapabilities?>(null) }
    var checked by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm, refresh) {
        loading = true; capabilities = null; checked = false
        if (cloud) {
            // The Cloud realtime room is created by the account-scoped Worker.
            // Failure to create it is reported by the call service, not hidden.
            loading = false
            return@LaunchedEffect
        }
        try {
            val caps = vm.mobileCapabilities()
            caps.requireFeature("realtime_audio")
            capabilities = caps
            if ("realtime_video" !in caps.features.orEmpty()) video = false
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        finally { loading = false }
    }
    val supported = cloud || capabilities != null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("본체 실시간 AI 통화") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (cloud)
                    "DOMINION Cloud의 실시간 음성 에이전트가 LiveKit 통화방에 접속합니다. 마이크는 스트리밍 전송되고, 서버 에이전트가 음성 인식·응답·중단 처리를 수행합니다."
                    else "본체 AI 에이전트가 실제 통화방에 접속한 뒤 음성을 송출합니다. 모델·도구·기억과 작업 기록은 현재 본체 대화방에 연결됩니다.")
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!loading && !supported) {
                    Text("현재 서버는 실시간 AI 세션 계약을 제공하지 않습니다. 기존 음성 통화와 연속 음성 대화는 그대로 이용할 수 있습니다.")
                    TextButton(onClick = { refresh++ }) { Text("지원 상태 재확인") }
                }
                if (!cloud) Row {
                    Checkbox(video, { video = it }, enabled = supported &&
                        "realtime_video" in capabilities?.features.orEmpty())
                    Text("실시간 카메라 영상 전송", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(checked, { checked = it }, enabled = supported)
                    Text(if (cloud) "Cloud 실시간 음성 에이전트에 마이크 전송 동의"
                        else "본체 AI에 마이크 및 선택한 영상 전송 동의", modifier = Modifier.padding(top = 12.dp))
                }
                if (cloud && checked) Text("최종 연결·에이전트 참가 확인은 통화 화면에서 실제로 검증됩니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = checked && supported && !loading, onClick = { onStart(video) }) {
            Text(if (cloud) "Cloud 실시간 음성 연결" else "실시간 AI 연결")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
