package com.dominion.mobile.ui.screens.profile

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Connection
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.CheckCircle
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.Hairline
import com.dominion.mobile.ui.components.FilterChipItem
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SettingsRow
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr

@Composable
fun ConnectionsScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenConnection: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val categories = listOf(tr(lang, "전체", "All"), tr(lang, "연결됨", "Connected"), tr(lang, "기본 도구", "Built-in"))
    val filtered = when (filter) {
        1 -> state.connections.filter { it.connected && !it.builtIn }
        2 -> state.connections.filter { it.builtIn }
        else -> state.connections.filterNot { it.builtIn }
    }.filter { query.isBlank() || it.name.contains(query, true) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "연결", "Connections"),
            subtitle = tr(lang, "도미니온이 사용할 수 있는 도구를 관리해요", "Choose the tools your Dominion can use"),
            onBack = onBack
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 34.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.blue.copy(alpha = 0.08f))
                        .padding(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.PLUG, p.blue, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(tr(lang, "도구 목록과 연결 상태", "Tool catalog and connection status"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text(
                                tr(lang, "실제 연결과 권한은 본체에서 확인합니다.", "Verify connections and permissions on the engine."),
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textTertiary
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    com.dominion.mobile.ui.components.SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = tr(lang, "도구 검색", "Search tools")
                    )
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories.size) { index ->
                        FilterChipItem(categories[index], filter == index, onClick = { filter = index })
                    }
                }
            }
            item { SectionLabel(tr(lang, "도구", "Tools"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(((filtered.size + 2) / 3 * 106).dp.coerceAtLeast(106.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(filtered, key = { it.id }) { connection ->
                        ConnectionTile(connection, onClick = { onOpenConnection(connection.id) })
                    }
                }
            }
            item {
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
                ) {
                    Column {
                        Text(
                            tr(lang, "기본 도구 목록", "Built-in tool catalog"),
                            style = MaterialTheme.typography.labelMedium,
                            color = p.textTertiary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        state.connections.filter { it.builtIn }.forEach { tool ->
                            SettingsRow(
                                title = tool.name,
                                subtitle = tool.scopes.joinToString(" · "),
                                leading = { GlyphIcon(iconForConnection(tool.provider), Color(tool.color), size = 18.dp) },
                                trailing = {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (tool.connected) p.success else p.textTertiary))
                                },
                                onClick = { onOpenConnection(tool.id) }
                            )
                            if (tool != state.connections.filter { it.builtIn }.last()) Hairline()
                        }
                    }
                }
            }
            item {
                Text(
                    tr(
                        lang,
                        "이 목록은 지원 예정 도구를 포함합니다. 실제 인증 상태는 본체에서 확인하세요.",
                        "This catalog includes planned tools. Verify actual authorization on the engine."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textTertiary,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionTile(connection: Connection, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(connection.color).copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(iconForConnection(connection.provider), Color(connection.color), size = 18.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            connection.name,
            style = MaterialTheme.typography.labelSmall,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (connection.connected) tr(lang, "연결됨", "Connected") else connection.category,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (connection.connected) p.blue else p.textTertiary
        )
    }
}

@Composable
fun ConnectionDetailScreen(
    state: AppState,
    vm: AppViewModel,
    connectionId: String,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val connection = state.connections.firstOrNull { it.id == connectionId }
    if (connection == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    var scopes by remember(connection.id, connection.scopes) { mutableStateOf(connection.scopes.toSet()) }
    var sharing by remember { mutableIntStateOf(0) }
    val accent = Color(connection.color)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = connection.name, onBack = onBack)
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(iconForConnection(connection.provider), accent, size = 36.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(connection.name, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary)
                Text(
                    connection.category + " · " + if (connection.builtIn) tr(lang, "기본 도구", "Built-in") else "OAuth",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (connection.connected) p.blue.copy(alpha = 0.12f) else p.raised)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (connection.connected) tr(lang, "연결됨", "Connected") else tr(lang, "연결 안 됨", "Not connected"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connection.connected) p.blue else p.textTertiary
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionLabel(tr(lang, "허용할 권한", "Permissions"))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))) {
                    Column {
                        connection.scopes.forEachIndexed { index, scope ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scopes = if (scope in scopes) scopes - scope else scopes + scope
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CheckCircle(scope in scopes)
                                Spacer(Modifier.width(9.dp))
                                Text(scope, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                            }
                            if (index != connection.scopes.lastIndex) Hairline()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel(tr(lang, "공유 방식", "Sharing"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipItem(tr(lang, "항상 허용", "Always allow"), sharing == 0, onClick = { sharing = 0 })
                    FilterChipItem(tr(lang, "매번 확인", "Ask each time"), sharing == 1, onClick = { sharing = 1 })
                    FilterChipItem(tr(lang, "사용 안 함", "Don't use"), sharing == 2, onClick = { sharing = 2 })
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.cardSoft).padding(14.dp)) {
                    Text(
                        tr(
                            lang,
                            "도미니온은 이 도구를 사용하기 전에 무엇을 할지 먼저 알려드려요. 민감한 작업은 항상 확인을 받아요.",
                            "Your Dominion tells you what it plans to do before using this tool. Sensitive actions always need your confirmation."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textTertiary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(16.dp)) {
                if (connection.builtIn) {
                    Text(
                        tr(lang, "기본 도구의 실행 가능 여부는 본체 설정에서 확인하세요.", "Check engine settings for actual tool availability."),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textTertiary
                    )
                } else {
                    PrimaryButton(
                        text = tr(lang, "본체에서 연결 설정", "Configure on engine"),
                        onClick = {
                            context.toast(tr(lang, "본체 설정에서 실제 서비스 인증이 필요합니다.", "Service authorization must be configured on the engine."))
                            onBack()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(lang, "연결 상태는 내 계정에만 적용돼요.", "The connection applies only to your account."),
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun iconForConnection(provider: String): Glyph = when (provider) {
    "google_calendar", "google_drive", "gmail", "microsoft" -> Glyph.CALENDAR
    "notion", "documents" -> Glyph.FILE
    "slack", "x" -> Glyph.SEND
    "github" -> Glyph.PLUG
    "apple_music" -> Glyph.SPEAKER
    "mercury" -> Glyph.CREDIT
    "location", "maps", "weather" -> Glyph.GLOBE
    "phone_call" -> Glyph.PHONE
    "browser" -> Glyph.LINK
    else -> Glyph.PLUG
}
