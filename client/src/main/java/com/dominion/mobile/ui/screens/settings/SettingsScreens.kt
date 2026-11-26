package com.dominion.mobile.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.TabId
import com.dominion.mobile.data.model.ThemeMode
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.Hairline
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.LanguageToggle
import com.dominion.mobile.ui.components.AppTextField
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SettingsRow
import com.dominion.mobile.ui.components.SwitchRow
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.nav.Routes
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr

@Composable
fun SettingsScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val own = state.myCharacter()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "설정", "Settings"), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Routes.ACCOUNT) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantAvatar(Color(own?.color ?: 0xFF4797EF), own?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 48.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.account.name, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                    Text(state.account.email, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                }
                Text(state.account.plan.label(lang), style = MaterialTheme.typography.labelSmall, color = p.blue)
                Spacer(Modifier.width(4.dp))
                Text("›", color = p.textTertiary)
            }
            Hairline()

            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "프로필 편집", "Edit profile"),
                    leading = { GlyphIcon(Glyph.PERSON, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.EDIT_PROFILE) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "도미니온 꾸미기", "Customize Dominion"),
                    leading = { GlyphIcon(Glyph.SPARK, p.textPrimary, size = 18.dp) },
                    onClick = { own?.let { onNavigate(Routes.customize(it.id)) } }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "플랜과 젤리", "Plans & jelly"),
                    trailingText = state.account.plan.label(lang),
                    leading = { GlyphIcon(Glyph.TICKET, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.CREDITS) }
                )
            }

            SectionLabel("DOMINION Cloud", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "공통 계정 연결", "Connect shared account"),
                    subtitle = tr(lang, "Google 로그인 · 무료 모델 및 이용 한도", "Google sign-in · Free models and quota"),
                    leading = { GlyphIcon(Glyph.GLOBE, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.CLOUD_ACCOUNT) }
                )
                Hairline()
                SettingsRow(title = tr(lang, "Cloud 일반 대화", "Cloud chat"),
                    subtitle = tr(lang, "본체 없이 무료 모델 사용", "Use free models without engine"),
                    onClick = { onNavigate(Routes.CLOUD_CHAT) })
            }

            SectionLabel(tr(lang, "앱", "App"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "알림", "Notifications"),
                    leading = { GlyphIcon(Glyph.BELL, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.NOTIFICATIONS) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "화면", "Appearance"),
                    trailingText = when (state.settings.theme) {
                        ThemeMode.SYSTEM -> tr(lang, "시스템", "System")
                        ThemeMode.LIGHT -> tr(lang, "라이트", "Light")
                        ThemeMode.DARK -> tr(lang, "다크", "Dark")
                    },
                    leading = { GlyphIcon(Glyph.SPARK, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.APPEARANCE) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "언어", "Language"),
                    trailingText = if (state.settings.lang == Lang.KO) "한국어" else "English",
                    leading = { GlyphIcon(Glyph.GLOBE, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.LANGUAGE) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "탭 구성", "Tab layout"),
                    leading = { GlyphIcon(Glyph.CHECK, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.TAB_LAYOUT) }
                )
            }

            SectionLabel(tr(lang, "개인정보와 보안", "Privacy & security"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "개인정보 및 보안", "Privacy & security"),
                    leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.PRIVACY) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "차단한 사용자", "Blocked users"),
                    trailingText = "${state.settings.blockedUserIds.size}",
                    leading = { GlyphIcon(Glyph.LOCK, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.BLOCKED) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "내 데이터 내보내기", "Export my data"),
                    leading = { GlyphIcon(Glyph.FILE, p.textPrimary, size = 18.dp) },
                    onClick = { context.toast(tr(lang, "내보내기 요청을 보냈어요 (데모)", "Export requested (demo)")) }
                )
            }

            SectionLabel(tr(lang, "지원", "Support"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "도움말", "Help"),
                    leading = { GlyphIcon(Glyph.SPARK, p.textPrimary, size = 18.dp) },
                    onClick = { context.toast(tr(lang, "도움말 센터는 백엔드 연결 후 열려요", "Help center opens after backend connection")) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "이용약관", "Terms of service"),
                    leading = { GlyphIcon(Glyph.FILE, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.LEGAL_TERMS) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "개인정보 처리방침", "Privacy policy"),
                    leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.LEGAL_PRIVACY) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "앱 정보", "About"),
                    trailingText = "1.1.20",
                    showChevron = false,
                    leading = { GlyphIcon(Glyph.MEMORY, p.textPrimary, size = 18.dp) },
                    onClick = null
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "로그아웃", "Sign out"),
                    danger = true,
                    showChevron = false,
                    leading = { GlyphIcon(Glyph.SEND, p.danger, size = 18.dp) },
                    onClick = { confirmSignOut = true }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "계정 삭제", "Delete account"),
                    danger = true,
                    showChevron = false,
                    leading = { GlyphIcon(Glyph.CLOSE, p.danger, size = 18.dp) },
                    onClick = { confirmDelete = true }
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "DOMINION Mobile ${com.dominion.mobile.BuildConfig.VERSION_NAME} · ${state.settings.legalVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmSignOut) {
        PrimaryDialog(
            title = tr(lang, "로그아웃할까요?", "Sign out?"),
            body = tr(lang, "이 기기의 데모 데이터가 초기화되고 로그인 화면으로 돌아가요.", "Local demo data resets and you return to sign-in."),
            confirmLabel = tr(lang, "로그아웃", "Sign out"),
            onConfirm = { confirmSignOut = false; vm.resetAllData() },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmSignOut = false },
            destructive = true
        )
    }
    if (confirmDelete) {
        PrimaryDialog(
            title = tr(lang, "계정을 삭제할까요?", "Delete your account?"),
            body = tr(lang, "대화, 기억, 금고가 모두 삭제돼요. 이 작업은 되돌릴 수 없어요.", "Chats, memories and vault items are deleted. This cannot be undone."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { confirmDelete = false; vm.deleteAccount() },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmDelete = false },
            destructive = true
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val p = LocalDominionPalette.current
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))) {
        Column(content = { content() })
    }
}

