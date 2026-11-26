package com.dominion.mobile.ui.screens.chats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.EmptyState
import com.dominion.mobile.ui.components.FriendAvatar
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.SearchField
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SettingsRow
import com.dominion.mobile.ui.components.TextPromptDialog
import com.dominion.mobile.ui.nav.Routes
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asRelative

@Composable
fun SearchScreen(
    state: AppState,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var query by remember { mutableStateOf("") }

    val convResults = state.conversations.filter {
        query.isNotBlank() && it.title(state.characters).contains(query, true)
    }
    val messageResults = if (query.isBlank()) emptyList() else state.conversations.flatMap { conv ->
        conv.messages.filter { it.text.contains(query, true) }.map { conv to it }
    }.take(30)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "검색", "Search"), onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = tr(lang, "대화와 메시지 검색", "Search chats and messages")
            )
        }
        if (query.isBlank()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Text(tr(lang, "이렇게 찾아보세요", "Try searching for"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        tr(lang, "제주", "Jeju"),
                        tr(lang, "리뷰", "Review"),
                        tr(lang, "기억", "Memory")
                    ).forEach { term ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(15.dp))
                                .background(p.raised)
                                .clickable { query = term }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(term, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        }
                    }
                }
            }
            EmptyState(
                glyph = Glyph.SPARK,
                title = tr(lang, "무엇을 찾고 있나요?", "What are you looking for?"),
                body = tr(lang, "대화 이름이나 메시지 내용으로 찾을 수 있어요.", "Search by chat name or message text.")
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                if (convResults.isNotEmpty()) {
                    item { SectionLabel(tr(lang, "대화", "Chats"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
                    items(convResults, key = { it.id }) { conv ->
                        val character = state.character(conv.characterId)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChat(conv.id) }.padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistantAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(conv.title(state.characters), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                                Text(
                                    conv.lastMessage()?.text ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (messageResults.isNotEmpty()) {
                    item { SectionLabel(tr(lang, "메시지", "Messages"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
                    items(messageResults.size) { index ->
                        val (conv, message) = messageResults[index]
                        val character = state.character(conv.characterId)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChat(conv.id) }.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AssistantAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(
                                        if (message.sender == MessageSender.USER) tr(lang, "나", "Me") else character?.name ?: "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = p.textPrimary
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(message.createdAt.asRelative(lang), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                                }
                                Text(
                                    highlight(message.text, query),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = p.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (convResults.isEmpty() && messageResults.isEmpty()) {
                    item {
                        EmptyState(
                            glyph = Glyph.SPARK,
                            title = tr(lang, "검색 결과가 없어요", "No results"),
                            body = tr(lang, "다른 단어로 찾아보세요.", "Try a different word.")
                        )
                    }
                }
            }
        }
    }
}

private fun highlight(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
    val lower = text.lowercase()
    val q = query.lowercase()
    var start = lower.indexOf(q)
    while (q.isNotEmpty() && start >= 0) {
        builder.addStyle(
            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF4797EF)),
            start,
            start + query.length
        )
        start = lower.indexOf(q, start + query.length)
    }
    return builder.toAnnotatedString()
}

@Composable
fun NewChatScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onAddFriend: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var query by remember { mutableStateOf("") }
    var showExtra by remember { mutableStateOf(false) }
    val own = state.myCharacter()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "새 대화", "New chat"), onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, tr(lang, "이름으로 찾기", "Search by name"))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.blue.copy(alpha = 0.08f))
                        .clickable { showExtra = true }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.PLUS, p.blue, size = 18.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(lang, "주제별 새 탭 만들기", "Create a topic tab"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(tr(lang, "여행, 공부처럼 대화를 나눠 관리해요", "Keep chats for trips, study, and more"), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                    }
                    Text("›", color = p.textTertiary)
                }
            }
            item { SectionLabel(tr(lang, "나의 도미니온", "My Dominions"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            items(state.characters.filter { it.mine || query.isBlank() || it.name.contains(query, true) }, key = { it.id }) { character ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val conv = vm.directConversationFor(character.id)
                        if (conv != null) onOpenChat(conv)
                    }.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistantAvatar(Color(character.color), character.shape, size = 46.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(character.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(character.tagline, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                    }
                    if (character.online) Box(Modifier.size(7.dp).clip(CircleShape).background(p.success))
                }
            }
            item { SectionLabel(tr(lang, "친구의 도미니온", "Friends' Dominions"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            items(state.friends.filter { it.characterId != null && (query.isBlank() || it.name.contains(query, true)) }, key = { it.id }) { friend ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val characterId = friend.characterId ?: return@clickable
                        val conv = vm.directConversationFor(characterId)
                        if (conv != null) onOpenChat(conv)
                    }.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendAvatar(friend.name, Color(friend.color), 46.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(friend.relation, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                    }
                    Text("›", color = p.textTertiary)
                }
            }
            item {
                SettingsRow(
                    title = tr(lang, "친구 추가", "Add a friend"),
                    subtitle = tr(lang, "아이디나 QR로 친구를 찾아요", "Find friends by ID or QR"),
                    leading = { GlyphIcon(Glyph.PLUS, p.textPrimary, size = 19.dp) },
                    onClick = onAddFriend
                )
            }
            if (own == null) {
                item { EmptyState(Glyph.SPARK, "도미니온이 없어요", "온보딩을 먼저 완료해 주세요.") }
            }
        }
    }

    if (showExtra) {
        TextPromptDialog(
            title = tr(lang, "새 탭 이름", "New tab name"),
            label = tr(lang, "이름", "Name"),
            placeholder = tr(lang, "예: 여행 준비", "e.g. Trip planning"),
            confirmLabel = tr(lang, "만들기", "Create"),
            onConfirm = {
                showExtra = false
                val conv = vm.createExtraConversation(it)
                if (conv.isNotBlank()) onOpenChat(conv)
            },
            onDismiss = { showExtra = false }
        )
    }
}
