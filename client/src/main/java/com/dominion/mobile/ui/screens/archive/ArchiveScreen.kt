package com.dominion.mobile.ui.screens.archive

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import com.dominion.mobile.data.ArchiveIndex
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.platform.attachments.ArchiveShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onOpenChat: (String) -> Unit,
    vm: AppViewModel,
    roomId: String? = null,
    onBack: (() -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val tabs = listOf(
        tr(lang, "전체", "All"),
        tr(lang, "사진", "Photos"),
        tr(lang, "파일", "Files"),
        tr(lang, "링크", "Links"),
        tr(lang, "영상", "Videos")
    )
    var selected by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ArchiveItem?>(null) }
    var viewing by remember { mutableStateOf<ArchiveItem?>(null) }
    var search by remember { mutableStateOf("") }
    var offlineOnly by remember { mutableStateOf(false) }
    var offlineIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cacheRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm, state.archive, cacheRevision) {
        try { offlineIds = vm.offlineArchiveIds() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { offlineIds = emptySet() }
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ArchiveItem?>(null) }
    val allItems = if (roomId == null) state.archive else
        ArchiveIndex.roomItems(state.conversation(roomId)?.messages.orEmpty())
    LaunchedEffect(roomId) { if (roomId != null) vm.refreshConversationNow(roomId) }
    val items = (when (selected) {
        1 -> allItems.filter { it.type == ArchiveType.IMAGE }
        2 -> allItems.filter { it.type == ArchiveType.FILE }
        3 -> allItems.filter { it.type == ArchiveType.LINK }
        4 -> allItems.filter { it.type == ArchiveType.VIDEO }
        else -> allItems
    }).filter { (!offlineOnly || it.id in offlineIds) &&
        (search.isBlank() || (it.title + " " + it.meta + " " + it.mimeType.orEmpty()).contains(search, true)) }
    suspend fun bytesFor(item: ArchiveItem): ByteArray =
        if (item.type == ArchiveType.LINK) requireNotNull(ArchiveIndex.safeUrl(item.url)).toByteArray(Charsets.UTF_8)
        else vm.readArchiveAttachment(item)
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = pendingSave
        pendingSave = null
        if (uri != null && item != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val bytes = bytesFor(item)
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                    }
                    context.toast(tr(lang, "원본을 기기에 저장했습니다.", "Original saved to device."))
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { context.toast(tr(lang, "파일 저장 실패 · 본체 연결과 원본을 확인하세요.", "Save failed; check engine and original.")) }
                finally { busy = false }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(p.screen)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹") }
            Column(Modifier.weight(1f)) {
                Text(if (roomId == null) tr(lang, "보관함", "Archive") else tr(lang, "공유된 파일과 링크", "Shared files and links"), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
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
        Column(Modifier.padding(horizontal = 16.dp)) {
            com.dominion.mobile.ui.components.AppTextField(search, { search = it.take(200) },
                placeholder = tr(lang, "파일·링크 검색", "Search files and links"))
            FilterChipItem(tr(lang, "오프라인 저장만", "Offline only"), offlineOnly, onClick = { offlineOnly = !offlineOnly })
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
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    ArchiveCard(item, offline = item.id in offlineIds, onClick = { detail = item })
                }
            }
        }
    }

    viewing?.let { item ->
        AttachmentViewerDialog(item, load = { bytesFor(item) }, onDismiss = { viewing = null })
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
            if (item.type == ArchiveType.LINK && ArchiveIndex.safeUrl(item.url) != null) {
                SheetRow(tr(lang, "웹 링크 열기", "Open web link"), Glyph.LINK) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                        .onFailure { context.toast(tr(lang, "링크 열기 실패", "Could not open link")) }
                }
            }
            if (item.type != ArchiveType.LINK && item.attachmentId != null) {
                SheetRow(tr(lang, "실제 파일 열기", "Open actual file"), Glyph.FILE) {
                    detail = null
                    viewing = item
                }
            }
            if (item.attachmentId != null && state.archive.any { it.id == item.id }) {
                SheetRow(if (item.id in offlineIds) "오프라인 사본 삭제" else "오프라인 열람용으로 저장", Glyph.FILE) {
                    if (!busy) {
                        val remove = item.id in offlineIds
                        busy = true
                        detail = null
                        scope.launch {
                            try {
                                if (remove) vm.unpinArchiveAttachment(item) else vm.pinArchiveAttachment(item)
                                cacheRevision++
                                context.toast(if (remove) "기기 사본을 삭제했습니다." else "암호화된 오프라인 사본 저장 완료")
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { context.toast("오프라인 저장·삭제 실패 · 연결 및 저장 공간을 확인하세요.") }
                            finally { busy = false }
                        }
                    }
                }
            }
            SheetRow(tr(lang, "기기에 저장", "Save to device"), Glyph.FILE) {
                if (!busy && pendingSave == null) {
                    pendingSave = item
                    runCatching { saveDocument.launch(if (item.type == ArchiveType.LINK) "link.txt" else ArchiveShare.safeName(item.title)) }
                        .onFailure { pendingSave = null; context.toast(tr(lang, "파일 선택기 열기 실패", "Could not open document picker")) }
                    detail = null
                }
            }
            SheetRow(tr(lang, "공유", "Share"), Glyph.LINK) {
                if (!busy) {
                    busy = true
                    detail = null
                    scope.launch {
                        try {
                            val intent = if (item.type == ArchiveType.LINK) {
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, requireNotNull(ArchiveIndex.safeUrl(item.url)))
                            } else {
                                val bytes = bytesFor(item)
                                withContext(Dispatchers.IO) { ArchiveShare.fileIntent(context, item, bytes) }
                            }
                            context.startActivity(Intent.createChooser(intent, tr(lang, "공유", "Share")))
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { context.toast(tr(lang, "원본 공유 실패", "Could not share original")) }
                        finally { busy = false }
                    }
                }
            }
            if (roomId != null) SheetRow(tr(lang, "보관함에 저장", "Save to archive"), Glyph.FILE) {
                val message = state.conversation(roomId)?.messages?.firstOrNull { it.id == item.messageId }
                if (message != null) vm.archiveMessage(message)
                else context.toast(tr(lang, "원본 메시지를 찾을 수 없습니다.", "Original message unavailable"))
                detail = null
            }
        }
    }
}

@Composable
private fun ArchiveCard(item: ArchiveItem, offline: Boolean = false, onClick: () -> Unit) {
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
            if (offline) "오프라인 · " + item.meta else item.meta,
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
