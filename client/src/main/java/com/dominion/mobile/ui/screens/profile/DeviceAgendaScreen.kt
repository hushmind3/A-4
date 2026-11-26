package com.dominion.mobile.ui.screens.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.AgendaEntry
import com.dominion.mobile.data.AgendaHandoff
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.DeviceAgendaReader
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.components.*
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DeviceAgendaScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalDominionPalette.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    var days by remember { mutableIntStateOf(7) }
    var rows by remember { mutableStateOf<List<AgendaEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf("선택한 일정의 준비물과 이동 계획, 겹치는 일정을 정리해줘.") }
    var agent by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }

    fun load() {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try {
                val today = LocalDate.now(zone)
                rows = DeviceAgendaReader.read(context, today.atStartOfDay(zone).toInstant().toEpochMilli(),
                    today.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli())
                selected = selected.intersect(rows.map { it.key }.toSet())
                loaded = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { rows = emptyList(); selected = emptySet(); loaded = false; error = true }
            finally { busy = false }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load() else context.toast("캘린더 읽기 권한이 필요합니다.")
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "휴대폰 일정", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("캘린더에서 직접 읽습니다. 체크한 일정의 제목과 시간만 AI 업무에 전달합니다.", color = p.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 7, 30).forEach { value ->
                    FilterChipItem(if (value == 1) "오늘" else value.toString() + "일", days == value, onClick = {
                        if (!busy) { days = value; rows = emptyList(); selected = emptySet(); loaded = false }
                    })
                    Spacer(Modifier.width(5.dp))
                }
                TextButton(enabled = !busy, onClick = {
                    if (DeviceAgendaReader.allowed(context)) load()
                    else permission.launch(Manifest.permission.READ_CALENDAR)
                }) { Text(if (loaded) "새로고침" else "읽기") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text("캘린더 조회 실패 · 권한과 기기 설정을 확인하세요.", color = p.danger)
            if (loaded && rows.isEmpty()) Text("선택 기간에 표시된 일정이 없습니다.", color = p.textSecondary)
            Text("선택 " + selected.size + "개 / 최대 50개", style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(rows, key = { it.key }) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.key in selected, onCheckedChange = { checked ->
                        if (!checked) selected = selected - item.key
                        else if (selected.size < 50) selected = selected + item.key
                    })
                    Column(Modifier.weight(1f)) {
                        Text(item.title)
                        Text(item.display(zone), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppTextField(request, { request = it.take(2000) }, label = "AI에게 맡길 일", singleLine = false, maxLines = 3)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.characters, key = { it.id }) { character ->
                    FilterChipItem(character.name, agent == character.id, onClick = { agent = character.id })
                }
            }
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !busy && selected.isNotEmpty() && request.isNotBlank() && state.character(agent) != null,
                onClick = {
                    val details = AgendaHandoff.request(rows, selected, request, zone)
                    vm.createServiceTask("선택 일정 도우미", agent, details, listOf("선택 일정 확인", "요청 수행", "결과 및 근거 확인"), onOpenChat)
                    selected = emptySet()
                }) { Text("선택한 일정만 AI에게 맡기기") }
        }
    }
}
