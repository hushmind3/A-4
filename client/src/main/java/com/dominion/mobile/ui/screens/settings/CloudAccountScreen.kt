package com.dominion.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.CloudAccountSnapshot
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CloudAccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context.applicationContext) {
        (context.applicationContext as DominionApplication).container.cloudAccount
    }
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var snapshot by remember { mutableStateOf<CloudAccountSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (busy) return
        busy = true
        try {
            snapshot = repo.refresh()
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { busy = false }
    }

    fun openApproval(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { error = true }
    }

    LaunchedEffect(lifecycle, repo) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(if (snapshot?.status == "pending") 3000L else 30_000L)
            }
        }
    }

    fun runAction(action: suspend () -> CloudAccountSnapshot, open: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = action()
                snapshot = result
                error = false
                if (open) result.approvalUrl?.let(::openApproval)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = "DOMINION Cloud", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr(lang, "공통 계정", "Shared account"), style = MaterialTheme.typography.headlineSmall)
            Text(tr(lang, "기존 DOMINION Cloud Google 계정을 연결합니다. 로컬 서버 사용에는 로그인이 필요하지 않습니다.",
                "Connect your existing DOMINION Cloud Google account. Local server use does not require cloud sign-in."))
            Text(tr(lang, "실행 서버: ", "Execution server: ") +
                ((context.applicationContext as DominionApplication).container.engine?.origin ?: tr(lang, "미설정", "Not configured")))
            Text(tr(lang, "클라우드 계정 연결은 실행 서버 접근 권한과 별개입니다.", "Cloud account authorization does not grant execution server access."))
            if (error) Text(tr(lang, "클라우드 연결 또는 확인에 실패했습니다. 다시 확인하세요.",
                "Cloud request or verification failed. Retry."), color = MaterialTheme.colorScheme.error)
            val current = snapshot
            if (current == null) {
                Text(tr(lang, "계정 상태 확인 중", "Checking account"))
            } else when (current.status) {
                "pending" -> {
                    Text(tr(lang, "Google 승인 대기 중", "Waiting for Google approval"))
                    Text(tr(lang, "브라우저에서 승인하거나 PC의 외부 앱 연결 화면에 코드를 입력하세요.",
                        "Approve in the browser or use the external app pairing screen on your PC."))
                    current.pairingId?.let { code ->
                        Text(code, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Text(tr(lang, "연결 코드 복사", "Copy pairing code"))
                        }
                    }
                    Button(onClick = { current.approvalUrl?.let(::openApproval) }, enabled = !busy) {
                        Text(tr(lang, "Google 승인 페이지 열기", "Open Google approval"))
                    }
                    OutlinedButton(onClick = { runAction(repo::disconnect) }, enabled = !busy) {
                        Text(tr(lang, "연결 취소", "Cancel pairing"))
                    }
                }
                "approved" -> {
                    Text(if (error) tr(lang, "마지막 확인: 연결됨", "Last verified: connected")
                        else tr(lang, "클라우드 계정 연결됨", "Cloud account connected"))
                    current.accountId?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    current.billing?.let {
                        Text(tr(lang, "플랜: ", "Plan: ") + it.plan)
                        it.balanceKrw()?.let { balance -> Text(tr(lang, "실제 잔액: ", "Verified balance: ") + balance) }
                        it.free_monthly_remaining?.let { remaining ->
                            Text(tr(lang, "월 잔여 요청: ", "Monthly remaining: ") + remaining + "/" + it.free_monthly_limit)
                        }
                        Text(tr(lang, "오늘 무료 모델 잔여 요청: ", "Free requests remaining today: ") +
                            "${it.free_daily_remaining} / ${it.free_daily_limit}")
                    }
                    Text(tr(lang, "클라우드 제공 모델 (목록 조회)", "Available cloud models (read only)"),
                        style = MaterialTheme.typography.titleMedium)
                    current.models.forEach { Text(it.name.ifBlank { it.id }) }
                    Text(tr(lang, "이 목록은 본체 실행 모델을 변경하지 않습니다.", "This list does not change the engine model."))
                    OutlinedButton(onClick = { runAction(repo::disconnect) }, enabled = !busy) {
                        Text(tr(lang, "클라우드 연결 해제", "Disconnect cloud account"))
                    }
                }
                else -> {
                    if (current.status == "expired" || current.status == "revoked") {
                        Text(tr(lang, "이전 연결이 만료되었거나 해제되었습니다.", "Previous connection expired or was revoked."))
                    }
                    Button(onClick = { runAction(repo::begin, open = true) }, enabled = !busy) {
                        Text(tr(lang, "Google 계정 연결", "Connect Google account"))
                    }
                }
            }
            OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !busy) {
                Text(tr(lang, "상태 새로고침", "Refresh status"))
            }
        }
    }
}
