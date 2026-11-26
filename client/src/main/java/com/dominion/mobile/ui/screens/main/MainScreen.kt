package com.dominion.mobile.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominion.mobile.data.ConnectionState
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Conversation
import com.dominion.mobile.data.model.ConversationKind
import com.dominion.mobile.data.model.TabId
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.CountPill
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.Hairline
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SheetRow
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.TabIcon
import com.dominion.mobile.ui.components.TextPromptDialog
import com.dominion.mobile.ui.nav.Routes
import com.dominion.mobile.ui.screens.archive.ArchiveScreen
import com.dominion.mobile.ui.screens.chats.ChatsScreen
import com.dominion.mobile.ui.screens.explore.ExploreScreen
import com.dominion.mobile.ui.screens.friends.FriendsScreen
import com.dominion.mobile.ui.screens.profile.ProfileScreen
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    state: AppState,
    vm: AppViewModel,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val connection by vm.connection.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewTab by remember { mutableStateOf(false) }
    var sharedPicker by remember { mutableStateOf(false) }
    val sharedFiles by vm.sharedFiles.collectAsStateWithLifecycle()
    val sharedCaption by vm.sharedCaption.collectAsStateWithLifecycle()
    val shareBusy by vm.shareBusy.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }
    fun go(route: String) {
        onNavigate(route)
        closeDrawer()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.screen,
                drawerContentColor = p.textPrimary,
                modifier = Modifier.width(312.dp)
            ) {
                DrawerContent(
                    state = state,
                    onTab = { tab ->
                        vm.selectTab(tab)
                        closeDrawer()
                    },
                    onOpenChat = { id -> go(Routes.chat(id)) },
                    onNewTab = { showNewTab = true },
                    onSettings = { go(Routes.SETTINGS) },
                    onInvite = { go(Routes.INVITE) },
                    onEditProfile = { go(Routes.EDIT_PROFILE) },
                    onDeleteConversation = { deleteTarget = it }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(p.screen)) {
            Scaffold(
                containerColor = p.screen,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Column {
                        state.call?.takeIf { it.minimized }?.let { call ->
                            MinimizedCallBar(
                                state = state,
                                onOpen = { onNavigate(Routes.CALL) },
                                onEnd = { vm.endCall() }
                            )
                        }
                        BottomBar(
                            state = state,
                            onSelect = { vm.selectTab(it) }
                        )
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    ConnectionBanner(connection, vm::retryServerConnection, { onNavigate(Routes.ENGINE_SETTINGS) })
                    if ("assistant" in state.hiddenRooms.orEmpty()) {
                        androidx.compose.material3.TextButton(onClick = vm::restoreHiddenRooms) {
                            Text(tr(lang, "숨긴 기본 비서방 다시 표시", "Show hidden assistant room"))
                        }
                    }
                    if (state.sharedText != null || sharedFiles.isNotEmpty()) {
                        val preview = listOfNotNull(if (sharedFiles.isNotEmpty()) sharedCaption else state.sharedText,
                            sharedFiles.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }).joinToString(" · ")
                        SharedBanner(
                            text = if (shareBusy) tr(lang, "공유 준비 중…", "Preparing share…") else preview,
                            onDismiss = { vm.clearSharedText() },
                            onSend = { if (!shareBusy) sharedPicker = true }
                        )
                    }
                    when (state.currentTab) {
                        TabId.CHATS -> ChatsScreen(
                            state = state,
                            onOpenDrawer = ::openDrawer,
                            onOpenChat = { onNavigate(Routes.chat(it)) },
                            onSearch = { onNavigate(Routes.SEARCH) },
                            onInbox = { onNavigate(Routes.INBOX) },
                            onNewChat = { onNavigate(Routes.NEW_CHAT) },
                            onCharacter = { onNavigate(Routes.character(it)) },
                            onPin = vm::setPinned,
                            onMute = vm::setMuted,
                            onRename = vm::renameConversation,
                            onDelete = vm::deleteConversation,
                            onMarkRead = vm::markConversationRead
                        )
                        TabId.EXPLORE -> ExploreScreen(
                            state = state,
                            vm = vm,
                            onOpenChat = { onNavigate(Routes.chat(it)) }
                        )
                        TabId.FRIENDS -> FriendsScreen(
                            state = state,
                            vm = vm,
                            onOpenFriend = { onNavigate(Routes.friend(it)) },
                            onAddFriend = { onNavigate(Routes.ADD_FRIEND) },
                            onOpenProfile = { onNavigate(Routes.EDIT_PROFILE) }
                        )
                        TabId.ARCHIVE -> ArchiveScreen(
                            state = state, vm = vm,
                            onOpenChat = { onNavigate(Routes.chat(it)) }
                        )
                        TabId.PROFILE -> ProfileScreen(
                            state = state,
                            onNavigate = onNavigate,
                            onOpenCharacter = { onNavigate(Routes.character(it)) }
                        )
                    }
                }
            }
        }
    }

    if (showNewTab) {
        TextPromptDialog(
            title = tr(lang, "새 탭 만들기", "Create a new tab"),
            label = tr(lang, "이름", "Name"),
            placeholder = tr(lang, "예: 여행 준비", "e.g. Trip planning"),
            confirmLabel = tr(lang, "만들기", "Create"),
            onConfirm = {
                showNewTab = false
                vm.createExtraConversation(it) { conv -> onNavigate(Routes.chat(conv)) }
            },
            onDismiss = { showNewTab = false }
        )
    }

    if (sharedPicker) {
        AppBottomSheet(onDismiss = { sharedPicker = false }) {
            SheetTitle(
                tr(lang, "어디로 보낼까요?", "Where should it go?"),
                tr(lang, "선택한 방으로만 공유합니다.", "Only send to the selected chat.")
            )
            (if (sharedFiles.isNotEmpty()) sharedCaption else state.sharedText)?.let { Text(it.take(500), modifier = Modifier.padding(horizontal = 20.dp)) }
            sharedFiles.forEach { file ->
                Text(file.name + " · " + file.bytes.size.toString() + " bytes", modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp))
            }
            if (shareBusy) Text(tr(lang, "첨부 준비 중…", "Preparing attachments…"), modifier = Modifier.padding(16.dp))
            if (state.conversations.isEmpty()) Text(tr(lang, "본체 연결 후 대화방을 먼저 만드세요.", "Connect the engine and create a chat first."), modifier = Modifier.padding(16.dp))
            state.conversations.forEach { conv ->
                val character = state.character(conv.characterId)
                if (character != null) {
                    SheetRow(
                        "${conv.title(state.characters)} · ${character.tagline}",
                        Glyph.SEND
                    ) {
                        if (!shareBusy) vm.shareToConversation(conv.id) {
                            sharedPicker = false
                            onNavigate(Routes.chat(conv.id))
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { conv ->
        PrimaryDialog(
            title = tr(lang, "대화를 삭제할까요?", "Delete this chat?"),
            body = tr(lang, "기본 비서방은 기기에서 숨기고, 다른 방은 본체에서 제거합니다. 기존 기록은 본체에 보존됩니다.", "Hide the default assistant locally; remove other rooms from the engine. Prior history is preserved."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = {
                vm.deleteConversation(conv.id)
                deleteTarget = null
            },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun BottomBar(
    state: AppState,
    onSelect: (TabId) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.screen)
            .navigationBarsPadding()
    ) {
        Hairline(start = 0.dp)
        Row(
            Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.settings.tabOrder.forEach { tab ->
                val selected = state.currentTab == tab
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box {
                        TabIcon(
                            tab = tab,
                            selected = selected,
                            tint = if (selected) p.textPrimary else p.textTertiary,
                            size = 23.dp
                        )
                        if (tab == TabId.CHATS && state.unreadChats() > 0) {
                            Box(Modifier.align(Alignment.TopEnd).padding(start = 16.dp)) {
                                CountPill(state.unreadChats(), p.danger)
                            }
                        }
                        if (tab == TabId.PROFILE && state.unreadActivity() > 0) {
                            Box(Modifier.align(Alignment.TopEnd).padding(start = 16.dp)) {
                                CountPill(state.unreadActivity(), p.danger)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label(lang),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) p.textPrimary else p.textTertiary,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    state: AppState,
    onTab: (TabId) -> Unit,
    onOpenChat: (String) -> Unit,
    onNewTab: () -> Unit,
    onSettings: () -> Unit,
    onInvite: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteConversation: (Conversation) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val own = state.myCharacter()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditProfile)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistantAvatar(
                Color(own?.color ?: 0xFF4797EF),
                own?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB,
                size = 48.dp
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(state.account.name, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text(
                    "${state.account.plan.label(lang)} · @minji",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary
                )
            }
            Text("›", color = p.textTertiary)
        }
        Hairline()

        TabId.entries.forEach { tab ->
            DrawerTabRow(
                tab = tab,
                selected = state.currentTab == tab,
                badge = when (tab) {
                    TabId.CHATS -> state.unreadChats()
                    TabId.PROFILE -> state.unreadActivity()
                    else -> 0
                },
                onClick = { onTab(tab) }
            )
        }

        Spacer(Modifier.height(10.dp))
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr(lang, "내 탭", "My tabs"),
                style = MaterialTheme.typography.labelMedium,
                color = p.textTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                tr(lang, "+ 추가", "+ Add"),
                style = MaterialTheme.typography.labelSmall,
                color = p.blue,
                modifier = Modifier.clickable(onClick = onNewTab).padding(4.dp)
            )
        }
        val extras = state.conversations.filter { it.kind == ConversationKind.EXTRA }
        if (extras.isEmpty()) {
            Text(
                tr(lang, "여행 준비처럼 주제별 대화 탭을 만들 수 있어요.", "Create topic tabs like trip planning."),
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        } else {
            extras.forEach { conv ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(conv.id) }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.SPARK, p.textTertiary, size = 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        conv.title(state.characters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "⋯",
                        color = p.textTertiary,
                        modifier = Modifier.clickable { onDeleteConversation(conv) }.padding(6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        DrawerSmallRow(Glyph.GIFT, tr(lang, "친구 초대", "Invite friends"), onInvite)
        DrawerSmallRow(Glyph.LOCK, tr(lang, "설정", "Settings"), onSettings)

        Spacer(Modifier.height(20.dp))
        Text(
            "DOMINION " + com.dominion.mobile.BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun DrawerTabRow(
    tab: TabId,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabIcon(tab, selected, if (selected) p.textPrimary else p.textTertiary, size = 21.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            tab.label(lang),
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (selected) p.textPrimary else p.textSecondary,
            modifier = Modifier.weight(1f)
        )
        if (badge > 0) CountPill(badge, p.danger)
    }
}

@Composable
private fun DrawerSmallRow(glyph: Glyph, label: String, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, p.textSecondary, size = 17.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
    }
}

@Composable
private fun SharedBanner(text: String, onDismiss: () -> Unit, onSend: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.blue.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.SEND, p.blue, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            tr(lang, "공유된 내용: ", "Shared: ") + text,
            style = MaterialTheme.typography.bodySmall,
            color = p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            tr(lang, "보내기", "Send"),
            style = MaterialTheme.typography.labelSmall,
            color = p.blue,
            modifier = Modifier.clickable(onClick = onSend).padding(6.dp)
        )
        Text(
            "✕",
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
            modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp)
        )
    }
}

@Composable
private fun MinimizedCallBar(
    state: AppState,
    onOpen: () -> Unit,
    onEnd: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val stateContext = androidx.compose.ui.platform.LocalContext.current
    val call = state.call ?: return
    val character = state.character(call.characterId) ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF171A20))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(Color(character.color), character.shape, size = 30.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(lang, "${character.name} · 음성 세션", "${character.name} · Voice session"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Text(when (call.phase) {
                com.dominion.mobile.data.model.CallPhase.WAITING_PEER -> tr(lang, "상대 대기 중", "Waiting for peer")
                com.dominion.mobile.data.model.CallPhase.PEER_PRESENT -> tr(lang, "상대 접속", "Peer joined")
                com.dominion.mobile.data.model.CallPhase.RECONNECTING -> tr(lang, "재연결 중", "Reconnecting")
                com.dominion.mobile.data.model.CallPhase.FAILED -> tr(lang, "연결 실패", "Connection failed")
                else -> tr(lang, "연결 중", "Connecting")
            }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFEE1717)).clickable {
                onEnd()
                com.dominion.mobile.platform.audio.CallForegroundService.stop(stateContext)
            },
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.PHONE, Color.White, size = 16.dp, modifier = Modifier.rotate(135f))
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, onRetry: () -> Unit, onSettings: () -> Unit) {
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    if (state == ConnectionState.CONNECTED) return
    val message = when (state) {
        ConnectionState.CONNECTING -> tr(lang, "서버 연결 중", "Connecting to server")
        ConnectionState.OFFLINE -> tr(lang, "서버 연결 끊김 · 저장된 대화", "Offline · cached conversations")
        ConnectionState.AUTH_REQUIRED -> tr(lang, "서버 인증 필요", "Server authorization required")
        ConnectionState.UNAVAILABLE -> tr(lang, "서버 응답 오류", "Server response unavailable")
        ConnectionState.CONNECTED -> return
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().background(p.blue.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.textPrimary)
        if (state == ConnectionState.AUTH_REQUIRED) {
            Text(tr(lang, "서버 연결", "Connect"), Modifier.clickable(onClick = onSettings).padding(6.dp), color = p.blue)
        } else if (state != ConnectionState.CONNECTING) {
            Text(tr(lang, "다시 연결", "Retry"), Modifier.clickable(onClick = onRetry).padding(6.dp), color = p.blue,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
