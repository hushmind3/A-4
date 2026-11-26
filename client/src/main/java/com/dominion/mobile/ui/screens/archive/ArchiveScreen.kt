package com.dominion.mobile.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.ArchiveItem
import com.dominion.mobile.data.model.ArchiveType
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.EmptyState
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.FilterChipItem
import com.dominion.mobile.ui.components.SheetRow
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import androidx.compose.ui.platform.LocalContext

@Composable
fun ArchiveScreen(
    state: AppState,
    onOpenChat: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val tabs = listOf(
        tr(lang, "전체", "All"),
        tr(lang, "사진", "Photos"),
        tr(lang, "파일", "Files"),
        tr(lang, "링크", "Links")
    )
    var selected by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ArchiveItem?>(null) }
    val items = when (selected) {
        1 -> state.archive.filter { it.type == ArchiveType.IMAGE }
        2 -> state.archive.filter { it.type == ArchiveType.FILE }
        3 -> state.archive.filter { it.type == ArchiveType.LINK }
        else -> state.archive
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(p.screen)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(lang, "보관함", "Archive"), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                Text(
                    tr(lang, "대화에서 저장한 사진과 파일", "Photos and files saved from chats"),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(p.raised),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.IMAGE, p.textPrimary, size = 19.dp)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs.size) { index ->
                FilterChipItem(tabs[index], selected == index, onClick = { selected = index })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptyState(
                glyph = Glyph.IMAGE,
                title = tr(lang, "보관한 항목이 없어요", "Nothing saved yet"),
                body = tr(lang, "대화에서 사진이나 파일을 길게 눌러 보관해보세요.", "Long-press a photo or file in a chat to save it here.")
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ArchiveCard(item, onClick = { detail = item })
                }
            }
        }
    }

    detail?.let { item ->
        AppBottomSheet(onDismiss = { detail = null }) {
            SheetTitle(item.title, item.meta)
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(item.color).copy(alpha = 0.55f), Color(item.color).copy(alpha = 0.18f), p.cardSoft)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(
                    when (item.type) {
                        ArchiveType.IMAGE -> Glyph.IMAGE
                        ArchiveType.VIDEO -> Glyph.VIDEO
                        ArchiveType.FILE -> Glyph.FILE
                        ArchiveType.LINK -> Glyph.LINK
                    },
                    Color.White.copy(alpha = 0.9f),
                    size = 48.dp
                )
            }
            Spacer(Modifier.height(8.dp))
            val conv = item.conversationId?.let { state.conversation(it) }
            SheetRow(tr(lang, "대화에서 보기", "Open in chat"), Glyph.SPARK) {
                detail = null
                if (conv != null) onOpenChat(conv.id)
                else context.toast(tr(lang, "대화를 찾을 수 없어요", "Chat not found"))
            }
            SheetRow(tr(lang, "기기에 저장", "Save to device"), Glyph.FILE) {
                context.toast(tr(lang, "저장했어요 (데모)", "Saved (demo)"))
                detail = null
            }
            SheetRow(tr(lang, "공유", "Share"), Glyph.LINK) {
                context.toast(tr(lang, "공유 시트는 연결 후 사용할 수 있어요", "Sharing needs the backend"))
                detail = null
            }
        }
    }
}

@Composable
private fun ArchiveCard(item: ArchiveItem, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    val accent = Color(item.color)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.36f), accent.copy(alpha = 0.10f), p.cardSoft)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                when (item.type) {
                    ArchiveType.IMAGE -> Glyph.IMAGE
                    ArchiveType.VIDEO -> Glyph.VIDEO
                    ArchiveType.FILE -> Glyph.FILE
                    ArchiveType.LINK -> Glyph.LINK
                },
                accent,
                size = 30.dp
            )
            if (item.type == ArchiveType.VIDEO) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelMedium,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.meta,
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
