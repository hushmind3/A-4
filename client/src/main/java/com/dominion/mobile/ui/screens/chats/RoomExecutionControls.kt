package com.dominion.mobile.ui.screens.chats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reuses the server's durable room, job, approval and operation records. */
@Composable
fun RoomExecutionControls(vm: AppViewModel, roomId: String,
    onPreview: (List<LiveTurn>) -> Unit = {}) {
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
    }
    val gate = remember(roomId) { Mutex() }
    var snapshot by remember(roomId) { mutableStateOf<RoomExecution?>(null) }
    var failure by remember(roomId) { mutableStateOf(false) }
    var streamConnected by remember(roomId) { mutableStateOf(false) }
    var resultNotice by remember(roomId) { mutableStateOf("") }
    var open by remember(roomId) { mutableStateOf(false) }
    var busy by remember(roomId) { mutableStateOf(false) }
    var review by remember(roomId) { mutableStateOf<EngineApproval?>(null) }
    var cancel by remember(roomId) { mutableStateOf<EngineJob?>(null) }

    suspend fun refresh() {
        gate.withLock {
            try {
                // Read the actual transcript before matching the job's message ID.
                vm.refreshConversationNow(roomId)
                snapshot = vm.roomExecution(roomId)
                failure = false
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure = true }
        }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        resultNotice = ""
        scope.launch {
            try {
                gate.withLock { block() }
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                resultNotice = tr(lang, "요청 처리 결과를 확인하지 못했습니다. 다시 조회하세요.", "Request not verified. Refresh before retrying.")
                refresh()
            } finally { busy = false }
        }
    }
    LaunchedEffect(roomId, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                // Independent periodic reconciliation covers dropped events and older engines.
                delay(if (snapshot?.status in setOf("queued", "running") ||
                    snapshot?.approvals.orEmpty().isNotEmpty()) 4000L else 15000L)
            }
        }
    }
    val session = snapshot?.sessionId
    val latestPreview by rememberUpdatedState(onPreview)
    LaunchedEffect(roomId, session, lifecycle) {
        latestPreview(emptyList())
        if (session.isNullOrBlank() || vm.localExecution) return@LaunchedEffect
        val reducer = RoomLiveReducer(roomId, session)
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var reconnectMs = 1000L
            var observedVersion = -1L
            val invalidations = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
            val refreshJob = launch {
                for (ignored in invalidations) {
                    refresh()
                    delay(250)
                }
            }
            try {
                while (true) {
                    try {
                        vm.roomLiveUpdates(reducer).collect { update ->
                            streamConnected = update.connected
                            reconnectMs = 1000L
                            latestPreview(update.turns)
                            if (observedVersion != update.refreshVersion) {
                                observedVersion = update.refreshVersion
                                invalidations.trySend(Unit)
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* polling remains active */ }
                    streamConnected = false
                    latestPreview(emptyList())
                    delay(reconnectMs)
                    reconnectMs = (reconnectMs * 2).coerceAtMost(30000L)
                }
            } finally {
                streamConnected = false
                latestPreview(emptyList())
                refreshJob.cancel()
                invalidations.close()
            }
        }
    }
    val current = snapshot
    val approvals = current?.approvals.orEmpty()
    fun statusLabel(status: String?) = when (status) {
        "queued" -> tr(lang, "대기", "Queued")
        "running" -> tr(lang, "실행 중", "Running")
        "completed" -> tr(lang, "완료", "Completed")
        "failed" -> tr(lang, "실패", "Failed")
        "cancelled" -> tr(lang, "중단됨", "Cancelled")
        "needs_review" -> tr(lang, "검토 필요", "Needs review")
        "interrupted" -> tr(lang, "중단된 실행", "Interrupted")
        "paused" -> tr(lang, "일시 정지", "Paused")
        "stopped" -> tr(lang, "정지", "Stopped")
        "idle" -> tr(lang, "대기 중", "Idle")
        else -> status ?: tr(lang, "조회 중", "Checking")
    }
    Row(Modifier.fillMaxWidth().background(p.cardSoft).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (failure) tr(lang, "실행 상태 조회 실패", "Execution status unavailable")
                else statusLabel(current?.status), color = p.textPrimary, style = MaterialTheme.typography.labelMedium)
            if (!failure && current?.status=="running")Text(tr(lang,"요청한 일을 처리하고 있어요","Working on your request"),
                color=p.textSecondary,style=MaterialTheme.typography.labelSmall)
            if (approvals.isNotEmpty()) Text(tr(lang, "승인 대기 ", "Pending approvals ") + approvals.size,
                color = p.blue, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { open = true; scope.launch { refresh() } }) {
            Text(tr(lang, "작업 확인", "Task status"))
        }
    }
    if (open) AppBottomSheet(onDismiss = { open = false }) {
        SheetTitle(tr(lang, "작업 결과 및 승인", "Results and approvals"))
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (failure) tr(lang, "연결 실패 · 표시된 정보는 이전 조회 결과입니다.", "Connection failed · last retrieved data")
                else statusLabel(current?.status))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!notificationsAllowed) OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ) { Text(tr(lang, "작업 알림 허용", "Enable background job alerts")) }
            OutlinedButton(enabled = !busy, onClick = { scope.launch { refresh() } }) { Text(tr(lang, "새로고침", "Refresh")) }
            if (resultNotice.isNotBlank()) Text(resultNotice)
            Text(tr(lang, "사용자 승인", "User approvals"), style = MaterialTheme.typography.titleMedium)
            if (approvals.isEmpty()) Text(tr(lang, "대기 중인 승인 없음", "No pending approvals"))
            approvals.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(item.tool.orEmpty(), modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !busy && !failure && item.actionableIn(current?.sessionId.orEmpty()),
                        onClick = { review = item }) { Text(tr(lang, "내용 확인", "Review")) }
                }
            }
            HorizontalDivider()
            Text(tr(lang, "작업 기록", "Jobs"), style = MaterialTheme.typography.titleMedium)
            current?.jobs.orEmpty().forEach { job ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(job.input?.input?.take(240).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(statusLabel(job.status) + if (job.stepTotal > 0)
                        " · ${job.stepIndex}/${job.stepTotal}" else "", color = p.textSecondary)
                    if (job.error != null && !job.error.isJsonNull)
                        Text(job.error.toString().take(800), style = MaterialTheme.typography.bodySmall)
                    if (job.status in setOf("queued", "running") && !job.id.isNullOrBlank())
                        TextButton(enabled = !busy && !failure, onClick = { cancel = job }) { Text(tr(lang, "작업 중단", "Cancel job")) }
                }
                HorizontalDivider()
            }
            Text(tr(lang, "작업 처리 결과", "Action results"), style = MaterialTheme.typography.titleMedium)
            if (current?.operations.orEmpty().isEmpty()) Text(tr(lang, "기록된 실행 없음", "No recorded operations"))
            current?.operations.orEmpty().forEach { operation ->
                Text((operation.tool ?: "Tool") + " · " + statusLabel(operation.status), style = MaterialTheme.typography.titleSmall)
                SelectionContainer { Text(operation.result?.takeUnless { it.isJsonNull }?.toString()
                    ?: tr(lang, "아직 결과 없음", "No result yet"), style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
            }
        }
    }
    review?.let { item ->
        AlertDialog(onDismissRequest = { review = null },
            title = { Text(tr(lang, "도구 실행 승인", "Approve tool execution")) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(item.tool.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    if (item.status == "interrupted") Text(tr(lang, "중단된 실행의 승인 기록입니다. 즉시 실행이 재개되지 않을 수 있습니다.", "Interrupted approval; execution may not resume immediately."))
                    SelectionContainer { Text(item.args?.toString().orEmpty()) }
                }
            },
            confirmButton = { TextButton(enabled = !busy && !failure, onClick = {
                review = null
                action {
                    val response = vm.decideApproval(roomId, item, true)
                    resultNotice = if (response.resumed) tr(lang, "승인 전달됨", "Approval delivered")
                        else tr(lang, "승인 저장됨 · 재개 여부는 작업 상태 확인", "Approval stored · check job status")
                }
            }) { Text(tr(lang, "실행 허용", "Allow")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { review = null }) { Text(tr(lang, "닫기", "Close")) }
                    TextButton(enabled = !busy && !failure, onClick = {
                        review = null
                        action {
                            vm.decideApproval(roomId, item, false)
                            resultNotice = tr(lang, "거부 저장됨", "Denial recorded")
                        }
                    }) { Text(tr(lang, "거부", "Deny")) }
                }
            })
    }
    cancel?.let { job ->
        AlertDialog(onDismissRequest = { cancel = null },
            title = { Text(tr(lang, "이 작업을 중단할까요?", "Cancel this job?")) },
            text = { Text(job.input?.input.orEmpty()) },
            confirmButton = { TextButton(enabled = !busy && !failure, onClick = {
                cancel = null
                action {
                    val response = vm.cancelJob(roomId, requireNotNull(job.id))
                    resultNotice = tr(lang, "서버 상태: ", "Server status: ") + statusLabel(response.status)
                }
            }) { Text(tr(lang, "중단", "Cancel job")) } },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text(tr(lang, "유지", "Keep")) } })
    }
}
