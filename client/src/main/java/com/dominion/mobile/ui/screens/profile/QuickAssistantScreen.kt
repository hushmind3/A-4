package com.dominion.mobile.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.components.*
import com.dominion.mobile.ui.theme.LocalDominionPalette

@Composable
fun QuickAssistantScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit,
    onChat: (String) -> Unit, onSettings: () -> Unit) {
    val p = LocalDominionPalette.current
    val connection by vm.connection.collectAsStateWithLifecycle()
    val rooms = state.conversations.filter { state.character(it.characterId) != null }
    val suggested = com.dominion.mobile.data.NextActions.build(state, System.currentTimeMillis())
        .firstOrNull { it.roomId != null && state.conversation(it.roomId) != null }
    var selected by remember { mutableStateOf((rooms.firstOrNull { it.id == "mobile-assistant" || it.id=="assistant" }?:rooms.firstOrNull())?.id.orEmpty()) }
    var voice by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "빠른 AI 비서", onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("필요한 일을 비서에게 요청하세요.", color = p.textSecondary)
            Text("대화방을 선택하고 음성 대화를 시작하거나 요청을 작성하세요. 시스템 호출의 화면·문자 정보는 자동 수집하지 않습니다.")
            if (rooms.isEmpty()) OutlinedButton(onClick = onSettings) { Text("계정 연결") }
            suggested?.let { action ->
                TextButton(onClick = { selected = requireNotNull(action.roomId) }) {
                    Text("추천 대화 · " + action.title)
                }
                Text(action.reason, style = MaterialTheme.typography.labelSmall)
            }
            AppTextField(draft, { draft = it.take(32000) }, label = "작업 요청", singleLine = false, maxLines = 4)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(rooms, key = { it.id }) { room ->
                Row {
                    RadioButton(selected = selected == room.id, onClick = { selected = room.id })
                    Text(room.title(state.characters), modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = state.conversation(selected) != null && state.call == null,
                onClick = { voice = selected }) { Text("음성 대화") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = state.conversation(selected) != null,
                onClick = {
                    val room = state.conversation(selected) ?: return@OutlinedButton
                    if (draft.isNotBlank()) vm.setDraft(selected, listOf(room.draft, draft).filter { it.isNotBlank() }.joinToString("\n"))
                    onChat(selected)
                }) { Text("대화방 열기 · 요청 검토") }
        }
    }
    voice?.let { roomId ->
        key(roomId) { if(vm.localExecution)MobileVoiceConversationDialog(vm,roomId,onDismiss={voice=null})
            else VoiceConversationDialog(vm, roomId, onDismiss = { voice = null }) }
    }
}
