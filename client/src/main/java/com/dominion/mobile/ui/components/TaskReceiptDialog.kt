package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.remote.TaskReceipt
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException

@Composable
fun TaskReceiptDialog(vm: AppViewModel, id: String, onDismiss: () -> Unit) {
    var receipt by remember(id) { mutableStateOf<TaskReceipt?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var refreshing by remember(id) { mutableStateOf(true) }
    var revision by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, revision) {
        refreshing = true
        failed = false
        try { receipt = vm.taskReceipt(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { refreshing = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("본체 업무 결과") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text("조회 실패 · 아래에 기록이 있다면 이전 조회 결과입니다.", color = MaterialTheme.colorScheme.error)
                receipt?.let { record ->
                    Text("작업 상태: " + record.status)
                    Text("조회: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.checkedAt)))
                    Text("본체 작업 ID: " + record.jobId, style = MaterialTheme.typography.labelSmall)
                    record.reason?.let { Text("종료 사유: " + it) }
                    record.error?.let { SelectionContainer { Text(it) } }
                    if (record.outputs.isEmpty()) Text("아직 본체에 저장된 응답 결과가 없습니다.")
                    record.outputs.forEach { output ->
                        HorizontalDivider()
                        Text(output.name ?: output.agentId ?: "에이전트", style = MaterialTheme.typography.titleSmall)
                        Text(when {
                            output.evidenceCount == null -> "실행 근거 수 미제공"
                            output.evidenceCount > 0 -> "본체 기록 실행 근거 " + output.evidenceCount + "개"
                            else -> "본체 기록 실행 근거 없음"
                        }, style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(output.content.orEmpty()) }
                        Text("응답 ID: " + output.messageId, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("실행 근거 개수는 본체 기록이며, 행정기관 접수나 외부 정보의 정확성 자체를 증명하지는 않습니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = !refreshing, onClick = { revision++ }) { Text("다시 조회") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
