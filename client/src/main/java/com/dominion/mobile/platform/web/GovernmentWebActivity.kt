package com.dominion.mobile.platform.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.GovernmentSubmission
import com.dominion.mobile.data.GovernmentSubmissionCheck
import com.dominion.mobile.data.GovernmentWebPlan
import com.dominion.mobile.data.KoreanServices
import com.dominion.mobile.data.model.GovernmentReceipt
import com.dominion.mobile.data.remote.CloudChatMessage
import com.dominion.mobile.ui.components.AppTextField
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.DominionTheme
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Visible official-site workspace. The user logs in here; no cookie or
 * credential is sent to the model. The model only returns a bounded ref plan. */
class GovernmentWebActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID).orEmpty()
        val service = KoreanServices.entries.firstOrNull { it.id == serviceId }
        if (service == null) {
            finish()
            return
        }
        setContent {
            DominionTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme(), lang = com.dominion.mobile.data.model.Lang.KO) {
                GovernmentWebScreen(service.id, service.title, service.url, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
        fun intent(context: android.content.Context, serviceId: String) =
            Intent(context, GovernmentWebActivity::class.java).putExtra(EXTRA_SERVICE_ID, serviceId)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GovernmentWebScreen(serviceId: String, serviceTitle: String, serviceUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DominionApplication).container
    val scope = rememberCoroutineScope()
    val palette = LocalDominionPalette.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(serviceUrl) }
    var status by remember { mutableStateOf("공식 사이트를 불러오는 중입니다.") }
    var error by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<GovernmentWebPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<GovernmentSubmissionCheck?>(null) }
    var pendingFile by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingFile
        pendingFile = null
        callback?.onReceiveValue(if (uri == null) null else arrayOf(uri))
    }
    val controller = remember(webView) { webView?.let(::GovernmentWebController) }

    suspend fun takeSnapshot() = requireNotNull(controller).snapshot()
    suspend fun makePlan() {
        val snapshot = takeSnapshot()
        val account = container.cloudAccount
        check(account.savedAccountId() != null) { "DOMINION Cloud 계정을 먼저 연결해 주세요." }
        val model = container.repository.state.value.settings.mobileModel ?: "dominion/free-auto"
        status = "화면 구조를 분석하고 실행 계획을 만드는 중입니다."
        val result = account.completeForMobile(model, listOf(
            CloudChatMessage("system", "You produce a strict bounded JSON form plan. Never authenticate, never invent values, never output JavaScript or credentials."),
            CloudChatMessage("user", GovernmentWebPlan.prompt(request.ifBlank { "$serviceTitle 업무를 공식 사이트에서 처리해 주세요." }, snapshot))
        ), 1200)
        val parsed = GovernmentWebPlan.parse(serviceId, result.content, snapshot.elements)
        check(parsed.actions.isNotEmpty()) { "실행 가능한 계획이 없습니다." }
        plan = parsed
        status = "계획 ${parsed.actions.size}단계를 검토한 뒤 실행하세요."
    }
    suspend fun executePrepare() {
        val current = requireNotNull(plan) { "먼저 AI 계획을 만드세요." }
        val c = requireNotNull(controller)
        val prepare = current.actions.filter { it.action != "submit" && it.action != "read" }
        for (action in prepare) {
            check(c.execute(action)) { "화면 요소 ${action.ref} 조작에 실패했습니다." }
            delay(250)
            // A file input opens Android's document picker asynchronously.
            while (pendingFile != null) delay(100)
        }
        val submit = current.actions.any { it.action == "submit" }
        status = if (submit) "입력·첨부 준비 완료. 최종 제출은 별도 승인 후 실행합니다."
        else "입력 준비 완료. 화면을 직접 확인하세요."
    }
    suspend fun executeSubmit() {
        val current = requireNotNull(plan)
        val submit = current.actions.firstOrNull { it.action == "submit" }
            ?: current.actions.firstOrNull { it.action == "click" && it.ref != null }
            ?: error("제출 버튼이 계획에 없습니다.")
        while (pendingFile != null) delay(100)
        check(requireNotNull(controller).execute(submit)) { "공식 제출 버튼을 실행하지 못했습니다." }
        status = "제출 응답을 기다리는 중입니다."
        delay(2_000)
        val text = requireNotNull(controller).pageText()
        val checked = GovernmentSubmission.inspect(text)
        receipt = checked
        if (checked.submitted) {
            val row = GovernmentReceipt(
                id = "gov:" + UUID.randomUUID(),
                serviceId = serviceId,
                serviceTitle = serviceTitle,
                receiptNumber = checked.receiptNumber,
                pageExcerpt = checked.excerpt
            )
            runCatching { container.repository.recordGovernmentReceipt(row) }
            status = if (checked.receiptNumber != null)
                "접수번호 ${checked.receiptNumber} 확인 · 기기에 기록했습니다."
            else "접수 완료 문구를 확인했습니다. 접수번호는 화면에서 직접 확인하세요."
        } else {
            status = "제출 후 접수 확인 문구를 찾지 못했습니다. 화면에서 직접 확인하세요."
        }
    }
    fun runAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message?.take(240) ?: "실행 결과를 확인하지 못했습니다." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(title = serviceTitle, subtitle = "공식 사이트 · 사용자 인증은 이 화면에서 직접", onBack = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = loaded && !busy, onClick = { runAction { takeSnapshot().also { status = "화면 ${it.elements.size}개 요소를 확인했습니다." } } }) { Text("화면 분석") }
            TextButton(enabled = loaded && !busy, onClick = { runAction { makePlan() } }) { Text("AI 계획") }
            TextButton(enabled = plan != null && !busy, onClick = { runAction { executePrepare() } }) { Text("입력 실행") }
            TextButton(enabled = plan?.hasSubmit == true && !busy, onClick = { confirmSubmit = true }) { Text("최종 제출") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp))
        Text(currentUrl.take(180), style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        AppTextField(request, { request = it.take(1800) }, label = "공식 업무 요청",
            placeholder = "예: 주민등록등본 1통을 발급 신청", singleLine = false, maxLines = 3,
            modifier = Modifier.padding(horizontal = 16.dp))
        plan?.let { current ->
            Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("검토할 계획", style = MaterialTheme.typography.labelMedium)
                current.actions.forEach { action ->
                    Text("${action.action} ${action.ref ?: ""} ${action.value?.take(80) ?: ""}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("입력값·첨부는 제출 전에 화면에서 직접 확인하세요. 로그인·인증은 자동화하지 않습니다.",
                    style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            }
        }
        receipt?.let {
            Text(if (it.receiptNumber != null) "접수번호: ${it.receiptNumber}" else "접수 결과를 화면에서 확인하세요.",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        userAgentString = userAgentString + " DOMINION-Government"
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url
                            if (target.scheme != "https") {
                                error = "보안을 위해 HTTPS 공식 페이지만 엽니다."
                                return true
                            }
                            return false
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            loaded = true
                            currentUrl = url ?: view.url.orEmpty()
                            status = "공식 페이지가 열렸습니다. 로그인·인증이 필요하면 직접 진행하세요."
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                            fileChooserParams: FileChooserParams): Boolean {
                            pendingFile?.onReceiveValue(null)
                            pendingFile = filePathCallback
                            runCatching { filePicker.launch(arrayOf("*/*")) }
                                .onFailure { pendingFile = null; filePathCallback.onReceiveValue(null) }
                            return true
                        }
                    }
                    loadUrl(serviceUrl)
                    webView = this
                }
            },
            update = { if (webView == null) webView = it }
        )
    }

    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("공식 제출을 실행할까요?") },
            text = { Text("입력값과 첨부 파일이 실제 기관에 제출됩니다.\n현재 주소: "+currentUrl.take(180)+
                "\n로그인·인증은 이미 직접 완료했는지 확인하세요. 이 작업은 취소할 수 없을 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSubmit = false
                    runAction { executeSubmit() }
                }) { Text("공식 제출") }
            },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("취소") } }
        )
    }
}
