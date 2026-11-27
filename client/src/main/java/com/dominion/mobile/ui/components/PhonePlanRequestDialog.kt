package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.PhoneNavigationPlan
import com.dominion.mobile.platform.DeviceActions

/** Only the user's chosen app name/package and explicit request leave the device. */
@Composable
fun PhonePlanRequestDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { runCatching { DeviceActions.launchableApps(context) }.getOrDefault(emptyList()) }
    var selected by remember { mutableStateOf<DeviceActions.InstalledApp?>(null) }
    var query by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    val filtered = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("휴대폰 앱 조작 계획") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("기존 6개 앱 자동 선택 또는 실제 설치 앱을 직접 선택할 수 있습니다.")
                TextButton(onClick = { selected = null }) {
                    Text(if (selected == null) "✓ 기존 앱 자동 선택" else "기존 앱 자동 선택")
                }
                selected?.let { Text("선택: " + it.label + " (" + it.packageName + ")") }
                OutlinedTextField(query, { query = it.take(100) }, label = { Text("설치 앱 검색") }, singleLine = true)
                filtered.take(100).forEach { app ->
                    Row {
                        RadioButton(selected = selected == app, onClick = { selected = app })
                        Column(Modifier.padding(top = 9.dp)) {
                            Text(app.label)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (filtered.size > 100) Text("검색어로 범위를 좁히세요.")
                OutlinedTextField(request, { request = it.take(2000) }, label = { Text("검색·탐색 요청") }, maxLines = 4)
                Text("실제 실행 전 모든 단계를 검토하고, 기기 화면에서 각각 승인합니다.")
            }
        },
        confirmButton = { TextButton(enabled = request.isNotBlank(), onClick = {
            val app = selected
            onConfirm(if (app == null) PhoneNavigationPlan.prompt(request)
                else PhoneNavigationPlan.selectedAppPrompt(request, app.packageName, app.label))
        }) { Text("요청 작성") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
