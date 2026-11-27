package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.*

private fun operationStatus(entry: MobileJournalEntry): String = when (entry.receipt?.status) {
    "accepted" -> "서버 접수"
    "review" -> "검토·승인 대기"
    "running" -> "실행 중"
    "completed" -> "서버 완료 · 증빙 확인"
    "failed" -> "서버 실패"
    "cancelled" -> "취소됨"
    else -> "전송 결과 미확인 · 요청 번호로 조회"
}

@Composable
private fun OperationRows(entries: List<MobileJournalEntry>, busy: Boolean,
    onRefresh: (String) -> Unit, onReview: (MobileJournalEntry) -> Unit) {
    entries.take(30).forEach { entry ->
        HorizontalDivider()
        Text(entry.intent.action + " · " + entry.intent.target, style = MaterialTheme.typography.titleSmall)
        Text(operationStatus(entry), style = MaterialTheme.typography.bodyMedium)
        Text(entry.intent.nonce, style = MaterialTheme.typography.labelSmall)
        entry.receipt?.detail?.let { Text(it.take(2000)) }
        entry.receipt?.evidenceId?.let { Text("서버 증빙 번호: " + it) }
        entry.receipt?.evidenceUrl?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        Row {
            TextButton(enabled = !busy, onClick = { onRefresh(entry.intent.nonce) }) { Text("상태 재조회") }
            if (entry.receipt?.status == "review" && !entry.decisionSent)
                TextButton(enabled = !busy, onClick = { onReview(entry) }) { Text("검토") }
        }
        if (entry.decisionSent && entry.receipt?.status == "review")
            Text("승인 전달 여부를 확인 중입니다. 자동 재전송하지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OperationReview(entry: MobileJournalEntry, busy: Boolean,
    onDecide: (String, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val review = entry.receipt?.review ?: return
    var confirm by remember(entry.intent.nonce, review.digest) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("서버 요청 검토") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(review.summary.orEmpty())
                if (review.requiresFreshAuth) Text("서버에서 본인 인증을 완료한 뒤 상태를 재조회하세요. 앱에 인증 비밀을 입력하지 마세요.")
                if (entry.intent.action == MobileAction.ACCOUNT_DELETE.wire)
                    Text("삭제는 복구 불가능할 수 있습니다. 위 계정과 삭제 범위를 확인하세요.", color = MaterialTheme.colorScheme.error)
                Row {
                    Checkbox(confirm, { confirm = it }, enabled = !busy && !review.requiresFreshAuth)
                    Text("검토한 내용으로 실행 승인", modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = confirm && !busy && !review.requiresFreshAuth &&
            review.expiresAt > System.currentTimeMillis() && review.digest != null,
            onClick = { onDecide(entry.intent.nonce, requireNotNull(review.digest), true) }) { Text("실행 승인") } },
        dismissButton = { Row {
            TextButton(enabled = !busy && review.digest != null && review.expiresAt > System.currentTimeMillis(),
                onClick = { onDecide(entry.intent.nonce, requireNotNull(review.digest), false) }) { Text("거부") }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") }
        } })
}

/** Account deletion and institution execution share the same durable, reviewed protocol.
 * No local deletion, payment or receipt is simulated. */
@Composable
fun MobileOperationsDialog(vm: AppViewModel, action: MobileAction, title: String,
    target: String?, text: String? = null, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var caps by remember { mutableStateOf<MobileCapabilities?>(null) }
    var entries by remember { mutableStateOf<List<MobileJournalEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf<MobileJournalEntry?>(null) }
    suspend fun reload() {
        caps = null; entries = emptyList()
        val current = vm.mobileCapabilities()
        val feature = if (action == MobileAction.ACCOUNT_DELETE &&
            action.feature !in current.features.orEmpty()) "account_delete_receipts" else action.feature
        val owner = current.requireFeature(feature)
        val rows = vm.mobileJournal().filter { it.intent.action == action.wire }
        check(vm.mobileCapabilities().requireFeature(feature) == owner)
        caps = current
        entries = rows.filter { it.intent.accountId == owner }
    }
    fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block(); reload() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                error = "본체 기능·연결 또는 접수 확인 필요. 전송한 요청은 기록에서 재조회하세요."
                try { reload() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            } finally { busy = false }
        }
    }
    LaunchedEffect(vm, action) { runOperation {} }
    val owner = caps?.accountId
    val canSubmit = action.feature in caps?.features.orEmpty()
    val actualTarget = if (action == MobileAction.ACCOUNT_DELETE) owner else target
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owner == null) Text("연결된 본체가 이 기능을 지원하고 실제 계정을 확인해야 활성화됩니다.")
                else Text("서버 계정: " + owner)
                if (owner != null && !canSubmit) Text("삭제 접수 기록 조회 전용 세션입니다.")
                if (action == MobileAction.ACCOUNT_DELETE)
                    Text("삭제 요청은 서버의 대상·범위·본인 인증 검토를 거칩니다. 로컬 앱 초기화와는 별도입니다.")
                else Text("기관·요청 내용을 서버에 전달해 검토안을 받습니다. 승인 없이 신청·결제 완료로 표시하지 않습니다.")
                text?.let { Text(it.take(1200), style = MaterialTheme.typography.bodySmall) }
                TextButton(enabled = !busy, onClick = { runOperation {} }) { Text("연결·지원 기능 다시 확인") }
                Row {
                    Checkbox(checked, { checked = it }, enabled = owner != null && canSubmit && !busy)
                    Text("대상과 서버 요청 내용 확인", modifier = Modifier.padding(top = 12.dp))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                OperationRows(entries, busy, { nonce -> runOperation { vm.refreshMobileOperation(nonce) } },
                    { reviewed = it })
            }
        },
        confirmButton = { TextButton(enabled = checked && !busy && canSubmit && actualTarget != null && owner != null &&
            entries.none { it.intent.target == actualTarget && it.receipt?.terminal() != true },
            onClick = { runOperation { vm.beginMobileOperation(action, requireNotNull(actualTarget), text); checked = false } }) {
            Text("서버 검토 요청")
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } })
    reviewed?.let { entry ->
        OperationReview(entry, busy, { nonce, digest, allow ->
            runOperation { vm.decideMobileOperation(nonce, digest, allow); reviewed = null }
        }, { reviewed = null })
    }
}

