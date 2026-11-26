package com.dominion.mobile.ui.screens.chats

import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import com.dominion.mobile.platform.attachments.LocalAttachment
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.CardKind
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageCard
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ChatCardView
import com.dominion.mobile.ui.components.DateDivider
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphButton
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.MessageBubble
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SheetRow
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.TypingBubble
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asDayHeader

private sealed interface ChatEntry {
    data class Day(val label: String) : ChatEntry
    data class Item(val message: Message) : ChatEntry
    data object Typing : ChatEntry
}

@Composable
fun ChatRoomScreen(
    state: AppState,
    vm: AppViewModel,
    conversationId: String,
    onBack: () -> Unit,
    onCharacter: (String) -> Unit,
    onRoomInfo: () -> Unit,
    onSearch: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val conversation = state.conversation(conversationId)
    val character = conversation?.let { state.character(it.characterId) }

    if (conversation == null || character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(conversationId) { vm.refreshConversation(conversationId) }

    var draft by remember(conversationId) { mutableStateOf(conversation.draft) }
    var showAttach by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<Message?>(null) }
    var confirmRewind by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var selectedAttachment by remember(conversationId) { mutableStateOf<LocalAttachment?>(null) }
    var attachmentBusy by remember(conversationId) { mutableStateOf(false) }
    var saveId by remember(conversationId) { mutableStateOf<String?>(null) }

    fun prepareAttachment(uri: Uri) {
        if (attachmentBusy) return
        attachmentBusy = true
        scope.launch {
            try { selectedAttachment = LocalAttachment.fromUri(context.applicationContext, uri) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { context.toast(tr(lang, "파일을 읽을 수 없습니다. 최대 8MiB입니다.", "Could not read file. Maximum 8 MiB.")) }
            finally { attachmentBusy = false }
        }
    }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                draft = listOf(draft, spoken).filter { it.isNotBlank() }.joinToString(" ")
                vm.setDraft(conversationId, draft)
            }
        }
    }
    fun startSpeechInput() {
        runCatching {
            speechInput.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == com.dominion.mobile.data.model.Lang.KO) "ko-KR" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, tr(lang, "메시지를 말해 주세요", "Speak your message"))
            })
        }.onFailure { context.toast(tr(lang, "기기의 음성 인식 서비스를 사용할 수 없습니다.", "Speech recognition unavailable")) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::prepareAttachment)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::prepareAttachment)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null && !attachmentBusy) {
            attachmentBusy = true
            scope.launch {
                try { selectedAttachment = LocalAttachment.fromCameraPreview(bitmap) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { context.toast(tr(lang, "사진을 읽을 수 없습니다.", "Could not read photo.")) }
                finally { attachmentBusy = false }
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { camera.launch(null) }.onFailure {
            context.toast(tr(lang, "카메라 앱을 열 수 없습니다.", "Camera unavailable"))
        } else context.toast(tr(lang, "카메라 권한이 필요합니다.", "Camera permission required"))
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = saveId
        saveId = null
        if (id != null && uri != null) scope.launch {
            try {
                val bytes = vm.downloadAttachment(conversationId, id)
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { it.write(bytes) } }
                context.toast(tr(lang, "원본 파일을 저장했습니다.", "Original file saved"))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { context.toast(tr(lang, "파일 다운로드 또는 저장 실패", "File download or save failed")) }
        }
    }

    val entries = remember(conversation.messages, state.typingConversationId, lang) {
        buildList {
            var lastDay = ""
            conversation.messages.forEach { message ->
                val day = message.createdAt.asDayHeader(lang)
                if (day != lastDay) {
                    add(ChatEntry.Day(day))
                    lastDay = day
                }
                add(ChatEntry.Item(message))
            }
            if (state.typingConversationId == conversationId) add(ChatEntry.Typing)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size, conversation.messages.lastOrNull()?.id, state.typingConversationId) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    fun send() {
        if (attachmentBusy) return
        val attachment = selectedAttachment
        if (attachment != null) {
            val prompt = draft
            attachmentBusy = true
            scope.launch {
                try {
                    vm.sendAttachment(conversationId, prompt, attachment.name, attachment.mimeType, attachment.bytes)
                    selectedAttachment = null
                    draft = ""
                    vm.setDraft(conversationId, "")
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    context.toast(tr(lang, "본체 첨부 실패 · 파일을 다시 전송할 수 있습니다.", "Attachment upload failed · retry sending"))
                } finally { attachmentBusy = false }
            }
            return
        }
        if (draft.isBlank()) return
        vm.sendMessage(conversationId, draft)
        draft = ""
        vm.setDraft(conversationId, "")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.screen)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = p.textPrimary, modifier = Modifier.size(22.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCharacter(character.id) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantAvatar(Color(character.color), character.shape, size = 34.dp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        conversation.title(state.characters),
                        style = MaterialTheme.typography.titleSmall,
                        color = p.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (state.typingConversationId == conversationId) {
                            tr(lang, "입력 중…", "typing…")
                        } else if (character.online) {
                            tr(lang, "지금 대화 가능", "Available now")
                        } else {
                            character.tagline
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (character.online) p.blue else p.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GlyphButton(Glyph.PHONE, onClick = {
                if (vm.startCall(conversationId, false)) onStartCall(false)
                else context.toast(tr(lang, "음성 통화는 연결된 서버에서 사용할 수 있어요", "Voice calls require a connected server"))
            })
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = p.textPrimary, modifier = Modifier.size(21.dp))
            }
        }

        RoomExecutionControls(vm, conversationId)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            items(entries.size) { index ->
                when (val entry = entries[index]) {
                    is ChatEntry.Day -> DateDivider(entry.label)
                    is ChatEntry.Item -> Column {
                        MessageBubble(
                            message = entry.message,
                            character = character,
                            onLongPress = { messageMenu = entry.message },
                            onCardAction = {
                                when (entry.message.card?.kind) {
                                    CardKind.TASK -> onNavigate(com.dominion.mobile.ui.nav.Routes.TASKS)
                                    CardKind.LINK -> {
                                        val link = entry.message.card?.let { card ->
                                            listOf(card.subtitle, card.meta, card.title, entry.message.text)
                                                .firstOrNull { value ->
                                                    runCatching {
                                                        val uri = java.net.URI(value.trim())
                                                        uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
                                                    }.getOrDefault(false)
                                                }
                                        }
                                        if (link == null) context.toast(tr(lang, "유효한 웹 링크가 없습니다.", "No valid web link"))
                                        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()))) }
                                            .onFailure { context.toast(tr(lang, "링크 열기 실패", "Cannot open link")) }
                                    }
                                    CardKind.FILE, CardKind.IMAGE -> {
                                        val id = entry.message.attachmentIds.orEmpty().firstOrNull()
                                        if (id == null) context.toast(tr(lang, "원본 파일 정보 없음", "No original attachment"))
                                        else {
                                            saveId = id
                                            runCatching { saveDocument.launch(entry.message.card?.title ?: "attachment") }.onFailure { saveId = null }
                                        }
                                    }
                                    else -> onNavigate(com.dominion.mobile.ui.nav.Routes.TASKS)
                                }
                            }
                        )
                        if (entry.message.sender == MessageSender.USER &&
                            entry.message.status != MessageStatus.SENT) {
                            Text(
                                text = if (entry.message.status == MessageStatus.FAILED)
                                    tr(lang, "전송 실패 · 눌러서 재시도", "Failed · tap to retry")
                                else tr(lang, "전송 중…", "Sending…"),
                                color = if (entry.message.status == MessageStatus.FAILED) p.blue else p.textTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(end = 12.dp, top = 2.dp)
                                    .then(if (entry.message.status == MessageStatus.FAILED)
                                        Modifier.clickable {
                                            vm.retryMessage(conversationId, entry.message.id)
                                        } else Modifier)
                            )
                        }
                    }
                    ChatEntry.Typing -> TypingBubble(character)
                }
            }
        }

        selectedAttachment?.let { attachment ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.FILE, p.blue, size = 18.dp)
                Text(attachment.name, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.material3.TextButton(enabled = !attachmentBusy, onClick = { selectedAttachment = null }) {
                    Text(tr(lang, "취소", "Remove"))
                }
            }
        }
        if (attachmentBusy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.screen)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(p.raised)
                    .clickable { showAttach = true },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.PLUS, p.textPrimary, size = 20.dp)
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.input)
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicTextField(
                    value = draft,
                    enabled = !attachmentBusy,
                    onValueChange = {
                        draft = it
                        if (it.length % 8 == 0) vm.setDraft(conversationId, it)
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(p.textPrimary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    tr(lang, "도미니온에게 메시지", "Message your Dominion"),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = p.textTertiary
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() && selectedAttachment == null) p.raised else p.blue)
                    .clickable {
                        if (draft.isBlank() && selectedAttachment == null) startSpeechInput()
                        else send()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (draft.isBlank() && selectedAttachment == null) GlyphIcon(Glyph.MIC, p.textSecondary, size = 20.dp)
                else GlyphIcon(Glyph.SEND, Color.White, size = 19.dp)
            }
        }
    }

    if (showAttach) {
        AppBottomSheet(onDismiss = { showAttach = false }) {
            SheetTitle(tr(lang, "무엇을 보낼까요?", "What would you like to send?"))
            SheetRow(tr(lang, "사진", "Photo"), Glyph.IMAGE) {
                showAttach = false
                if (!attachmentBusy) runCatching { photoPicker.launch("image/*") }.onFailure {
                    context.toast(tr(lang, "사진 선택기를 열 수 없습니다.", "Photo picker unavailable"))
                }
            }
            SheetRow(tr(lang, "카메라 (미리보기)", "Camera preview"), Glyph.CAMERA) {
                showAttach = false
                if (!attachmentBusy) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        runCatching { camera.launch(null) }.onFailure { context.toast(tr(lang, "카메라 앱을 열 수 없습니다.", "Camera unavailable")) }
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            SheetRow(tr(lang, "파일", "File"), Glyph.FILE) {
                showAttach = false
                if (!attachmentBusy) runCatching { filePicker.launch("*/*") }.onFailure {
                    context.toast(tr(lang, "파일 선택기를 열 수 없습니다.", "File picker unavailable"))
                }
            }
            SheetRow(tr(lang, "할 일", "Task"), Glyph.TASK) { showAttach = false; onNavigate(com.dominion.mobile.ui.nav.Routes.TASKS) }
            SheetRow(tr(lang, "일정", "Schedule"), Glyph.CALENDAR) { showAttach = false; onNavigate(com.dominion.mobile.ui.nav.Routes.SCHEDULES) }
            SheetRow(tr(lang, "연결", "Connections"), Glyph.PLUG) { showAttach = false; onNavigate(com.dominion.mobile.ui.nav.Routes.CONNECTIONS) }
        }
    }

    if (showMenu) {
        AppBottomSheet(onDismiss = { showMenu = false }) {
            SheetTitle(conversation.title(state.characters), character.tagline)
            SheetRow(tr(lang, "대화방 정보", "Room info"), Glyph.PERSON) { showMenu = false; onRoomInfo() }
            SheetRow(tr(lang, "대화 검색", "Search chat"), Glyph.SEARCH) { showMenu = false; onSearch() }
            SheetRow(
                tr(lang, if (conversation.pinned) "고정 해제" else "고정하기", if (conversation.pinned) "Unpin" else "Pin"),
                Glyph.PIN
            ) { vm.setPinned(conversationId, !conversation.pinned); showMenu = false }
            SheetRow(
                tr(lang, if (conversation.muted) "알림 켜기" else "알림 끄기", if (conversation.muted) "Unmute" else "Mute"),
                Glyph.SPEAKER_OFF
            ) { vm.setMuted(conversationId, !conversation.muted); showMenu = false }
            SheetRow(tr(lang, "대화 되감기 (티켓 1장)", "Rewind (1 ticket)"), Glyph.REWIND) { showMenu = false; confirmRewind = true }
            SheetRow(tr(lang, "대화 비우기", "Clear chat"), Glyph.CLOSE) { showMenu = false; confirmClear = true }
            SheetRow(tr(lang, "신고하기", "Report"), Glyph.SHIELD) {
                showMenu = false
                context.toast(tr(lang, "신고가 접수됐어요 (데모)", "Report received (demo)"))
            }
        }
    }

    messageMenu?.let { message ->
        AppBottomSheet(onDismiss = { messageMenu = null }) {
            SheetTitle(tr(lang, "메시지", "Message"))
            if (message.sender == MessageSender.USER && message.status == MessageStatus.FAILED) {
                SheetRow(tr(lang, "전송 재시도", "Retry sending"), Glyph.SEND) {
                    vm.retryMessage(conversationId, message.id)
                    messageMenu = null
                }
            }
            if (message.text.isNotBlank()) {
                SheetRow(tr(lang, "복사", "Copy"), Glyph.LINK) {
                    clipboard.setText(AnnotatedString(message.text))
                    context.toast(tr(lang, "복사했어요", "Copied"))
                    messageMenu = null
                }
            }
            SheetRow(tr(lang, "기억에 저장", "Save to memories"), Glyph.MEMORY) {
                vm.addMemory(character.id, message.text.ifBlank { message.card?.title.orEmpty() })
                 messageMenu = null
            }
            SheetRow(tr(lang, "이 메시지에서 할 일 만들기", "Create a task"), Glyph.TASK) {
                vm.addTask(
                    title = message.text.take(40).ifBlank { "새 할 일" },
                    characterId = character.id,
                    due = tr(lang, "오늘", "Today"),
                    steps = listOf(tr(lang, "내용 확인", "Review"), tr(lang, "완료하기", "Finish"))
                )
                context.toast(tr(lang, "할 일을 만들었어요", "Task created"))
                messageMenu = null
            }
        }
    }

    if (confirmRewind) {
        PrimaryDialog(
            title = tr(lang, "대화를 되감을까요?", "Rewind this chat?"),
            body = tr(lang, "리셋 티켓 1장을 사용해 마지막 요청으로 돌아가요.", "Uses 1 reset ticket to go back to your last request."),
            confirmLabel = tr(lang, "되감기", "Rewind"),
            onConfirm = {
                confirmRewind = false
                if (!vm.useResetTicket(conversationId)) {
                    context.toast(tr(lang, "리셋 티켓이 없어요", "No reset tickets left"))
                }
            },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmRewind = false }
        )
    }
    if (confirmClear) {
        PrimaryDialog(
            title = tr(lang, "대화를 비울까요?", "Clear this chat?"),
            body = tr(lang, "메시지가 모두 사라져요. 이 작업은 되돌릴 수 없어요.", "All messages will be removed. This cannot be undone."),
            confirmLabel = tr(lang, "비우기", "Clear"),
            onConfirm = { confirmClear = false; vm.clearConversation(conversationId) },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { confirmClear = false },
            destructive = true
        )
    }
}
