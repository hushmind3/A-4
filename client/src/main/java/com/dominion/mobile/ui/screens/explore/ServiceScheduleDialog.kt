package com.dominion.mobile.ui.screens.explore

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.ReminderTiming
import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ServiceScheduleDraft(val title: String, val agent: String, val request: String)

@Composable
fun ServiceScheduleDialog(vm: AppViewModel, draft: ServiceScheduleDraft, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.of("Asia/Seoul") }
    var due by remember { mutableStateOf(ZonedDateTime.now(zone).plusDays(1).withHour(9).withMinute(0)
        .withSecond(0).withNano(0).toInstant().toEpochMilli()) }
    var repeat by remember { mutableStateOf(Repeat.WEEKLY) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("정기 조사 예약") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(draft.title)
                Text("휴대폰에서 선택한 주기에 본체 작업을 접수합니다. 기기·서버 연결 상황에 따라 지연될 수 있습니다.")
                TextButton(enabled = !saving, onClick = {
                    val local = Instant.ofEpochMilli(due).atZone(zone)
                    DatePickerDialog(context, { _, year, month, day ->
                        val chosen = java.time.LocalDate.of(year, month + 1, day)
                        TimePickerDialog(context, { _, hour, minute ->
                            due = chosen.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        }, local.hour, local.minute, true).show()
                    }, local.year, local.monthValue - 1, local.dayOfMonth).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) { Text(ReminderTiming.format(due, zone.id)) }
                Repeat.entries.forEach { option ->
                    Row {
                        RadioButton(selected = option == repeat, enabled = !saving, onClick = { repeat = option })
                        Text(option.ko, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (saving) Text("저장 요청 중 · 실패 시 알림 및 일정 목록을 확인하세요.")
            }
        },
        confirmButton = { TextButton(enabled = !saving && due > System.currentTimeMillis() && draft.request.length <= 3000, onClick = {
            saving = true
            vm.saveSchedule(null, draft.title, "생활 서비스 정기 조사", draft.agent, repeat, due, zone.id, draft.request) {
                onSaved()
            }
        }) { Text("예약 저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