@Composable
fun ServerFriendsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var caps by remember { mutableStateOf<MobileCapabilities?>(null) }
    var peers by remember { mutableStateOf<List<MobilePeer>>(emptyList()) }
    var entries by remember { mutableStateOf<List<MobileJournalEntry>>(emptyList()) }
    var peer by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<MobilePeerMessage>>(emptyList()) }
    var invite by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<MobileAction, String>?>(null) }
    suspend fun reload() {
        caps = null; peers = emptyList(); entries = emptyList(); messages = emptyList()
        val current = vm.mobileCapabilities()
        val owner = current.requireFeature("friends")
        val directory = vm.mobilePeers()
        val rows = vm.mobileJournal().filter { it.intent.action.startsWith("friend.") || it.intent.action == "peer.message" }
        check(vm.mobileCapabilities().requireFeature("friends") == owner)
        caps = current
        peers = directory
        entries = rows.filter { it.intent.accountId == owner }
        val active = peer
        if (active != null && "peer_messages" in current.features.orEmpty() &&
            peers.any { it.id == active && it.relationship == "friend" }) {
            val history = vm.mobilePeerHistory(active)
            check(vm.mobileCapabilities().requireFeature("peer_messages") == owner)
            messages = history
        } else peer = null
    }
    fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block(); reload() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                error = "서버 연결·지원 기능·접수 상태를 확인하세요. 요청을 자동 반복하지 않습니다."
                try { reload() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            } finally { busy = false }
        }
    }
    LaunchedEffect(vm) { runOperation {} }
    LaunchedEffect(peer, busy) {
        val id = peer ?: return@LaunchedEffect
        if (busy) return@LaunchedEffect
        while (isActive) {
            delay(3500)
            try {
                val current = vm.mobileCapabilities()
                check(current.requireFeature("peer_messages") == caps?.accountId)
                val history = vm.mobilePeerHistory(id)
                check(vm.mobileCapabilities().requireFeature("peer_messages") == caps?.accountId)
                messages = history
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "메시지 동기화 중단 · 다시 조회하세요."; break }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("서버 친구 · 메시지") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(caps?.accountId?.let { "서버 계정: " + it } ?: "본체의 실제 친구 서비스 연결 필요")
                TextButton(enabled = !busy, onClick = { runOperation {} }) { Text("친구·접수 기록 새로고침") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (peer == null) {
                    OutlinedTextField(invite, { invite = it.take(200) }, label = { Text("친구 계정 ID 또는 핸들") },
                        singleLine = true, enabled = caps != null && !busy)
                    TextButton(enabled = caps != null && !busy && validMobileId(invite.trim()), onClick = {
                        confirm = MobileAction.INVITE to invite.trim()
                    }) { Text("친구 요청") }
                    peers.forEach { row ->
                        val id = requireNotNull(row.id)
                        HorizontalDivider()
                        Text(row.displayName.orEmpty() + " · " + row.relationship, style = MaterialTheme.typography.titleSmall)
                        Text(row.handle.orEmpty() + " / " + id, style = MaterialTheme.typography.labelSmall)
                        when (row.relationship) {
                            "incoming" -> Row {
                                TextButton(enabled = !busy, onClick = { confirm = MobileAction.ACCEPT to id }) { Text("수락") }
                                TextButton(enabled = !busy, onClick = { confirm = MobileAction.DECLINE to id }) { Text("거절") }
                            }
                            "friend" -> Row {
                                TextButton(enabled = !busy && "peer_messages" in caps?.features.orEmpty(),
                                    onClick = { peer = id; runOperation {} }) { Text("메시지") }
                                TextButton(enabled = !busy, onClick = { confirm = MobileAction.REMOVE to id }) { Text("삭제") }
                                TextButton(enabled = !busy, onClick = { confirm = MobileAction.BLOCK to id }) { Text("차단") }
                            }
                        }
                    }
                } else {
                    val target = requireNotNull(peer)
                    TextButton(enabled = !busy, onClick = { peer = null; messages = emptyList(); draft = "" }) { Text("친구 목록") }
                    Text(peers.firstOrNull { it.id == target }?.displayName ?: target)
                    messages.forEach { msg ->
                        Text((if (msg.from == caps?.accountId) "나: " else "상대: ") + msg.text.orEmpty())
                    }
                    OutlinedTextField(draft, { draft = it.take(8000) }, label = { Text("메시지") }, maxLines = 4)
                    TextButton(enabled = !busy && draft.isNotBlank() &&
                        entries.none { it.intent.action == MobileAction.MESSAGE.wire && it.intent.target == target &&
                            it.receipt?.terminal() != true }, onClick = {
                        val outgoing = draft.trim()
                        runOperation { vm.beginMobileOperation(MobileAction.MESSAGE, target, outgoing); draft = "" }
                    }) { Text("서버 전송") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                OperationRows(entries.filter { peer == null || it.intent.target == peer }, busy,
                    { nonce -> runOperation { vm.refreshMobileOperation(nonce) } }, {})
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } })
    confirm?.let { (action, target) ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("친구 변경 확인") },
            text = { Text(action.wire + " · " + target) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                confirm = null; runOperation { vm.beginMobileOperation(action, target); invite = "" }
            }) { Text("서버 요청") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } })
    }
}
