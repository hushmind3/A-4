package com.dominion.mobile.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.PhoneNavigationPlan
import com.dominion.mobile.platform.automation.PhoneNavigationService

@Composable
fun PhoneNavigationDialog(plan: PhoneNavigationPlan, onDismiss: () -> Unit,
    onResult: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val connected by PhoneNavigationService.connected.collectAsState()
    val status by PhoneNavigationService.status.collectAsState()
    var approved by remember(plan) { mutableStateOf(false) }
    var started by remember(plan) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val installed = remember(plan.app) {
        com.dominion.mobile.platform.DeviceActions.launchableApps(context).firstOrNull { it.packageName == plan.app }
    }
    AlertDialog(onDismissRequest = { PhoneNavigationService.stop(); onDismiss() },
        title = { Text("기기 앱 조작 검토") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("대상: " + (installed?.label ?: PhoneNavigationPlan.apps[plan.app] ?: plan.app))
                Text(plan.app, style = MaterialTheme.typography.labelSmall)
                if (installed == null) Text("이 기기에서 실행할 수 있는 앱을 확인하지 못했습니다.", color = MaterialTheme.colorScheme.error)
                Text("선택한 앱에서 각 단계를 화면 위 확인 버튼으로 직접 승인해야 실행합니다. 단계별 승인 제한은 30초, 전체 제한은 5분입니다. 다른 앱·잠금 화면으로 바뀌면 중단합니다.")
                plan.steps.forEachIndexed { index, step ->
                    Text((index + 1).toString() + ". " + when(step.action) {
                        "click" -> "선택: "
                        "search_text" -> "검색 입력: "
                        "scroll_forward" -> "화면 앞으로 스크롤: "
                        "scroll_backward" -> "화면 뒤로 스크롤: "
                        else -> "화면 확인: "
                    } + step.label + (step.value?.let { " → " + it } ?: ""))
                }
                if (!connected) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        .onFailure { error = "접근성 설정 열기 실패" }
                }) { Text("Android 접근성에서 DOMINION 앱 조작 허용") }
                Row {
                    Checkbox(checked = approved, enabled = !started, onCheckedChange = { approved = it })
                    Text("대상과 모든 단계의 기기 실행을 승인합니다.", modifier = Modifier.padding(top = 12.dp))
                }
                if (started) {
                    Text(status)
                    TextButton(onClick = { PhoneNavigationService.stop() }) { Text("조작 중지") }
                    if (onResult != null && status.startsWith("기기 실행 확인") ||
                        onResult != null && status.startsWith("중단")) {
                        TextButton(onClick = { onResult?.invoke("휴대폰 실행 기록 (본체 작업과 별도): " + status) }) { Text("실행 기록을 대화에 넣기") }
                    }
                }
                if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = connected && approved && !started && installed != null,onClick = {
            started = PhoneNavigationService.start(plan)
            if (!started) error = "이미 조작 중이거나 기기가 잠겨 있습니다."
        }) { Text("기기에서 실행") } },
        dismissButton = { TextButton(onClick = { if (started) PhoneNavigationService.stop(); onDismiss() }) { Text("닫기") } })
}
