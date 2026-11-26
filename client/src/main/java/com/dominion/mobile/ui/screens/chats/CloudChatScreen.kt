package com.dominion.mobile.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.data.remote.CloudChatSession
import com.dominion.mobile.data.remote.CloudModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CloudChatScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember(context.applicationContext) {
        (context.applicationContext as DominionApplication).container.cloudAccount
    }
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    var session by remember { mutableStateOf<CloudChatSession?>(null) }
    var models by remember { mutableStateOf(emptyList<CloudModel>()) }
    var loading by remember { mutableStateOf(true) }
    var online by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(cloud, revision) {
        loading = true
        online = false
        try {
            val result = cloud.refresh()
            online = result.status == "approved"
            models = result.models
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { models = emptyList() }
        try { session = cloud.openChat() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { session = null }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "Cloud 일반 대화", "Cloud chat"), onBack = onBack)
        Text(tr(lang, "무료 모델 · 본체 없이 일반 대화 · 작업 실행은 본체 필요",
            "Free models · No engine required for chat · Tasks require engine"),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (online) tr(lang, "계정 확인됨", "Account verified")
                else tr(lang, "Cloud 연결 미확인", "Cloud not verified"), Modifier.weight(1f))
            TextButton(onClick = { revision++ }, enabled = !loading) { Text(tr(lang, "새로고침", "Refresh")) }
            TextButton(onClick = onAccount) { Text(tr(lang, "계정", "Account")) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val active = session
        if (active == null) {
            if (!loading) {
                Text(tr(lang, "Google 계정 승인 후 사용할 수 있습니다.", "Approve your Google account first."),
                    Modifier.padding(16.dp))
                Button(onClick = onAccount, modifier = Modifier.padding(16.dp)) {
                    Text(tr(lang, "계정 연결", "Connect account"))
                }
            }
        } else key(active) {
            CloudChatContent(active, models, online && !loading, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CloudChatContent(session: CloudChatSession, models: List<CloudModel>, online: Boolean,
    modifier: Modifier = Modifier) {
    val state by session.state.collectAsStateWithLifecycle()
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(state.conversations.firstOrNull()?.id) }
    var model by remember { mutableStateOf("dominion/free-auto") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val options = remember(models) { (listOf(CloudModel("dominion/free-auto", "Auto · Free", true)) +
        models.filter { it.free }).distinctBy { it.id } }
    LaunchedEffect(options) { if (options.none { it.id == model }) model = "dominion/free-auto" }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: retrofit2.HttpException) {
                error = if (e.code() == 429) tr(lang, "무료 이용 한도 또는 요청 제한에 도달했습니다.", "Free quota or rate limit reached.")
                    else tr(lang, "Cloud 응답 오류: ", "Cloud response error: ") + e.code()
            }
            catch (_: Exception) { error = tr(lang, "요청 또는 저장 실패. 자동 재전송하지 않습니다.", "Request or storage failed. No automatic resend.") }
            finally { busy = false }
        }
    }
    val conversation = state.conversation(selected.orEmpty())
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { action { selected = session.newConversation() } }, enabled = !busy) {
                Text(tr(lang, "새 대화", "New chat"))
            }
            state.conversations.forEach { c ->
                FilterChip(selected = c.id == selected, enabled = !busy, onClick = { selected = c.id },
                    label = { Text(c.titleOverride ?: "Cloud", maxLines = 1) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(selected = model == option.id, enabled = !busy,
                    onClick = { model = option.id }, label = { Text(option.name) })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(conversation?.messages.orEmpty().asReversed(), key = { it.id }) { message ->
                val mine = message.sender == MessageSender.USER
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) tr(lang, "나", "Me") else (message.modelId ?: "DOMINION Cloud"),
                        style = MaterialTheme.typography.labelSmall)
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(message.text, Modifier.padding(12.dp))
                    }
                    if (message.status == MessageStatus.FAILED) Text(tr(lang, "응답 미완료", "Response incomplete"),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    if (message.status == MessageStatus.SENDING) Text(tr(lang, "응답 대기", "Waiting for response"),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().imePadding(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = draft, onValueChange = { draft = it },
                label = { Text(tr(lang, "메시지", "Message")) }, modifier = Modifier.weight(1f),
                maxLines = 5, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            Button(enabled = !busy && online && draft.isNotBlank() && draft.length <= 16_000, onClick = {
                val text = draft
                action {
                    val target = selected ?: session.newConversation().also { selected = it }
                    session.send(target, text, model)
                    draft = ""
                }
            }) { Text(tr(lang, "보내기", "Send")) }
        }
    }
}
