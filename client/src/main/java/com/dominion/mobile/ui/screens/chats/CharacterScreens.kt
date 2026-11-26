package com.dominion.mobile.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.CharacterShape
import com.dominion.mobile.data.model.VoiceTone
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.Hairline
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.LevelBar
import com.dominion.mobile.ui.components.AppTextField
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SecondaryButton
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SettingsRow
import com.dominion.mobile.ui.components.SwitchRow
import com.dominion.mobile.ui.components.TextPromptDialog
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.nav.Routes
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asRelative

@Composable
fun CharacterScreen(
    state: AppState,
    vm: AppViewModel,
    characterId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onStartCall: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val character = state.character(characterId)
    if (character == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val memories = state.memories.filter { it.characterId == characterId }
    val tasks = state.tasks.filter { it.characterId == characterId }
    val schedules = state.schedules.filter { it.characterId == characterId }
    var showRename by remember { mutableStateOf(false) }
    var showAddMemory by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.screen)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ScreenTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(
                    if (character.mine) tr(lang, "편집", "Edit") else tr(lang, "신고", "Report"),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textSecondary,
                    modifier = Modifier.clickable {
                        if (character.mine) showRename = true
                        else context.toast(tr(lang, "신고가 접수됐어요 (데모)", "Report received (demo)"))
                    }.padding(12.dp)
                )
            }
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(character.color).copy(alpha = 0.24f), p.screen)
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AssistantAvatar(Color(character.color), character.shape, size = 104.dp, level = character.level, breathing = true)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(character.name, style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    if (character.online) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(p.success))
                    }
                }
                Text(character.tagline, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.actionPrimary)
                            .clickable {
                                val conv = vm.directConversationFor(character.id)
                                if (conv != null) onOpenChat(conv)
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(tr(lang, "대화하기", "Chat"), color = p.onActionPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card)
                            .clickable {
                                val conv = vm.directConversationFor(character.id)
                                if (conv != null && vm.startCall(conv, false)) onStartCall()
                                else context.toast(tr(lang, "음성 통화는 연결된 서버에서 사용할 수 있어요", "Voice calls require a connected server"))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.PHONE, p.textPrimary, size = 15.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(tr(lang, "통화", "Call"), color = p.textPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card)
                            .clickable { onNavigate(Routes.customize(character.id)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(tr(lang, "꾸미기", "Style"), color = p.textPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(20.dp))
                LevelBar(character.level, character.xp, character.xpGoal)
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("${memories.size}", tr(lang, "기억", "Memories"), Modifier.weight(1f)) { onNavigate(Routes.MEMORIES) }
                    StatBox("${tasks.count { !it.done }}", tr(lang, "할 일", "Tasks"), Modifier.weight(1f)) { onNavigate(Routes.TASKS) }
                    StatBox("${schedules.count { it.enabled }}", tr(lang, "일정", "Schedules"), Modifier.weight(1f)) { onNavigate(Routes.SCHEDULES) }
                }
                Spacer(Modifier.height(16.dp))
                if (character.bio.isNotBlank()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.cardSoft).padding(15.dp)
                    ) {
                        Text(character.bio, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel(
                    tr(lang, "기억", "Memories"),
                    action = tr(lang, "추가", "Add"),
                    onAction = { showAddMemory = true }
                )
                if (memories.isEmpty()) {
                    Text(tr(lang, "아직 저장된 기억이 없어요.", "No memories yet."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                } else {
                    memories.take(4).forEach { memory ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            GlyphIcon(Glyph.MEMORY, p.textTertiary, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(memory.text, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                                Text(memory.source, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel(tr(lang, "진행 중인 할 일", "Active tasks"))
                if (tasks.none { !it.done }) {
                    Text(tr(lang, "진행 중인 할 일이 없어요.", "No active tasks."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                } else {
                    tasks.filter { !it.done }.take(3).forEach { task ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(p.raised),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${(task.progress * 100).toInt()}%", fontSize = 8.sp, color = p.textSecondary)
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                                Text(task.due, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
                ) {
                    Column {
                        SettingsRow(
                            title = tr(lang, "목소리", "Voice"),
                            trailingText = character.voice.label(lang),
                            leading = { GlyphIcon(Glyph.SPEAKER, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.customize(character.id)) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "대화 검색", "Search chats"),
                            leading = { GlyphIcon(Glyph.SPARK, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.SEARCH) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "이 도미니온과의 추억", "Shared archive"),
                            leading = { GlyphIcon(Glyph.IMAGE, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.MAIN) }
                        )
                        if (!character.mine) {
                            Hairline()
                            SettingsRow(
                                title = tr(lang, "차단하기", "Block"),
                                danger = true,
                                showChevron = false,
                                leading = { GlyphIcon(Glyph.SHIELD, p.danger, size = 18.dp) },
                                onClick = {
                                    vm.blockUser(character.id)
                                    context.toast(tr(lang, "차단했어요", "Blocked"))
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showRename) {
        TextPromptDialog(
            title = tr(lang, "도미니온 이름 바꾸기", "Rename your Dominion"),
            label = tr(lang, "이름", "Name"),
            initial = character.name,
            confirmLabel = tr(lang, "저장", "Save"),
            onConfirm = { vm.updateCharacter(character.id, name = it); showRename = false },
            onDismiss = { showRename = false }
        )
    }
    if (showAddMemory) {
        TextPromptDialog(
            title = tr(lang, "기억 추가", "Add a memory"),
            label = tr(lang, "기억할 내용", "What should it remember?"),
            confirmLabel = tr(lang, "저장", "Save"),
            onConfirm = { vm.addMemory(character.id, it); showAddMemory = false },
            onDismiss = { showAddMemory = false }
        )
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.cardSoft)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
    }
}

@Composable
fun CustomizeScreen(
    state: AppState,
    vm: AppViewModel,
    characterId: String,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val character = state.character(characterId)
    if (character == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    var color by remember(character.id) { mutableStateOf(character.color) }
    var shape by remember(character.id) { mutableStateOf(character.shape) }
    var voice by remember(character.id) { mutableStateOf(character.voice) }
    var name by remember(character.id) { mutableStateOf(character.name) }
    var bio by remember(character.id) { mutableStateOf(character.bio) }
    val context = LocalContext.current
    val previewVoice = com.dominion.mobile.platform.audio.rememberVoicePreview(lang)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen).imePadding()
    ) {
        ScreenTopBar(title = tr(lang, "꾸미기", "Customize"), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AssistantAvatar(Color(color), shape, size = 124.dp, breathing = true)
            }
            Spacer(Modifier.height(20.dp))
            Text(tr(lang, "개인화 프리셋", "Personalization presets"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.dominion.mobile.data.AssistantCatalog.assistantPresets().forEach { preset ->
                    Column(Modifier.clickable {
                        name = preset.name
                        bio = preset.bio
                        color = preset.color
                        shape = preset.shape
                        voice = preset.voice
                    }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AssistantAvatar(Color(preset.color), preset.shape, size = 42.dp)
                        Text(preset.name, color = p.textPrimary)
                    }
                }
            }
            AppTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = tr(lang, "이름", "Name")
            )
            Spacer(Modifier.height(20.dp))
            AppTextField(value = bio, onValueChange = { bio = it.take(4000) }, label = tr(lang, "소개·성격 설정 (기기 내)", "Profile and personality (local)"))
            Text(tr(lang, "색", "Color"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF4797EF, 0xFFFF8FB1, 0xFF8B7CF6, 0xFF3FB6A8, 0xFFF5A524, 0xFFEE5D5D, 0xFF22252C, 0xFF9AD0FF).forEach { swatch ->
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(if (color == swatch) 3.dp else 0.dp, if (color == swatch) p.textPrimary else Color.Transparent, CircleShape)
                            .clickable { color = swatch }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(tr(lang, "모양", "Shape"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CharacterShape.entries.forEach { s ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (shape == s) p.selected else p.cardSoft)
                            .border(1.dp, if (shape == s) p.textPrimary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { shape = s }
                            .padding(6.dp)
                    ) {
                        AssistantAvatar(Color(color), s, size = 50.dp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(tr(lang, "목소리", "Voice"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Spacer(Modifier.height(8.dp))
            VoiceTone.entries.forEach { tone ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (voice == tone) p.selected else p.cardSoft)
                        .clickable { voice = tone }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.SPEAKER, p.blue, size = 17.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("${tone.label(lang)} ${tr(lang, "목소리", "voice")}", style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, modifier = Modifier.weight(1f))
                    if (voice == tone) Text("✓", color = p.blue)
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = tr(lang, "저장", "Save"),
                onClick = {
                    vm.updateCharacter(character.id, name = name.ifBlank { character.name }, color = color, shape = shape, voice = voice, bio = bio)
                    context.toast(tr(lang, "저장했어요", "Saved"))
                    onBack()
                }
            )
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                text = tr(lang, "미리 듣기 (기기 음성)", "Preview voice (device)"),
                onClick = { previewVoice(voice) { context.toast(tr(lang, "기기의 음성 합성 서비스를 확인하세요.", "Check the device speech service")) } }
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun RoomInfoScreen(
    state: AppState,
    vm: AppViewModel,
    conversationId: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onSearch: () -> Unit,
    onOpenCharacter: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val conversation = state.conversation(conversationId)
    val character = conversation?.let { state.character(it.characterId) }
    if (conversation == null || character == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRewind by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)
    ) {
        ScreenTopBar(title = tr(lang, "대화방 정보", "Room info"), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AssistantAvatar(Color(character.color), character.shape, size = 84.dp, level = character.level)
                Spacer(Modifier.height(10.dp))
                Text(conversation.title(state.characters), style = MaterialTheme.typography.headlineSmall, color = p.textPrimary)
                Text(character.tagline, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(p.raised).clickable(onClick = onOpenChat).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(tr(lang, "대화 열기", "Open chat"), style = MaterialTheme.typography.labelSmall, color = p.textPrimary) }
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(p.raised).clickable(onClick = onSearch).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(tr(lang, "대화 검색", "Search"), style = MaterialTheme.typography.labelSmall, color = p.textPrimary) }
                }
            }
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
            ) {
                Column {
                    SwitchRow(
                        title = tr(lang, "알림 받기", "Notifications"),
                        subtitle = tr(lang, "새 메시지가 오면 알려드려요", "Get notified about new messages"),
                        checked = !conversation.muted,
                        onCheckedChange = { vm.setMuted(conversationId, !it) }
                    )
                    Hairline()
                    SwitchRow(
                        title = tr(lang, "대화 고정", "Pin chat"),
                        subtitle = tr(lang, "목록 맨 위에 표시해요", "Show this chat at the top"),
                        checked = conversation.pinned,
                        onCheckedChange = { vm.setPinned(conversationId, it) }
                    )
                    Hairline()
                    SettingsRow(
                        title = tr(lang, "공유된 파일", "Shared files"),
                        subtitle = tr(lang, "사진 3개 · 파일 2개", "3 photos · 2 files"),
                        leading = { GlyphIcon(Glyph.IMAGE, p.textPrimary, size = 18.dp) },
                        onClick = { onSearch() }
                    )
                    Hairline()
                    SettingsRow(
                        title = tr(lang, "대화 되감기", "Rewind chat"),
                        subtitle = tr(lang, "리셋 티켓 1장을 사용해요", "Uses 1 reset ticket"),
                        leading = { GlyphIcon(Glyph.REWIND, p.textPrimary, size = 18.dp) },
                        onClick = { confirmRewind = true }
                    )
                    Hairline()
                    SettingsRow(
                        title = tr(lang, "대화 비우기", "Clear chat"),
                        leading = { GlyphIcon(Glyph.CLOSE, p.textPrimary, size = 18.dp) },
                        onClick = {
                            vm.clearConversation(conversationId)
                            context.toast(tr(lang, "대화를 비웠어요", "Chat cleared"))
                        }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
            ) {
                Column {
                    SettingsRow(
                        title = tr(lang, "프로필 보기", "View profile"),
                        leading = { GlyphIcon(Glyph.PERSON, p.textPrimary, size = 18.dp) },
                        onClick = onOpenCharacter
                    )
                    Hairline()
                    SettingsRow(
                        title = tr(lang, "신고하기", "Report"),
                        leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 18.dp) },
                        onClick = { context.toast(tr(lang, "신고가 접수됐어요 (데모)", "Report received (demo)")) }
                    )
                    Hairline()
                    SettingsRow(
                        title = tr(lang, "대화방 나가기", "Leave chat"),
                        danger = true,
                        showChevron = false,
                        leading = { GlyphIcon(Glyph.CLOSE, p.danger, size = 18.dp) },
                        onClick = { confirmLeave = true }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                tr(lang, "대화는 나만 볼 수 있어요. 금고에 저장한 정보는 암호화돼요.", "Your chats stay private. Vault items are encrypted."),
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmRewind) {
        PrimaryDialog(
            title = tr(lang, "대화를 되감을까요?", "Rewind this chat?"),
            body = tr(lang, "리셋 티켓 1장이 사용돼요.", "1 reset ticket will be used."),
            confirmLabel = tr(lang, "되감기", "Rewind"),
            onConfirm = {
                confirmRewind = false
                if (!vm.useResetTicket(conversationId)) context.toast(tr(lang, "리셋 티켓이 없어요", "No reset tickets"))
            },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmRewind = false }
        )
    }
    if (confirmLeave) {
        PrimaryDialog(
            title = tr(lang, "대화방을 나갈까요?", "Leave this chat?"),
            body = tr(lang, "대화 목록에서 사라져요.", "It will disappear from your chats."),
            confirmLabel = tr(lang, "나가기", "Leave"),
            onConfirm = {
                confirmLeave = false
                vm.deleteConversation(conversationId)
                onBack()
            },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmLeave = false },
            destructive = true
        )
    }
}
