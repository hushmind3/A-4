package com.dominion.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.NextActions
import com.dominion.mobile.data.NextActionKind
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.delay

@Composable
fun NextActionPanel(state: AppState, onOpenChat: (String) -> Unit,
    onTasks: () -> Unit, onSchedules: () -> Unit) {
    val p = LocalDominionPalette.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(60_000L) }
    }
    val actions = remember(state, now) { NextActions.build(state, now) }
    if (actions.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(18.dp)).background(p.cardSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("지금 챙길 일", style = MaterialTheme.typography.titleMedium)
        Text("저장된 대화·본체 작업·일정 기준", color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
        actions.forEach { action ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall)
                    Text(action.reason, color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    when {
                        action.roomId != null -> onOpenChat(action.roomId)
                        action.kind == NextActionKind.UPCOMING -> onSchedules()
                        else -> onTasks()
                    }
                }) { Text("확인") }
            }
            HorizontalDivider()
        }
        Text("추천을 선택해야 해당 화면이 열립니다. 새 요청은 자동 실행하지 않습니다.",
            color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
