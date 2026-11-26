package com.pickle.client.ui.screens.chats

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.CardKind
import com.pickle.client.data.model.Character
import com.pickle.client.data.model.Message
import com.pickle.client.data.model.MessageCard
import com.pickle.client.data.model.MessageSender
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.ChatCardView
import com.pickle.client.ui.components.DateDivider
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphButton
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.MessageBubble
import com.pickle.client.ui.components.PickleBottomSheet
import com.pickle.client.ui.components.PrimaryDialog
import com.pickle.client.ui.components.SheetRow
import com.pickle.client.ui.components.SheetTitle
import com.pickle.client.ui.components.TypingBubble
import com.pickle.client.ui.components.toast
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import com.pickle.client.util.asDayHeader

private sealed interface ChatEntry {
    data class Day(val label: String) : ChatEntry
    data class Item(val message: Message) : ChatEntry
    data object Typing : ChatEntry
}

@Composable
fun ChatRoomScreen(
    state: AppState,
    vm: AppViewModel,
    conversationId: String,
    onBack: () -> Unit,
    onCharacter: (String) -> Unit,
    onRoomInfo: () -> Unit,
    onSearch: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val conversation = state.conversation(conversationId)
    val character = conversation?.let { state.character(it.characterId) }

    if (conversation == null || character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var draft by remember(conversationId) { mutableStateOf(conversation.draft) }
    var showAttach by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<Message?>(null) }
    var confirmRewind by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val entries = remember(conversation.messages, state.typingConversationId, lang) {
        buildList {
            var lastDay = ""
            conversation.messages.forEach { message ->
                val day = message.createdAt.asDayHeader(lang)
                if (day != lastDay) {
                    add(ChatEntry.Day(day))
                    lastDay = day
                }
                add(ChatEntry.Item(message))
            }
            if (state.typingConversationId == conversationId) add(ChatEntry.Typing)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size, conversation.messages.lastOrNull()?.id, state.typingConversationId) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    fun send() {
        if (draft.isBlank()) return
        vm.sendMessage(conversationId, draft)
        draft = ""
        vm.setDraft(conversationId, "")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.screen)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = p.textPrimary, modifier = Modifier.size(22.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCharacter(character.id) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JaumAvatar(Color(character.color), character.shape, size = 34.dp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        conversation.title(state.characters),
                        style = MaterialTheme.typography.titleSmall,
                        color = p.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (state.typingConversationId == conversationId) {
                            tr(lang, "입력 중…", "typing…")
                        } else if (character.online) {
                            tr(lang, "지금 대화 가능", "Available now")
                        } else {
                            character.tagline
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (character.online) p.blue else p.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GlyphButton(Glyph.PHONE, onClick = {
                if (vm.startCall(conversationId, false)) onStartCall(false)
                else context.toast(tr(lang, "에너지가 부족해요", "Not enough energy"))
            })
            GlyphButton(Glyph.VIDEO, onClick = {
                if (vm.startCall(conversationId, true)) onStartCall(true)
                else context.toast(tr(lang, "에너지가 부족해요", "Not enough energy"))
            })
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = p.textPrimary, modifier = Modifier.size(21.dp))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            items(entries.size) { index ->
                when (val entry = entries[index]) {
                    is ChatEntry.Day -> DateDivider(entry.label)
                    is ChatEntry.Item -> MessageBubble(
                        message = entry.message,
                        character = character,
                        onLongPress = { messageMenu = entry.message },
                        onCardAction = {
                            when (entry.message.card?.kind) {
                                CardKind.TASK -> onNavigate(com.pickle.client.ui.nav.Routes.TASKS)
                                CardKind.LINK -> context.toast(tr(lang, "링크를 열었어요 (데모)", "Opened link (demo)"))
                                CardKind.FILE -> context.toast(tr(lang, "파일을 열었어요 (데모)", "Opened file (demo)"))
                                else -> onNavigate(com.pickle.client.ui.nav.Routes.TASKS)
                            }
                        }
                    )
                    ChatEntry.Typing -> TypingBubble(character)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.screen)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(p.raised)
                    .clickable { showAttach = true },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.PLUS, p.textPrimary, size = 20.dp)
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.input)
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (it.length % 8 == 0) vm.setDraft(conversationId, it)
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(p.textPrimary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    tr(lang, "피클에게 메시지", "Message your Pickle"),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = p.textTertiary
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (draft.isBlank()) p.raised else p.blue)
                    .clickable {
                        if (draft.isBlank()) context.toast(tr(lang, "음성 입력은 데모에서 지원하지 않아요", "Voice input is not wired in the demo"))
                        else send()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (draft.isBlank()) GlyphIcon(Glyph.MIC, p.textSecondary, size = 20.dp)
                else GlyphIcon(Glyph.SEND, Color.White, size = 19.dp)
            }
        }
    }

    if (showAttach) {
        PickleBottomSheet(onDismiss = { showAttach = false }) {
            SheetTitle(tr(lang, "무엇을 보낼까요?", "What would you like to send?"))
            SheetRow(tr(lang, "사진", "Photo"), Glyph.IMAGE) {
                vm.addCardToConversation(
                    conversationId,
                    MessageCard(CardKind.IMAGE, "사진", "데모 이미지가 첨부됐어요", "오늘"),
                    ""
                )
                showAttach = false
            }
            SheetRow(tr(lang, "카메라", "Camera"), Glyph.CAMERA) {
                vm.addCardToConversation(
                    conversationId,
                    MessageCard(CardKind.IMAGE, "카메라로 찍은 사진", "카메라 권한은 연결 후 사용할 수 있어요", "오늘"),
                    ""
                )
                showAttach = false
            }
            SheetRow(tr(lang, "파일", "File"), Glyph.FILE) {
                vm.addCardToConversation(
                    conversationId,
                    MessageCard(CardKind.FILE, "공유한 파일.pdf", "PDF · 1.2 MB", "방금"),
                    ""
                )
                showAttach = false
            }
            SheetRow(tr(lang, "할 일", "Task"), Glyph.TASK) { showAttach = false; onNavigate(com.pickle.client.ui.nav.Routes.TASKS) }
            SheetRow(tr(lang, "일정", "Schedule"), Glyph.CALENDAR) { showAttach = false; onNavigate(com.pickle.client.ui.nav.Routes.SCHEDULES) }
            SheetRow(tr(lang, "연결", "Connections"), Glyph.PLUG) { showAttach = false; onNavigate(com.pickle.client.ui.nav.Routes.CONNECTIONS) }
        }
    }

    if (showMenu) {
        PickleBottomSheet(onDismiss = { showMenu = false }) {
            SheetTitle(conversation.title(state.characters), character.tagline)
            SheetRow(tr(lang, "대화방 정보", "Room info"), Glyph.PERSON) { showMenu = false; onRoomInfo() }
            SheetRow(tr(lang, "대화 검색", "Search chat"), Glyph.SEARCH) { showMenu = false; onSearch() }
            SheetRow(
                tr(lang, if (conversation.pinned) "고정 해제" else "고정하기", if (conversation.pinned) "Unpin" else "Pin"),
                Glyph.PIN
            ) { vm.setPinned(conversationId, !conversation.pinned); showMenu = false }
            SheetRow(
                tr(lang, if (conversation.muted) "알림 켜기" else "알림 끄기", if (conversation.muted) "Unmute" else "Mute"),
                Glyph.SPEAKER_OFF
            ) { vm.setMuted(conversationId, !conversation.muted); showMenu = false }
            SheetRow(tr(lang, "대화 되감기 (티켓 1장)", "Rewind (1 ticket)"), Glyph.REWIND) { showMenu = false; confirmRewind = true }
            SheetRow(tr(lang, "대화 비우기", "Clear chat"), Glyph.CLOSE) { showMenu = false; confirmClear = true }
            SheetRow(tr(lang, "신고하기", "Report"), Glyph.SHIELD) {
                showMenu = false
                context.toast(tr(lang, "신고가 접수됐어요 (데모)", "Report received (demo)"))
            }
        }
    }

    messageMenu?.let { message ->
        PickleBottomSheet(onDismiss = { messageMenu = null }) {
            SheetTitle(tr(lang, "메시지", "Message"))
            if (message.text.isNotBlank()) {
                SheetRow(tr(lang, "복사", "Copy"), Glyph.LINK) {
                    clipboard.setText(AnnotatedString(message.text))
                    context.toast(tr(lang, "복사했어요", "Copied"))
                    messageMenu = null
                }
            }
            SheetRow(tr(lang, "기억에 저장", "Save to memories"), Glyph.MEMORY) {
                vm.addMemory(character.id, message.text.ifBlank { message.card?.title.orEmpty() })
                context.toast(tr(lang, "기억에 저장했어요", "Saved to memories"))
                messageMenu = null
            }
            SheetRow(tr(lang, "이 메시지에서 할 일 만들기", "Create a task"), Glyph.TASK) {
                vm.addTask(
                    title = message.text.take(40).ifBlank { "새 할 일" },
                    characterId = character.id,
                    due = tr(lang, "오늘", "Today"),
                    steps = listOf(tr(lang, "내용 확인", "Review"), tr(lang, "완료하기", "Finish"))
                )
                context.toast(tr(lang, "할 일을 만들었어요", "Task created"))
                messageMenu = null
            }
        }
    }

    if (confirmRewind) {
        PrimaryDialog(
            title = tr(lang, "대화를 되감을까요?", "Rewind this chat?"),
            body = tr(lang, "리셋 티켓 1장을 사용해 마지막 요청으로 돌아가요.", "Uses 1 reset ticket to go back to your last request."),
            confirmLabel = tr(lang, "되감기", "Rewind"),
            onConfirm = {
                confirmRewind = false
                if (!vm.useResetTicket(conversationId)) {
                    context.toast(tr(lang, "리셋 티켓이 없어요", "No reset tickets left"))
                }
            },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmRewind = false }
        )
    }
    if (confirmClear) {
        PrimaryDialog(
            title = tr(lang, "대화를 비울까요?", "Clear this chat?"),
            body = tr(lang, "메시지가 모두 사라져요. 이 작업은 되돌릴 수 없어요.", "All messages will be removed. This cannot be undone."),
            confirmLabel = tr(lang, "비우기", "Clear"),
            onConfirm = { confirmClear = false; vm.clearConversation(conversationId) },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmClear = false },
            destructive = true
        )
    }
}
