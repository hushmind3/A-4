package com.pickle.client.ui.screens.friends

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
import androidx.compose.runtime.LaunchedEffect
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
import com.pickle.client.data.model.Friend
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.FriendAvatar
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.PrimaryDialog
import com.pickle.client.ui.components.PrimaryButton
import com.pickle.client.ui.components.SectionLabel
import com.pickle.client.ui.components.SettingsRow
import com.pickle.client.ui.components.toast
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import androidx.compose.ui.platform.LocalContext

@Composable
fun FriendsScreen(
    state: AppState,
    vm: AppViewModel,
    onOpenFriend: (String) -> Unit,
    onAddFriend: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val own = state.myCharacter()
    var removeTarget by remember { mutableStateOf<Friend?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().background(p.screen),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "친구", "Friends"), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Text(
                        tr(lang, "${state.friends.size}명의 친구와 피클을 나누고 있어요", "Sharing Pickles with ${state.friends.size} friends"),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textTertiary
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(p.raised).clickable(onClick = onAddFriend),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.PLUS, p.textPrimary, size = 20.dp)
                }
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(own?.color ?: 0xFF4797EF).copy(alpha = 0.18f), p.cardSoft)
                        )
                    )
                    .clickable(onClick = onOpenProfile)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JaumAvatar(
                    Color(own?.color ?: 0xFF4797EF),
                    own?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB,
                    size = 48.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${state.account.name} · @minji", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    Text(
                        tr(lang, "내 피클 ${own?.name ?: ""} 과 함께해요", "With my Pickle ${own?.name ?: ""}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.actionPrimary)
                        .clickable {
                            context.toast(tr(lang, "초대 링크를 복사했어요 (데모)", "Invite link copied (demo)"))
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(tr(lang, "초대", "Invite"), style = MaterialTheme.typography.labelSmall, color = p.onActionPrimary)
                }
            }
        }

        if (state.friendRequests.isNotEmpty()) {
            item { SectionLabel(tr(lang, "친구 요청", "Friend requests"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
            items(state.friendRequests, key = { it.id }) { request ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendAvatar(request.name, Color(request.color), 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(request.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(request.message, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.actionPrimary)
                            .clickable { vm.acceptFriendRequest(request.id) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(tr(lang, "수락", "Accept"), style = MaterialTheme.typography.labelSmall, color = p.onActionPrimary)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.raised)
                            .clickable { vm.declineFriendRequest(request.id) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(tr(lang, "거절", "Decline"), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.cardSoft)
                    .clickable {
                        context.toast(tr(lang, "주소록 연동은 백엔드 연결 후 동작해요", "Contacts sync needs the backend"))
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(Glyph.MEMORY, p.blue, size = 18.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "주소록에서 친구 찾기", "Find friends from contacts"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    Text(tr(lang, "이미 피클을 쓰는 친구를 찾아드려요", "Find friends already on Pickle"), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                }
                Text("›", color = p.textTertiary)
            }
        }

        item { SectionLabel(tr(lang, "친구 목록", "All friends"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        items(state.friends, key = { it.id }) { friend ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFriend(friend.id) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(friend.name, Color(friend.color), 46.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        if (friend.isPickle) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Pickle",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = p.blue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(p.blue.copy(alpha = 0.10f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text("${friend.handle} · ${friend.relation}", style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                }
                if (friend.favorite) {
                    Text("★", color = p.toggleOn, fontSize = 15.sp)
                }
                Text("›", color = p.textTertiary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    removeTarget?.let { friend ->
        PrimaryDialog(
            title = tr(lang, "${friend.name}님을 삭제할까요?", "Remove ${friend.name}?"),
            body = tr(lang, "친구 목록에서 사라지고 서로의 피클을 볼 수 없게 돼요.", "They disappear from your friends and can no longer see your Pickle."),
            confirmLabel = tr(lang, "삭제", "Remove"),
            onConfirm = { removeTarget = null; vm.removeFriend(friend.id) },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { removeTarget = null },
            destructive = true
        )
    }
}

@Composable
fun AddFriendScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val results = state.friends.filter {
        query.isNotBlank() && (it.name.contains(query, true) || it.handle.contains(query, true))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(p.screen).navigationBarsPadding()) {
        com.pickle.client.ui.components.PickleTopBar(
            title = tr(lang, "친구 추가", "Add friend"),
            onBack = onBack
        )
        Column(Modifier.padding(horizontal = 18.dp)) {
            com.pickle.client.ui.components.SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = tr(lang, "이름 또는 아이디로 찾기", "Search by name or ID")
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.cardSoft)
                    .border(0.5.dp, p.hairline, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(p.raised),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.QR, p.textPrimary, size = 34.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr(lang, "QR 코드로 친구 추가", "Add by QR code"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tr(lang, "카메라 연결 후 사용할 수 있어요", "Available after camera is connected"),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textTertiary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(tr(lang, "친구 아이디", "Friend ID"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.input).padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text(
                        if (query.isBlank()) "@" else query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (query.isBlank()) p.textTertiary else p.textPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    text = tr(lang, "추가", "Add"),
                    onClick = {
                        if (query.isBlank()) {
                            context.toast(tr(lang, "아이디를 입력해 주세요", "Enter an ID first"))
                        } else {
                            vm.addFriendByHandle(query)
                            query = ""
                            context.toast(tr(lang, "친구를 추가했어요", "Friend added"))
                        }
                    },
                    modifier = Modifier.width(96.dp)
                )
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                results.forEach { friend ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendAvatar(friend.name, Color(friend.color), 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(friend.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text(friend.handle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                        }
                        Text(tr(lang, "이미 친구", "Added"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            SettingsRow(
                title = tr(lang, "주소록에서 찾기", "Find from contacts"),
                subtitle = tr(lang, "연락처와 매칭해 친구를 찾아요", "Match contacts to find friends"),
                leading = { GlyphIcon(Glyph.PERSON, p.textPrimary, size = 19.dp) },
                onClick = { context.toast(tr(lang, "주소록 연동은 백엔드 연결 후 동작해요", "Contacts sync needs the backend")) }
            )
            SettingsRow(
                title = tr(lang, "초대 링크 보내기", "Share invite link"),
                subtitle = "pickle.com/u/${state.account.inviteCode.lowercase()}",
                leading = { GlyphIcon(Glyph.LINK, p.textPrimary, size = 19.dp) },
                onClick = { context.toast(tr(lang, "초대 링크를 복사했어요", "Invite link copied")) }
            )
        }
    }
}

@Composable
fun FriendProfileScreen(
    state: AppState,
    vm: AppViewModel,
    friendId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val friend = state.friends.firstOrNull { it.id == friendId }
    if (friend == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(p.screen)) {
        com.pickle.client.ui.components.PickleTopBar(
            title = friend.name,
            subtitle = friend.handle,
            onBack = onBack
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            FriendAvatar(friend.name, Color(friend.color), 92.dp)
            Spacer(Modifier.height(14.dp))
            Text(friend.name, style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
            Text(
                "${friend.handle} · ${friend.relation}",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textTertiary
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.actionPrimary)
                        .clickable {
                            val characterId = friend.characterId
                            if (characterId != null) {
                                val conv = vm.directConversationFor(characterId)
                                if (conv != null) onOpenChat(conv)
                            } else {
                                context.toast(tr(lang, "이 친구는 아직 피클을 공유하지 않았어요", "This friend has not shared a Pickle yet"))
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(tr(lang, "피클에게 말 걸기", "Chat with their Pickle"), style = MaterialTheme.typography.labelMedium, color = p.onActionPrimary)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.raised)
                        .clickable { vm.toggleFavoriteFriend(friend.id) }
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(
                        if (friend.favorite) tr(lang, "즐겨찾기 해제", "Unfavorite") else tr(lang, "즐겨찾기", "Favorite"),
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
        }
        SettingsRow(
            title = tr(lang, "함께하는 피클", "Shared Pickle"),
            subtitle = friend.characterId?.let { state.character(it)?.name } ?: tr(lang, "공유하지 않음", "Not shared"),
            leading = { GlyphIcon(Glyph.SPARK, p.textPrimary, size = 19.dp) }
        )
        SettingsRow(
            title = tr(lang, "보관함에서 함께 본 항목", "Shared archive"),
            subtitle = tr(lang, "사진 2개 · 링크 1개", "2 photos · 1 link"),
            leading = { GlyphIcon(Glyph.IMAGE, p.textPrimary, size = 19.dp) }
        )
        SettingsRow(
            title = tr(lang, "차단하기", "Block"),
            leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 19.dp) },
            onClick = {
                vm.blockUser(friend.id)
                context.toast(tr(lang, "차단했어요", "Blocked"))
                onBack()
            }
        )
        SettingsRow(
            title = tr(lang, "친구 삭제", "Remove friend"),
            danger = true,
            showChevron = false,
            leading = { GlyphIcon(Glyph.CLOSE, p.danger, size = 19.dp) },
            onClick = { confirmRemove = true }
        )

        if (confirmRemove) {
            PrimaryDialog(
                title = tr(lang, "친구를 삭제할까요?", "Remove friend?"),
                body = tr(lang, "서로의 피클을 더 이상 볼 수 없어요.", "You will no longer see each other's Pickles."),
                confirmLabel = tr(lang, "삭제", "Remove"),
                onConfirm = { confirmRemove = false; vm.removeFriend(friend.id); onBack() },
                dismissLabel = tr(lang, "취소", "Cancel"),
                onDismiss = { confirmRemove = false },
                destructive = true
            )
        }
    }
}
