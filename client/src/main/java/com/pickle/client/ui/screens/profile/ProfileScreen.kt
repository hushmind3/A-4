package com.pickle.client.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.Plan
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.BalanceTile
import com.pickle.client.ui.components.CountPill
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.Hairline
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.LevelBar
import com.pickle.client.ui.components.SectionLabel
import com.pickle.client.ui.components.SettingsRow
import com.pickle.client.ui.nav.Routes
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr

@Composable
fun ProfileScreen(
    state: AppState,
    onNavigate: (String) -> Unit,
    onOpenCharacter: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val own = state.myCharacter()

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
                    Text(tr(lang, "프로필", "Profile"), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Text(
                        tr(lang, "나의 피클과 계정을 관리해요", "Manage your Pickle and account"),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textTertiary
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(p.raised).clickable { onNavigate(Routes.SETTINGS) },
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 19.dp)
                }
            }
        }

        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(own?.color ?: 0xFF4797EF).copy(alpha = 0.24f),
                                Color(own?.color ?: 0xFF4797EF).copy(alpha = 0.08f),
                                p.cardSoft
                            )
                        )
                    )
                    .border(0.5.dp, p.hairline, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JaumAvatar(
                            Color(own?.color ?: 0xFF4797EF),
                            own?.shape ?: com.pickle.client.data.model.CharacterShape.BLOB,
                            size = 64.dp,
                            level = own?.level
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.account.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = p.textPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    state.account.plan.label(lang),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = p.blue,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(p.blue.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                "${own?.name ?: "Pickle"} · ${tr(lang, "나의 피클", "My Pickle")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.actionPrimary)
                                .clickable { onNavigate(Routes.EDIT_PROFILE) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(tr(lang, "편집", "Edit"), style = MaterialTheme.typography.labelSmall, color = p.onActionPrimary)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LevelBar(
                        level = state.account.level,
                        xp = state.account.xp,
                        goal = state.account.xpGoal
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            tr(lang, "꾸미기", "Customize") to Routes.customize(own?.id ?: ""),
                            tr(lang, "기억", "Memories") to Routes.MEMORIES,
                            tr(lang, "레벨 보상", "Rewards") to Routes.CREDITS
                        ).forEach { (label, route) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(p.card)
                                    .clickable { onNavigate(route) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceTile(Glyph.JELLY, tr(lang, "젤리", "Jelly"), "${state.account.jelly}", Color(0xFFFFB86B), Modifier.weight(1f)) { onNavigate(Routes.CREDITS) }
                    BalanceTile(Glyph.BOLT, tr(lang, "에너지", "Energy"), "${state.account.energy}/${state.account.energyMax}", p.blue, Modifier.weight(1f)) { onNavigate(Routes.CREDITS) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceTile(Glyph.TICKET, tr(lang, "티켓", "Tickets"), "${state.account.tickets}", Color(0xFF8B7CF6), Modifier.weight(1f)) { onNavigate(Routes.CREDITS) }
                    BalanceTile(Glyph.CREDIT, tr(lang, "크레딧", "Credits"), "${state.account.credits}", Color(0xFF3FB6A8), Modifier.weight(1f)) { onNavigate(Routes.CREDITS) }
                }
            }
        }

        item { SectionLabel(tr(lang, "나의 피클", "My Pickles"), Modifier.padding(horizontal = 20.dp)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.characters, key = { it.id }) { character ->
                    Column(
                        Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(p.cardSoft)
                            .clickable { onOpenCharacter(character.id) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        JaumAvatar(Color(character.color), character.shape, size = 52.dp)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            character.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = p.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            tr(lang, "Lv.${character.level}", "Lv.${character.level}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = p.textTertiary
                        )
                    }
                }
            }
        }

        item { SectionLabel(tr(lang, "바로가기", "Shortcuts"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTile(Glyph.MEMORY, tr(lang, "기억", "Memories"), Modifier.weight(1f)) { onNavigate(Routes.MEMORIES) }
                QuickTile(Glyph.TASK, tr(lang, "할 일", "Tasks"), Modifier.weight(1f)) { onNavigate(Routes.TASKS) }
                QuickTile(Glyph.CALENDAR, tr(lang, "일정", "Schedule"), Modifier.weight(1f)) { onNavigate(Routes.SCHEDULES) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTile(Glyph.LOCK, tr(lang, "금고", "Vault"), Modifier.weight(1f)) { onNavigate(Routes.VAULT) }
                QuickTile(Glyph.PLUG, tr(lang, "연결", "Connections"), Modifier.weight(1f)) { onNavigate(Routes.CONNECTIONS) }
                QuickTile(Glyph.GIFT, tr(lang, "초대", "Invite"), Modifier.weight(1f)) { onNavigate(Routes.INVITE) }
            }
        }

        item {
            Column(Modifier.padding(top = 16.dp)) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
                ) {
                    Column {
                        SettingsRow(
                            title = tr(lang, "받은 소식", "Inbox"),
                            subtitle = tr(lang, "친구 요청과 작업 소식",
                                "Friend requests and task updates"),
                            leading = { GlyphIcon(Glyph.BELL, p.textPrimary, size = 18.dp) },
                            trailing = {
                                val unread = state.unreadActivity()
                                if (unread > 0) CountPill(unread)
                            },
                            onClick = { onNavigate(Routes.INBOX) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "친구 초대", "Invite friends"),
                            subtitle = tr(lang, "함께 쓸 친구를 초대해요", "Invite friends to Pickle"),
                            leading = { GlyphIcon(Glyph.GIFT, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.INVITE) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "플랜과 젤리", "Plans & jelly"),
                            subtitle = "${state.account.plan.label(lang)} · ${state.account.jelly} ${tr(lang, "젤리", "jelly")}",
                            leading = { GlyphIcon(Glyph.TICKET, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.CREDITS) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "알림", "Notifications"),
                            leading = { GlyphIcon(Glyph.BELL, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.NOTIFICATIONS) }
                        )
                        Hairline()
                        SettingsRow(
                            title = tr(lang, "설정", "Settings"),
                            leading = { GlyphIcon(Glyph.SHIELD, p.textPrimary, size = 18.dp) },
                            onClick = { onNavigate(Routes.SETTINGS) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            Text(
                "Pickle Client 1.1.20-source · ${tr(lang, "데모 데이터", "demo data")}",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QuickTile(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalPicklePalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.cardSoft)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlyphIcon(glyph, p.textPrimary, size = 20.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
    }
}
