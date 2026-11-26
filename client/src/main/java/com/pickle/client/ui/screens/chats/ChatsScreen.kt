package com.pickle.client.ui.screens.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.CardKind
import com.pickle.client.data.model.Conversation
import com.pickle.client.data.model.MessageKind
import com.pickle.client.data.model.MessageSender
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.CountPill
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphButton
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.PickleBottomSheet
import com.pickle.client.ui.components.PrimaryDialog
import com.pickle.client.ui.components.SectionLabel
import com.pickle.client.ui.components.SheetRow
import com.pickle.client.ui.components.SheetTitle
import com.pickle.client.ui.components.TextPromptDialog
import com.pickle.client.ui.components.TruncatedText
import com.pickle.client.ui.components.UnreadBadge
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import com.pickle.client.util.asRelative

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    state: AppState,
    onOpenDrawer: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSearch: () -> Unit,
    onInbox: () -> Unit,
    onNewChat: () -> Unit,
    onCharacter: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onMute: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMarkRead: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var actionTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    val ordered = state.conversations
        .filterNot { it.request }
        .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
    val pinned = ordered.filter { it.pinned }
    val rest = ordered.filterNot { it.pinned }
    val own = state.myCharacter()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onOpenDrawer, modifier = Modifier.size(40.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = p.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        "Pickle",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.Bold,
                        color = p.textPrimary,
                        letterSpacing = (-0.8).sp
                    )
                    Box(
                        Modifier
                            .padding(start = 3.dp, top = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(p.blue)
                    )
                }
                Spacer(Modifier.weight(1f))
                GlyphButton(Glyph.SEARCH, onClick = onSearch, tint = p.textPrimary)
                Box {
                    GlyphButton(Glyph.BELL, onClick = onInbox, tint = p.textPrimary)
                    val unread = state.unreadActivity()
                    if (unread > 0) {
                        Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 0.dp)) {
                            CountPill(unread, p.danger)
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)) {
                item {
                    HeroCard(
                        name = state.account.name,
                        ownColor = own?.color ?: 0xFF4797EF,
                        ownShape = own?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB,
                        message = tr(lang, "오늘은 무엇을 함께 해볼까요?", "What should we do together today?"),
                        onNewChat = onNewChat
                    )
                }
                if (pinned.isNotEmpty()) {
                    item { SectionLabel(tr(lang, "고정됨", "Pinned"), Modifier.padding(horizontal = 20.dp)) }
                    items(pinned, key = { it.id }) { conv ->
                        ConversationRow(
                            state = state,
                            conv = conv,
                            onClick = { onMarkRead(conv.id); onOpenChat(conv.id) },
                            onLongClick = { actionTarget = conv },
                            onCharacter = { onCharacter(conv.characterId) }
                        )
                    }
                }
                item {
                    SectionLabel(
                        tr(lang, "대화", "Chats"),
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        action = tr(lang, "새 대화", "New chat"),
                        onAction = onNewChat
                    )
                }
                if (rest.isEmpty() && pinned.isEmpty()) {
                    item {
                        com.pickle.client.ui.components.EmptyState(
                            glyph = Glyph.SPARK,
                            title = tr(lang, "아직 대화가 없어요", "No chats yet"),
                            body = tr(lang, "피클에게 말을 걸면 오늘 할 일을 함께 찾아드려요.", "Say hello to your Pickle to get started."),
                            actionLabel = tr(lang, "첫 대화 시작", "Start a chat"),
                            onAction = onNewChat
                        )
                    }
                } else {
                    items(rest, key = { it.id }) { conv ->
                        ConversationRow(
                            state = state,
                            conv = conv,
                            onClick = { onMarkRead(conv.id); onOpenChat(conv.id) },
                            onLongClick = { actionTarget = conv },
                            onCharacter = { onCharacter(conv.characterId) }
                        )
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(p.actionPrimary)
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(Icons.Filled.Add, "New chat", tint = p.onActionPrimary, modifier = Modifier.size(25.dp))
        }
    }

    actionTarget?.let { conv ->
        val character = state.character(conv.characterId)
        PickleBottomSheet(onDismiss = { actionTarget = null }) {
            SheetTitle(conv.title(state.characters), character?.tagline)
            SheetRow(
                tr(lang, if (conv.pinned) "고정 해제" else "고정하기", if (conv.pinned) "Unpin" else "Pin"),
                Glyph.PIN
            ) { onPin(conv.id, !conv.pinned); actionTarget = null }
            SheetRow(
                tr(lang, if (conv.muted) "알림 켜기" else "알림 끄기", if (conv.muted) "Unmute" else "Mute"),
                Glyph.SPEAKER_OFF
            ) { onMute(conv.id, !conv.muted); actionTarget = null }
            SheetRow(tr(lang, "이름 바꾸기", "Rename"), Glyph.LINK) { renameTarget = conv; actionTarget = null }
            if (character != null) SheetRow(tr(lang, "프로필 보기", "View profile"), Glyph.MEMORY) {
                actionTarget = null
                onCharacter(character.id)
            }
            SheetRow(tr(lang, "읽음으로 표시", "Mark as read"), Glyph.CHECK) { onMarkRead(conv.id); actionTarget = null }
            SheetRow(tr(lang, "대화 삭제", "Delete chat"), Glyph.CLOSE, danger = true) {
                deleteTarget = conv
                actionTarget = null
            }
        }
    }

    renameTarget?.let { conv ->
        TextPromptDialog(
            title = tr(lang, "대화 이름 바꾸기", "Rename chat"),
            label = tr(lang, "이름", "Name"),
            initial = conv.title(state.characters),
            confirmLabel = tr(lang, "저장", "Save"),
            onConfirm = { onRename(conv.id, it); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { conv ->
        PrimaryDialog(
            title = tr(lang, "대화를 삭제할까요?", "Delete this chat?"),
            body = tr(lang, "이 대화의 메시지가 목록에서 사라져요. 기억에 저장된 내용은 남아 있어요.", "Messages disappear from your list. Saved memories stay."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { onDelete(conv.id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun HeroCard(
    name: String,
    ownColor: Long,
    ownShape: com.pickle.client.data.model.CharacterShape,
    message: String,
    onNewChat: () -> Unit
) {
    val p = LocalPicklePalette.current
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(ownColor).copy(alpha = 0.20f), p.cardSoft, p.cardSoft)
                )
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JaumAvatar(Color(ownColor), ownShape, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$name${if (LocalLang.current == com.pickle.client.data.model.Lang.KO) "님" else ""},",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Text(message, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.actionPrimary)
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.PLUS, p.onActionPrimary, size = 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(tr(LocalLang.current, "새 대화", "New chat"), style = MaterialTheme.typography.labelSmall, color = p.onActionPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    state: AppState,
    conv: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCharacter: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val character = state.character(conv.characterId)
    val last = conv.lastMessage()
    val preview = when {
        last == null -> tr(lang, "새 대화를 시작해보세요", "Start a new conversation")
        last.card != null -> last.card.title
        last.kind == MessageKind.IMAGE -> tr(lang, "사진", "Photo")
        else -> last.text
    }
    val prefix = when {
        last?.sender == MessageSender.ME -> tr(lang, "나: ", "Me: ")
        last?.card?.kind == CardKind.TASK -> tr(lang, "할 일 · ", "Task · ")
        last?.card?.kind == CardKind.CALL -> tr(lang, "통화 · ", "Call · ")
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            JaumAvatar(
                color = Color(character?.color ?: 0xFF4797EF),
                shape = character?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB,
                size = 54.dp
            )
            if (character?.online == true) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 3.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(p.success)
                        .border(2.dp, p.screen, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.title(state.characters),
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conv.kind == com.pickle.client.data.model.ConversationKind.EXTRA) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        tr(lang, "추가", "Extra"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = p.blue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.blue.copy(alpha = 0.10f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (conv.pinned) {
                    Spacer(Modifier.width(4.dp))
                    GlyphIcon(Glyph.PIN, p.textTertiary, size = 12.dp)
                }
                if (conv.muted) {
                    Spacer(Modifier.width(4.dp))
                    GlyphIcon(Glyph.SPEAKER_OFF, p.textTertiary, size = 13.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    conv.updatedAt.asRelative(lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textTertiary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TruncatedText(
                    prefix + preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conv.unread > 0) p.textSecondary else p.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                if (conv.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(conv.unread)
                }
            }
        }
    }
}
