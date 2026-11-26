package com.dominion.mobile.ui.screens.profile

import androidx.compose.runtime.DisposableEffect
import com.dominion.mobile.platform.security.rememberDeviceAuthentication
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.VaultItem
import com.dominion.mobile.data.model.VaultType
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphButton
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.FilterChipItem
import com.dominion.mobile.ui.components.AppTextField
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asRelative

@Composable
fun VaultScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<VaultItem?>(null) }
    var revealed by remember { mutableStateOf(emptySet<String>()) }

    DisposableEffect(vm) { onDispose { vm.lockVault() } }
    val authenticate = rememberDeviceAuthentication(
        title = tr(lang, "DOMINION 금고 잠금 해제", "Unlock DOMINION vault"),
        onSuccess = vm::unlockVault,
        onError = { context.toast(it) }
    )
    if (!state.vaultUnlocked) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenTopBar(title = tr(lang, "금고", "Vault"), onBack = onBack)
            Spacer(Modifier.weight(0.7f))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(p.raised),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.LOCK, p.textPrimary, size = 42.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(tr(lang, "금고가 잠겨 있어요", "Vault is locked"), style = MaterialTheme.typography.headlineSmall, color = p.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                tr(lang, "지문이나 기기 잠금으로 열 수 있어요.", "Unlock with biometrics or your device lock."),
                style = MaterialTheme.typography.bodyMedium,
                color = p.textTertiary
            )
            Spacer(Modifier.height(26.dp))
            Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                PrimaryButton(
                    text = tr(lang, "지문으로 열기", "Unlock with biometrics"),
                    onClick = authenticate
                )
            }
            Spacer(Modifier.weight(1f))
        }
        return
    }

    val tabs = listOf(tr(lang, "전체", "All")) + VaultType.entries.map { it.label(lang) }
    val items = if (filter == 0) state.vault else state.vault.filter { it.type == VaultType.entries[filter - 1] }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "금고", "Vault"),
            subtitle = tr(lang, "${state.vault.size}개의 항목 · 기기에서 암호화됨", "${state.vault.size} items · encrypted on device"),
            onBack = onBack,
            actions = {
                GlyphButton(Glyph.LOCK, onClick = { vm.lockVault() })
                GlyphButton(Glyph.PLUS, onClick = { showAdd = true })
            }
        )
        Column {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF111318), Color(0xFF292E39))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(Glyph.SHIELD, Color.White, size = 20.dp) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(lang, "나만 볼 수 있는 금고", "A vault only you can open"), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            tr(lang, "민감한 정보는 암호화되어 저장돼요.", "Sensitive details are stored encrypted."),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.size) { index ->
                    FilterChipItem(tabs[index], filter == index, onClick = { filter = index })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp, top = 8.dp)) {
            items(items, key = { it.id }) { item ->
                val isRevealed = item.id in revealed
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.raised),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(
                            when (item.type) {
                                VaultType.LOGIN -> Glyph.LOCK
                                VaultType.CARD -> Glyph.CREDIT
                                VaultType.ADDRESS -> Glyph.PIN
                                VaultType.PHONE -> Glyph.PHONE
                            },
                            p.textSecondary,
                            size = 18.dp
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(
                            if (item.username.isNotBlank()) item.username else item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isRevealed) {
                            Text(
                                item.secret,
                                style = MaterialTheme.typography.bodyMedium,
                                color = p.textPrimary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                revealed = if (isRevealed) revealed - item.id else revealed + item.id
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(if (isRevealed) Glyph.EYE_OFF else Glyph.EYE, p.textTertiary, size = 17.dp)
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                clipboard.setText(AnnotatedString(item.secret))
                                context.toast(tr(lang, "복사했어요", "Copied"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.COPY, p.textTertiary, size = 17.dp)
                    }
                    Text(
                        "⋯",
                        color = p.textTertiary,
                        modifier = Modifier.clickable { deleteTarget = item }.padding(horizontal = 4.dp)
                    )
                }
            }
            if (items.isEmpty()) {
                item {
                    com.dominion.mobile.ui.components.EmptyState(
                        glyph = Glyph.LOCK,
                        title = tr(lang, "금고가 비어 있어요", "Your vault is empty"),
                        body = tr(lang, "로그인, 카드, 주소를 안전하게 보관해보세요.", "Keep logins, cards and addresses safe."),
                        actionLabel = tr(lang, "항목 추가", "Add item"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddVaultSheet(
            onDismiss = { showAdd = false },
            onAdd = { type, title, username, secret, note ->
                vm.addVaultItem(type, title, username, secret, note)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { item ->
        PrimaryDialog(
            title = tr(lang, "${item.title}을(를) 삭제할까요?", "Delete ${item.title}?"),
            body = tr(lang, "금고에서 완전히 삭제돼요.", "It will be permanently removed from your vault."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteVaultItem(item.id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun AddVaultSheet(
    onDismiss: () -> Unit,
    onAdd: (VaultType, String, String, String, String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var type by remember { mutableStateOf(VaultType.LOGIN) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AppBottomSheet(onDismiss = onDismiss) {
        SheetTitle(tr(lang, "금고에 추가", "Add to vault"), tr(lang, "이 정보는 암호화되어 저장돼요.", "This item is stored encrypted."))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VaultType.entries) { option ->
                FilterChipItem(option.label(lang), type == option, onClick = { type = option })
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            AppTextField(title, { title = it }, label = tr(lang, "이름", "Name"), placeholder = tr(lang, "예: 네이버", "e.g. Naver"))
            Spacer(Modifier.height(8.dp))
            AppTextField(username, { username = it }, label = tr(lang, "아이디 또는 번호", "ID or number"), placeholder = tr(lang, "선택", "Optional"))
            Spacer(Modifier.height(8.dp))
            AppTextField(secret, { secret = it }, label = tr(lang, "보관할 내용", "Secret"), placeholder = tr(lang, "비밀번호, 카드 번호 등", "Password, card number…"), isPassword = true)
            Spacer(Modifier.height(8.dp))
            AppTextField(note, { note = it }, label = tr(lang, "메모", "Note"), placeholder = tr(lang, "선택", "Optional"))
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = tr(lang, "저장", "Save"),
                enabled = title.isNotBlank() && secret.isNotBlank(),
                onClick = { onAdd(type, title, username, secret, note) }
            )
        }
    }
}
