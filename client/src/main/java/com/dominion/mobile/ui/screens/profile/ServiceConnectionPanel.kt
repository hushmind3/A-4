package com.dominion.mobile.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dominion.mobile.data.model.Connection
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.components.AppTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** Existing engine-owned account and external tool registration. No mobile token store. */
@Composable
fun ServiceConnectionPanel(vm: AppViewModel, service: Connection? = null) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var accounts by remember { mutableStateOf<List<ServiceAccount>?>(null) }
    var connectors by remember { mutableStateOf<List<ServiceConnector>?>(null) }
    var tools by remember { mutableStateOf<List<EngineTool>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var website by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<ServiceAccount?>(null) }
    suspend fun load() {
        try { accounts = vm.serviceAccounts() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { accounts = null }
        try { connectors = vm.serviceConnectors() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { connectors = null }
        try { tools = vm.engineTools() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { tools = null }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { if (!busy) load() }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = ""
        scope.launch {
            try { block(); load() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "본체에서 요청 처리 여부를 확인하지 못했습니다. 상태를 다시 조회하세요." }
            finally { busy = false }
        }
    }
    val native = accounts.orEmpty().filter { service == null || it.id == serviceAccountId(service.provider) }
    val external = connectors.orEmpty().filter { service == null || it.mobileConnectionId == service.id }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("실제 서비스 연결", style = MaterialTheme.typography.titleMedium)
        if (accounts == null) Text("본체 계정 목록 조회 필요", style = MaterialTheme.typography.bodySmall)
        native.forEach { account ->
            val accountId = account.id
            val granted = if (service == null) account.connected else account.authorizes(service.provider)
            Text(account.name ?: accountId.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(if (granted) "인증 확인됨" else if (account.connected) "계정 연결됨 · 서비스 권한 부족" else "인증 필요")
            if (account.connected) Text(account.grantedScopes?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
            if (!account.configured && !account.connected) Text("본체에 제공자 인증 설정이 필요합니다.", style = MaterialTheme.typography.bodySmall)
            if (accountId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && account.configured, onClick = {
                        action { vm.openServiceLogin(accountId); status = "본체 컴퓨터에서 로그인 화면을 열었습니다. 인증 후 새로고침하세요." }
                    }) { Text(if (account.connected) "권한 재인증" else "로그인") }
                    if (account.connected) TextButton(enabled = !busy, onClick = { disconnect = account }) { Text("연결 해제") }
                }
            }
        }
        if (service != null && native.isEmpty() && accounts != null) Text("본체에 이 서비스의 전용 인증 제공자가 없습니다. 아래의 직접 등록 또는 브라우저 로그인 경로를 사용하세요.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("직접 등록한 외부 서비스", style = MaterialTheme.typography.titleSmall)
        if (connectors == null) Text("외부 서비스 목록 조회 필요")
        external.forEach { connector ->
            val matching = tools.orEmpty().filter { it.source == "mcp" && it.server == connector.name }
            Text(connector.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(when {
                connector.enabled == false -> "사용 중지"
                tools == null -> "등록됨 · 도구 검색 필요"
                matching.isEmpty() -> "등록됨 · 실행 가능한 도구 미확인"
                else -> "검색된 도구 ${matching.size}개"
            }, style = MaterialTheme.typography.bodySmall)
            matching.take(8).forEach { Text(it.name + if (it.approval) " · 승인 필요" else " · 본체 정책 적용", style = MaterialTheme.typography.bodySmall) }
            val id = connector.id
            if (id != null && connector.transport == "http") {
                OutlinedButton(enabled = !busy, onClick = {
                    action { vm.setServiceConnectorEnabled(id, connector.enabled == false); status = "본체 설정 저장 확인" }
                }) { Text(if (connector.enabled == false) "사용 켜기" else "사용 끄기") }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { register = true }) { Text("외부 서비스 직접 등록") }
        OutlinedButton(enabled = !busy, onClick = { website = true }) { Text("본체 브라우저에서 직접 로그인") }
        TextButton(enabled = !busy, onClick = { action {} }) { Text("연결 및 도구 상태 새로고침") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
    if (register) {
        var name by remember { mutableStateOf(service?.name.orEmpty()) }
        var endpoint by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        val id = remember { UUID.randomUUID().toString() }
        val valid = runCatching { ConnectorRegistration(id, name.trim(), endpoint.trim(), service?.id).validate() }.isSuccess
        AlertDialog(onDismissRequest = { if (!busy) register = false }, title = { Text("외부 서비스 등록") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("서비스에서 제공하는 HTTPS 도구 서버 주소와 해당 서버 전용 인증 토큰을 입력하세요. 일반 웹사이트 주소만으로는 도구가 등록되지 않습니다.")
                    AppTextField(name, { name = it.take(100) }, label = "서비스 이름")
                    AppTextField(endpoint, { endpoint = it.take(2048) }, label = "도구 서버 HTTPS 주소")
                    AppTextField(token, { token = it.take(8192) }, label = "인증 토큰 (필요한 경우)", isPassword = true)
                    Text("토큰은 본체 금고에 전달하며 앱 DB에 저장하지 않습니다. 실제 승인 여부는 본체 정책을 따릅니다.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = !busy && valid, onClick = {
                val request = ConnectorRegistration(id, name.trim(), endpoint.trim(), service?.id)
                val credential = token.trim()
                token = ""
                action {
                    val saved = vm.registerServiceConnector(request, credential)
                    register = false
                    status = "${saved.name} 등록 확인. 검색 결과에서 실제 도구를 확인하세요."
                }
            }) { Text("등록 및 도구 검색") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { token = ""; register = false }) { Text("취소") } })
    }
    if (website) {
        var url by remember { mutableStateOf(when (service?.provider) {
            "gmail" -> "https://mail.google.com/"
            "google_calendar" -> "https://calendar.google.com/"
            "google_drive" -> "https://drive.google.com/"
            "github" -> "https://github.com/login"
            "notion" -> "https://www.notion.so/login"
            "slack" -> "https://slack.com/signin"
            "x" -> "https://x.com/i/flow/login"
            "apple_music" -> "https://music.apple.com/"
            "microsoft" -> "https://www.microsoft365.com/"
            else -> ""
        }) }
        AlertDialog(onDismissRequest = { if (!busy) website = false }, title = { Text("서비스 웹 로그인") },
            text = {
                Column {
                    Text("본체의 브라우저를 열어 직접 로그인합니다. 휴대폰 브라우저 로그인과 본체 브라우저 세션은 별개입니다.")
                    AppTextField(url, { url = it }, label = "서비스 HTTPS 주소")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { url = "https://www.coupang.com/" }) { Text("쿠팡") }
                        TextButton(onClick = { url = "https://www.naver.com/" }) { Text("네이버") }
                    }
                    Text("로그인은 본체 브라우저에서 직접 완료해야 하며 자동 인증 성공으로 표시하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(enabled = !busy && url.startsWith("https://"), onClick = {
                val target = url.trim()
                action { vm.openServiceWebsite(target); website = false; status = "본체 브라우저 열기 요청 완료. 해당 컴퓨터에서 로그인하세요." }
            }) { Text("브라우저 열기") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { website = false }) { Text("취소") } })
    }
    disconnect?.let { account ->
        AlertDialog(onDismissRequest = { disconnect = null }, title = { Text("계정 연결 해제") },
            text = { Text("본체에 연결된 ${account.name} 인증을 해제합니다. 같은 계정을 사용하는 다른 서비스도 영향을 받을 수 있습니다.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                disconnect = null
                action { vm.disconnectServiceAccount(requireNotNull(account.id)); status = "본체 연결 해제 확인" }
            }) { Text("해제") } }, dismissButton = { TextButton(onClick = { disconnect = null }) { Text("취소") } })
    }
}