@Composable
fun AccountScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var name by remember { mutableStateOf(state.account.name) }
    var birthday by remember { mutableStateOf(state.account.birthday) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "계정", "Account"), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            AppTextField(name, { name = it.take(20) }, label = tr(lang, "이름", "Name"))
            Spacer(Modifier.height(8.dp))
            AppTextField(birthday, { birthday = it.take(10) }, label = tr(lang, "생일", "Birthday"), placeholder = "1998.04.12")
            Spacer(Modifier.height(8.dp))
            AppTextField(state.account.phone, {}, label = tr(lang, "전화번호", "Phone"), singleLine = true)
            Spacer(Modifier.height(8.dp))
            AppTextField(state.account.email, {}, label = tr(lang, "이메일", "Email"))
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = tr(lang, "저장", "Save"),
                onClick = {
                    vm.updateAccountProfile(name, birthday)
                    context.toast(tr(lang, "저장했어요", "Saved"))
                }
            )
            Spacer(Modifier.height(22.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "플랜", "Plan"),
                    trailingText = state.account.plan.label(lang),
                    leading = { GlyphIcon(Glyph.TICKET, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.CREDITS) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "비밀번호 및 보안", "Password & security"),
                    leading = { GlyphIcon(Glyph.LOCK, p.textPrimary, size = 18.dp) },
                    onClick = { onNavigate(Routes.PRIVACY) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "로그인한 기기", "Signed-in devices"),
                    trailingText = tr(lang, "1대", "1 device"),
                    leading = { GlyphIcon(Glyph.MEMORY, p.textPrimary, size = 18.dp) },
                    onClick = { context.toast(tr(lang, "이 기기만 로그인되어 있어요", "Only this device is signed in")) }
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun NotificationsSettingsScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "알림", "Notifications"), onBack = onBack)
        SettingsGroup {
            SwitchRow(
                title = tr(lang, "알림 받기", "Allow notifications"),
                subtitle = tr(lang, "새 메시지와 작업 소식을 알려드려요", "New messages and task updates"),
                checked = state.settings.notifications,
                onCheckedChange = { enabled ->
                    vm.setNotifications(enabled)
                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            Hairline()
            SwitchRow(
                title = tr(lang, "메시지 미리보기", "Message preview"),
                subtitle = tr(lang, "잠금 화면에 내용을 표시해요", "Show content on the lock screen"),
                checked = state.settings.messagePreview,
                onCheckedChange = vm::setMessagePreview
            )
            Hairline()
            SwitchRow(
                title = tr(lang, "대화방 소리", "In-chat sounds"),
                subtitle = tr(lang, "메시지를 보낼 때 소리를 재생해요", "Play a sound when sending"),
                checked = state.settings.roomSounds,
                onCheckedChange = vm::setRoomSounds
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            tr(lang, "알림 권한은 기기 설정에서 언제든 바꿀 수 있어요.", "You can change notification permission in system settings."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
fun AppearanceScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "화면", "Appearance"), onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(tr(lang, "테마", "Theme"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to tr(lang, "시스템", "System"),
                    ThemeMode.LIGHT to tr(lang, "라이트", "Light"),
                    ThemeMode.DARK to tr(lang, "다크", "Dark")
                ).forEach { (mode, label) ->
                    val selected = state.settings.theme == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) p.selected else p.cardSoft)
                            .border(1.dp, if (selected) p.textPrimary.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { vm.setTheme(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        SettingsGroup {
            SwitchRow(
                title = tr(lang, "동작 줄이기", "Reduce motion"),
                subtitle = tr(lang, "애니메이션을 줄여요", "Use fewer animations"),
                checked = state.settings.reduceMotion,
                onCheckedChange = vm::setReduceMotion
            )
            Hairline()
            SettingsRow(
                title = tr(lang, "탭 구성", "Tab layout"),
                subtitle = tr(lang, "아래쪽 탭의 순서를 바꿔요", "Reorder the bottom tabs"),
                leading = { GlyphIcon(Glyph.CHECK, p.textPrimary, size = 18.dp) },
                onClick = { onNavigate(Routes.TAB_LAYOUT) }
            )
        }
    }
}

@Composable
fun LanguageScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "언어", "Language"), onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
            LanguageToggle(state.settings.lang, vm::setLanguage)
        }
        SettingsGroup {
            listOf(
                Lang.KO to "한국어",
                Lang.EN to "English"
            ).forEach { (option, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setLanguage(option) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(
                            if (option == Lang.KO) "기본 언어" else "English UI",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary
                        )
                    }
                    if (state.settings.lang == option) Text("✓", color = p.blue, fontWeight = FontWeight.Bold)
                }
                if (option == Lang.KO) Hairline()
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr(lang, "도미니온의 답변 언어도 함께 바뀌어요.", "Your Dominion's reply language changes too."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
fun PrivacyScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "개인정보 및 보안", "Privacy & security"), onBack = onBack)
        SettingsGroup {
            SwitchRow(
                title = tr(lang, "앱 잠금", "App lock"),
                subtitle = tr(lang, "앱을 열 때 지문이나 기기 잠금을 사용해요", "Use biometrics or device lock to open Dominion"),
                checked = state.settings.appLock,
                onCheckedChange = vm::setAppLock
            )
            Hairline()
            SwitchRow(
                title = tr(lang, "알림 내용 가리기", "Hide notification content"),
                subtitle = tr(lang, "알림에 메시지 내용을 표시하지 않아요", "Don't show message text in notifications"),
                checked = state.settings.hideContent,
                onCheckedChange = vm::setHideContent
            )
        }
        Spacer(Modifier.height(14.dp))
        SettingsGroup {
            SettingsRow(
                title = tr(lang, "차단한 사용자", "Blocked users"),
                trailingText = "${state.settings.blockedUserIds.size}",
                leading = { GlyphIcon(Glyph.LOCK, p.textPrimary, size = 18.dp) },
                onClick = { onNavigate(Routes.BLOCKED) }
            )
            Hairline()
            SettingsRow(
                title = tr(lang, "금고", "Vault"),
                leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 18.dp) },
                onClick = { onNavigate(Routes.VAULT) }
            )
            Hairline()
            SettingsRow(
                title = tr(lang, "내 데이터 내보내기", "Export my data"),
                leading = { GlyphIcon(Glyph.FILE, p.textPrimary, size = 18.dp) },
                onClick = { context.toast(tr(lang, "내보내기 요청을 보냈어요 (데모)", "Export requested (demo)")) }
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.cardSoft).padding(14.dp)) {
            Text(
                tr(lang, "대화는 전송 구간과 저장 시 암호화돼요. 금고 항목은 기기에서만 복호화돼요.", "Chats are encrypted in transit and at rest. Vault items are decrypted only on your device."),
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary
            )
        }
    }
}

