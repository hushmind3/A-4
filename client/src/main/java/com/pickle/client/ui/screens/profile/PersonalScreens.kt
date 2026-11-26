package com.pickle.client.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.Repeat
import com.pickle.client.data.model.TaskItem
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.CheckCircle
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphButton
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.PickleBottomSheet
import com.pickle.client.ui.components.PickleChip
import com.pickle.client.ui.components.PickleTextField
import com.pickle.client.ui.components.PickleTopBar
import com.pickle.client.ui.components.PrimaryButton
import com.pickle.client.ui.components.PrimaryDialog
import com.pickle.client.ui.components.SearchField
import com.pickle.client.ui.components.SectionLabel
import com.pickle.client.ui.components.SheetRow
import com.pickle.client.ui.components.SheetTitle
import com.pickle.client.ui.components.TextPromptDialog
import com.pickle.client.ui.components.toast
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import com.pickle.client.util.asRelative

@Composable
fun MemoriesScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var selectedCharacter by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val filtered = state.memories.filter { it.text.contains(query, true) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        PickleTopBar(
            title = tr(lang, "기억", "Memories"),
            subtitle = tr(lang, "${state.memories.size}개의 기억을 저장했어요", "${state.memories.size} memories saved"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, tr(lang, "기억 검색", "Search memories"))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.blue.copy(alpha = 0.08f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.MEMORY, p.blue, size = 19.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            tr(lang, "피클은 대화에서 중요한 내용을 기억해요. 직접 추가할 수도 있어요.", "Your Pickle remembers what matters. You can add memories too."),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { memory ->
                val character = state.character(memory.characterId)
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JaumAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB, size = 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(character?.name ?: "Pickle", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(memory.createdAt.asRelative(lang), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "⋯",
                            color = p.textTertiary,
                            modifier = Modifier.clickable { deleteTarget = memory.id }.padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(memory.text, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(memory.source, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    com.pickle.client.ui.components.EmptyState(
                        glyph = Glyph.MEMORY,
                        title = tr(lang, "기억이 없어요", "No memories yet"),
                        body = tr(lang, "대화에서 기억에 저장하거나 직접 추가해보세요.", "Save from a chat or add one yourself."),
                        actionLabel = tr(lang, "기억 추가", "Add memory"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        PickleBottomSheet(onDismiss = { showAdd = false }) {
            var text by remember { mutableStateOf("") }
            SheetTitle(tr(lang, "기억 추가", "Add a memory"), tr(lang, "어떤 피클이 기억할까요?", "Which Pickle should remember?"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.characters, key = { it.id }) { character ->
                    val selected = selectedCharacter == character.id
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) p.actionPrimary else p.raised)
                            .clickable { selectedCharacter = character.id }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JaumAvatar(Color(character.color), character.shape, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            character.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) p.onActionPrimary else p.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 24.dp)) {
                PickleTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = tr(lang, "예: 나는 아침에 커피를 마셔요", "e.g. I drink coffee in the morning"),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = tr(lang, "저장", "Save"),
                    enabled = text.isNotBlank() && selectedCharacter != null,
                    onClick = {
                        selectedCharacter?.let { vm.addMemory(it, text) }
                        showAdd = false
                    }
                )
            }
        }
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 기억을 삭제할까요?", "Delete this memory?"),
            body = tr(lang, "피클이 더 이상 이 내용을 기억하지 않아요.", "Your Pickle will no longer remember it."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteMemory(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
fun TasksScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var filter by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val tabs = listOf(tr(lang, "진행 중", "Active"), tr(lang, "완료", "Done"), tr(lang, "전체", "All"))
    val filtered = when (filter) {
        1 -> state.tasks.filter { it.done }
        2 -> state.tasks
        else -> state.tasks.filterNot { it.done }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        PickleTopBar(
            title = tr(lang, "할 일", "Tasks"),
            subtitle = tr(lang, "${state.tasks.count { !it.done }}개 진행 중", "${state.tasks.count { !it.done }} active"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { index ->
                PickleChip(tabs[index], filter == index, onClick = { filter = index })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)) {
            items(filtered, key = { it.id }) { task ->
                TaskCard(
                    state,
                    task,
                    onToggleStep = { index -> vm.toggleTaskStep(task.id, index) },
                    onToggleDone = { vm.toggleTaskDone(task.id) },
                    onDelete = { deleteTarget = task.id }
                )
            }
            if (filtered.isEmpty()) {
                item {
                    com.pickle.client.ui.components.EmptyState(
                        glyph = Glyph.TASK,
                        title = tr(lang, "할 일이 없어요", "No tasks"),
                        body = tr(lang, "피클에게 일을 맡기거나 직접 추가해보세요.", "Ask your Pickle or add one yourself."),
                        actionLabel = tr(lang, "할 일 추가", "Add task"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            state = state,
            onDismiss = { showAdd = false },
            onAdd = { title, characterId, due, steps ->
                vm.addTask(title, characterId, due, steps)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 할 일을 삭제할까요?", "Delete this task?"),
            body = tr(lang, "단계와 진행률도 함께 사라져요.", "Its steps and progress will be removed."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteTask(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun TaskCard(
    state: AppState,
    task: TaskItem,
    onToggleStep: (Int) -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val character = state.character(task.characterId)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JaumAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                )
                Text("${character?.name ?: ""} · ${task.due}", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
            Text(
                "⋯",
                color = p.textTertiary,
                modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 6.dp, vertical = 4.dp)
            )
            CheckCircle(task.done, onClick = onToggleDone)
        }
        if (task.steps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            task.steps.forEachIndexed { index, step ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleStep(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckCircle(step.done)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        step.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.done) p.textTertiary else p.textPrimary,
                        textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(p.control)) {
                    Box(Modifier.fillMaxWidth(task.progress).height(4.dp).clip(CircleShape).background(p.blue))
                }
                Spacer(Modifier.width(8.dp))
                Text("${(task.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, List<String>) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var characterId by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(tr(lang, "할 일 추가", "Add a task"), style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = {
            Column {
                PickleTextField(title, { title = it }, placeholder = tr(lang, "무엇을 할까요?", "What needs doing?"))
                Spacer(Modifier.height(8.dp))
                PickleTextField(due, { due = it }, placeholder = tr(lang, "언제까지? 예: 오늘 오후 3시", "When? e.g. today 3pm"))
                Spacer(Modifier.height(8.dp))
                PickleTextField(
                    steps, { steps = it },
                    placeholder = tr(lang, "단계는 줄바꿈으로 구분해요", "Separate steps with new lines"),
                    singleLine = false, maxLines = 4
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.characters.size) { index ->
                        val character = state.characters[index]
                        val selected = characterId == character.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) p.actionPrimary else p.raised)
                                .clickable { characterId = character.id }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            JaumAvatar(Color(character.color), character.shape, size = 20.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(character.name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.onActionPrimary else p.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        title,
                        characterId,
                        due.ifBlank { tr(lang, "오늘", "Today") },
                        steps.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    )
                }
            ) { Text(tr(lang, "추가", "Add"), color = if (title.isNotBlank()) p.blue else p.textDisabled) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "취소", "Cancel"), color = p.textSecondary) } }
    )
}

@Composable
fun SchedulesScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        PickleTopBar(
            title = tr(lang, "일정", "Schedules"),
            subtitle = tr(lang, "${state.schedules.count { it.enabled }}개 알림이 켜져 있어요", "${state.schedules.count { it.enabled }} reminders on"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.cardSoft)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(tr(lang, "다가오는 일정", "Coming up"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        state.schedules.filter { it.enabled }.take(2).forEach { schedule ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.CALENDAR, p.blue, size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(schedule.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                                    Text(schedule.whenText, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                                }
                            }
                        }
                        if (state.schedules.none { it.enabled }) {
                            Text(tr(lang, "켜져 있는 일정이 없어요.", "No active reminders."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                        }
                    }
                }
            }
            items(state.schedules, key = { it.id }) { schedule ->
                val character = state.character(schedule.characterId)
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .clickable { deleteTarget = schedule.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JaumAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(schedule.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(
                            "${schedule.whenText} · ${schedule.repeat.label(lang)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary
                        )
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { vm.toggleSchedule(schedule.id) },
                        colors = com.pickle.client.ui.components.pickleSwitchColors()
                    )
                }
            }
            if (state.schedules.isEmpty()) {
                item {
                    com.pickle.client.ui.components.EmptyState(
                        glyph = Glyph.CALENDAR,
                        title = tr(lang, "일정이 없어요", "No schedules"),
                        body = tr(lang, "피클이 알려줄 일정을 추가해보세요.", "Add something your Pickle should remind you about."),
                        actionLabel = tr(lang, "일정 추가", "Add schedule"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            state = state,
            onDismiss = { showAdd = false },
            onAdd = { title, whenText, characterId, repeat ->
                vm.addSchedule(title, whenText, characterId, repeat)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 일정을 삭제할까요?", "Delete this schedule?"),
            body = tr(lang, "알림도 함께 꺼져요.", "Its reminder will be removed too."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteSchedule(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun AddScheduleDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Repeat) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var title by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var characterId by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(tr(lang, "일정 추가", "Add a schedule"), style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = {
            Column {
                PickleTextField(title, { title = it }, placeholder = tr(lang, "무엇을 알려줄까요?", "What should we remind you about?"))
                Spacer(Modifier.height(8.dp))
                PickleTextField(whenText, { whenText = it }, placeholder = tr(lang, "예: 내일 오후 3시", "e.g. tomorrow 3pm"))
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Repeat.entries) { option ->
                        PickleChip(option.label(lang), repeat == option, onClick = { repeat = option })
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.characters.size) { index ->
                        val character = state.characters[index]
                        val selected = characterId == character.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) p.actionPrimary else p.raised)
                                .clickable { characterId = character.id }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            JaumAvatar(Color(character.color), character.shape, size = 20.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(character.name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.onActionPrimary else p.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, whenText.ifBlank { tr(lang, "오늘", "Today") }, characterId, repeat) }
            ) { Text(tr(lang, "추가", "Add"), color = if (title.isNotBlank()) p.blue else p.textDisabled) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "취소", "Cancel"), color = p.textSecondary) } }
    )
}