@Composable
fun BlockedUsersScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "차단한 사용자", "Blocked users"), onBack = onBack)
        if (state.settings.blockedUserIds.isEmpty()) {
            com.dominion.mobile.ui.components.EmptyState(
                glyph = Glyph.LOCK,
                title = tr(lang, "차단한 사용자가 없어요", "No blocked users"),
                body = tr(lang, "차단하면 서로의 도미니온을 볼 수 없어요.", "Blocking hides each other's Dominions.")
            )
        } else {
            SettingsGroup {
                state.settings.blockedUserIds.forEachIndexed { index, id ->
                    SettingsRow(
                        title = id,
                        subtitle = tr(lang, "차단됨", "Blocked"),
                        leading = { GlyphIcon(Glyph.LOCK, p.textPrimary, size = 18.dp) },
                        onClick = { vm.unblockUser(id) },
                        showChevron = false,
                        trailingText = tr(lang, "차단 해제", "Unblock")
                    )
                    if (index != state.settings.blockedUserIds.lastIndex) Hairline()
                }
            }
        }
    }
}

@Composable
fun TabLayoutScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "탭 구성", "Tab layout"), onBack = onBack)
        SettingsGroup {
            state.settings.tabOrder.forEachIndexed { index, tab ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textTertiary,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(tab.label(lang), style = MaterialTheme.typography.titleSmall, color = p.textPrimary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.raised).clickable { vm.moveTab(tab, true) },
                        contentAlignment = Alignment.Center
                    ) { Text("↑", color = if (index == 0) p.textDisabled else p.textSecondary) }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.raised).clickable { vm.moveTab(tab, false) },
                        contentAlignment = Alignment.Center
                    ) { Text("↓", color = if (index == state.settings.tabOrder.lastIndex) p.textDisabled else p.textSecondary) }
                }
                if (index != state.settings.tabOrder.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            PrimaryButton(text = tr(lang, "기본 순서로 되돌리기", "Reset order"), onClick = vm::resetTabOrder)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tr(lang, "채팅, 탐색, 친구, 보관함, 프로필 순서로 표시돼요.", "Chats, Explore, Friends, Archive and Profile are shown in this order."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
fun LegalScreen(document: String, onBack: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val terms = document.contains("terms")
    val title = if (terms) tr(lang, "이용약관", "Terms of service") else tr(lang, "개인정보 처리방침", "Privacy policy")
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text(
                if (terms) tr(lang, "시행일 2026년 11월 1일", "Effective November 1, 2026")
                else tr(lang, "시행일 2026년 11월 1일", "Effective November 1, 2026"),
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary
            )
            Spacer(Modifier.height(16.dp))
            if (terms) {
                LegalSection(
                    "1. 서비스 이용",
                    "이 약관은 Dominion 클라이언트와 관련 서비스의 이용 조건을 정합니다. 사용자는 자신의 계정과 대화 내용을 안전하게 관리할 책임이 있습니다."
                )
                LegalSection(
                    "2. 콘텐츠와 책임",
                    "사용자가 입력한 내용과 도미니온이 생성한 응답은 서비스 이용을 위한 것이며, 중요한 결정에는 별도의 확인이 필요합니다."
                )
                LegalSection(
                    "3. 결제와 환불",
                    "유료 기능의 결제와 환불은 연결된 앱 마켓의 정책을 따릅니다. 데모 빌드에서는 실제 결제가 이루어지지 않습니다."
                )
                LegalSection(
                    "4. 서비스 변경",
                    "서비스의 기능과 정책은 개선을 위해 변경될 수 있으며, 중요한 변경은 앱 안에서 안내합니다."
                )
            } else {
                LegalSection(
                    "1. 수집하는 정보",
                    "계정 정보, 대화 내용, 사용자가 저장한 기억과 금고 항목을 서비스 제공을 위해 처리합니다. 연락처는 친구 찾기에만 사용합니다."
                )
                LegalSection(
                    "2. 보관과 암호화",
                    "대화는 전송 구간과 저장 시 암호화되고, 금고 항목은 기기에서만 복호화됩니다. 보관 기간이 지나면 안전하게 삭제됩니다."
                )
                LegalSection(
                    "3. 제3자 연결",
                    "사용자가 연결한 외부 도구는 명시한 권한 범위에서만 사용하며, 언제든 연결을 해제할 수 있습니다."
                )
                LegalSection(
                    "4. 사용자의 권리",
                    "사용자는 자신의 데이터를 내보내거나 계정을 삭제할 수 있습니다. 삭제된 데이터는 복구할 수 없습니다."
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                tr(lang, "이 문서는 소스 재구성을 위한 예시 문구이며 실제 서비스 약관이 아닙니다.", "This is sample text for the source rebuild, not the live legal document."),
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    val p = LocalDominionPalette.current
    Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
}

@Composable
fun EditProfileScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onCustomize: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val own = state.myCharacter()
    var name by remember { mutableStateOf(state.account.name) }
    var bio by remember { mutableStateOf(own?.bio ?: "") }
    var characterName by remember { mutableStateOf(own?.name ?: "") }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "프로필 편집", "Edit profile"), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AssistantAvatar(
                    Color(own?.color ?: 0xFF4797EF),
                    own?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB,
                    size = 96.dp
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    tr(lang, "사진 대신 도미니온의 색과 모양을 바꿔보세요", "Change your Dominion's color and shape instead of a photo"),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textTertiary
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    tr(lang, "꾸미기 열기", "Open customizer"),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.blue,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onCustomize).padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            AppTextField(name, { name = it.take(20) }, label = tr(lang, "내 이름", "My name"))
            Spacer(Modifier.height(8.dp))
            AppTextField(characterName, { characterName = it.take(12) }, label = tr(lang, "도미니온 이름", "Dominion name"))
            Spacer(Modifier.height(8.dp))
            AppTextField(bio, { bio = it.take(160) }, label = tr(lang, "소개", "Bio"), singleLine = false, maxLines = 4)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = tr(lang, "저장", "Save"),
                onClick = {
                    vm.updateAccountProfile(name, state.account.birthday)
                    own?.let { vm.updateCharacter(it.id, name = characterName.ifBlank { it.name }, bio = bio) }
                    context.toast(tr(lang, "프로필을 저장했어요", "Profile saved"))
                    onBack()
                }
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                SettingsRow(
                    title = tr(lang, "전화번호", "Phone"),
                    trailingText = state.account.phone,
                    leading = { GlyphIcon(Glyph.PHONE, p.textPrimary, size = 18.dp) },
                    onClick = { context.toast(tr(lang, "전화번호 변경은 백엔드 연결 후 가능해요", "Phone changes need the backend")) }
                )
                Hairline()
                SettingsRow(
                    title = tr(lang, "이메일", "Email"),
                    trailingText = state.account.email,
                    leading = { GlyphIcon(Glyph.FILE, p.textPrimary, size = 18.dp) },
                    onClick = null
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
