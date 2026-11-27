package com.dominion.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EngineSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DominionApplication).container
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    val scope = rememberCoroutineScope()
    val saved = container.engine
    val api = remember(saved) { saved?.let(ApiFactory::create) }
    var origin by remember { mutableStateOf(saved?.origin.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var useAccess by remember { mutableStateOf(saved?.accessClientId != null) }
    var accessId by remember { mutableStateOf("") }
    var accessSecret by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf<EngineReadiness?>(null) }
    var rooms by remember { mutableStateOf(emptyList<EngineRoom>()) }
    var providers by remember { mutableStateOf(emptyList<EngineProvider>()) }
    var tools by remember { mutableStateOf(emptyList<EngineTool>()) }
    var room by remember { mutableStateOf<EngineRoom?>(null) }
    var provider by remember { mutableStateOf<EngineProvider?>(null) }
    var models by remember { mutableStateOf(emptyList<EngineModel>()) }
    var selected by remember { mutableStateOf(emptyList<RoomModel>()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var confirmImport by remember { mutableStateOf(false) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: retrofit2.HttpException) {
                status = tr(lang, "서버 오류: ", "Server error: ") + e.code()
            }
            catch (_: Exception) { status = tr(lang, "연결 또는 설정 확인 실패", "Connection or configuration failed") }
            finally { busy = false }
        }
    }
    fun candidate(): EngineConnection {
        val parsed = if (token.isBlank() && saved != null)
            EngineConnection.parse(origin, saved.ownerToken).also { require(it.origin == saved.origin) }
        else EngineConnection.parse(origin, token)
        if (!useAccess) return parsed
        val useSavedAccess = accessId.isBlank() && accessSecret.isBlank() && saved?.origin == parsed.origin
        val id = if (useSavedAccess) saved?.accessClientId else accessId
        val secret = if (useSavedAccess) saved?.accessClientSecret else accessSecret
        require(!id.isNullOrBlank() && !secret.isNullOrBlank())
        return EngineConnection.parse(parsed.origin, parsed.ownerToken, id, secret)
    }
    fun loadCatalog() = action {
        val client = requireNotNull(api)
        rooms = client.engineRooms()
        providers = client.engineProviders().filter { it.enabled != false }
        tools = client.engineTools()
        val current = rooms.firstOrNull { it.id == room?.id }
            ?: rooms.firstOrNull { it.id == "assistant" } ?: rooms.firstOrNull()
        room = current
        selected = current?.models.orEmpty()
        provider = null
        models = emptyList()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "본체 연결 및 모델·도구", "Engine, models and tools"), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(lang, "DOMINION 실행 서버", "DOMINION engine"), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(enabled = !busy, onClick = {
                origin = "https://engine.dominionai.kr/"
                token = ""; useAccess = false; accessId = ""; accessSecret = ""; diagnosis = null
            }) { Text("DOMINION Cloud 원격 본체 주소 사용") }
            Text("전용 Tunnel 주소입니다. PC에서 본체와 인증 브리지, cloudflared가 실행 중이어야 합니다.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = origin, onValueChange = { origin = it; diagnosis = null }, enabled = !busy,
                label = { Text("HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = token, onValueChange = { token = it; diagnosis = null }, enabled = !busy,
                label = { Text(tr(lang, "본체 관리자 토큰", "Engine owner token")) },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(tr(lang, "본인 소유의 HTTPS 서버를 연결합니다. 관리자 토큰은 Google 계정 토큰과 별개이며 전체 본체 권한을 갖습니다.",
                "Connect your own HTTPS server. The engine owner token is separate from Google and grants full engine access."))
            Row {
                Checkbox(checked = useAccess, enabled = !busy, onCheckedChange = {
                    useAccess = it; diagnosis = null
                })
                Text("Cloudflare Access (이미 설정한 서비스 인증)")
            }
            if (useAccess) {
                OutlinedTextField(accessId, { accessId = it; diagnosis = null }, enabled = !busy, singleLine = true,
                    label = { Text("Access Client ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accessSecret, { accessSecret = it; diagnosis = null }, enabled = !busy, singleLine = true,
                    label = { Text("Access Client Secret") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation())
                Text("기존 서비스 토큰이 있는 경우에만 입력하세요. 저장된 인증은 같은 주소에서만 재사용됩니다. Tunnel 자체의 설치 토큰이 아닙니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(enabled = !busy, onClick = { action {
                diagnosis = null
                diagnosis = probeEngine(ApiFactory.create(candidate()))
            } }) { Text("연결·모델·음성 진단") }
            diagnosis?.let { result ->
                Text("본체 인증: " + result.authenticated.label)
                Text("대화방: " + result.rooms.label + (result.roomCount?.let { " (" + it + "개)" } ?: ""))
                Text("모델 제공자: " + result.providers.label + (result.providerCount?.let { " (" + it + "개 활성)" } ?: ""))
                Text("도구: " + result.tools.label + (result.toolCount?.let { " (" + it + "개)" } ?: ""))
                Text("Whisper: " + result.whisper.label + " · Piper: " + result.piper.label)
                Text("조회 성공은 실제 모델 추론·실시간 통화 성공을 뜻하지 않습니다.", style = MaterialTheme.typography.labelSmall)
            }
            Button(enabled = !busy, onClick = { action {
                val candidate = candidate()
                val result = probeEngine(ApiFactory.create(candidate))
                diagnosis = result
                check(result.authenticated == ProbeState.OK && result.rooms == ProbeState.OK)
                (context as MainActivity).applyEngine(candidate)
            } }) { Text(tr(lang, "확인 후 연결", "Verify and connect")) }
            if (api != null) {
                OutlinedButton(enabled = !busy, onClick = { action {
                    saved?.let{container.cloudAccount.pauseEngineReconnect(it)}
                    (context as MainActivity).applyEngine(null)
                } }) {
                    Text(tr(lang, "본체 연결 해제", "Disconnect engine"))
                }
                HorizontalDivider()
                Text(tr(lang, "대화방 에이전트 설정", "Room agent configuration"), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(enabled = !busy, onClick = ::loadCatalog) { Text(tr(lang, "기존 모델·도구 조회", "Load existing models and tools")) }
                rooms.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = room?.id == item.id, enabled = !busy, onClick = {
                            room = item
                            selected = item.models
                        })
                        Column {
                            Text(item.name)
                            Text(if (item.toolsEnabled) tr(lang, "도구 활성", "Tools enabled") else tr(lang, "도구 비활성", "Tools disabled"),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(tr(lang, "모델 풀 · 로컬, 외부 API, Cloudflare 등 본체 등록 모델", "Model pool · configured local, API and Cloudflare providers"))
                providers.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = provider?.id == item.id, enabled = !busy, onClick = {
                            provider = item
                            models = emptyList()
                            action { models = api.engineModels(item.id) }
                        })
                        Text(item.name)
                    }
                }
                models.forEach { item ->
                    val candidate = provider?.let { RoomModel(it.id, item.id) }
                    if (candidate != null) Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked = candidate in selected, enabled = !busy, onCheckedChange = { checked ->
                            selected = if (checked) (selected + candidate).distinct() else selected - candidate
                        })
                        Text(item.name ?: item.id)
                    }
                }
                selected.forEach { item ->
                    TextButton(enabled = !busy, onClick = { selected = selected - item }) { Text("− " + item.model) }
                }
                Button(enabled = !busy && room != null && selected.isNotEmpty() && selected.size <= 64, onClick = { action {
                    val id = requireNotNull(room).id
                    api.updateRoom(id, RoomConfigUpdate(true, true, selected))
                    val verified = api.engineRoom(id)
                    check(verified.toolsEnabled && verified.autoModels && verified.models.toSet() == selected.toSet())
                    room = verified
                    rooms = rooms.map { if (it.id == id) verified else it }
                    status = tr(lang, "본체 설정 확인 완료", "Engine configuration verified")
                } }) { Text(tr(lang, "도구 활성화 및 모델 풀 적용", "Enable tools and apply model pool")) }
                OutlinedButton(enabled = !busy && room != null, onClick = { action {
                    val id = requireNotNull(room).id
                    api.updateRoom(id, RoomConfigUpdate(toolsEnabled = true))
                    val verified = api.engineRoom(id)
                    check(verified.toolsEnabled)
                    room = verified
                    rooms = rooms.map { if (it.id == id) verified else it }
                    status = tr(lang, "도구 활성화 확인", "Tools enabled")
                } }) { Text(tr(lang, "기존 모델 유지하고 도구 활성화", "Enable tools, keep models")) }
                HorizontalDivider()
                Text(tr(lang, "본체에서 실행 가능한 도구", "Engine tools"), style = MaterialTheme.typography.titleMedium)
                tools.forEach { item ->
                    Text(item.name + if (item.approval) " · " + tr(lang, "승인 필요", "Approval required") else "")
                }
                Text(tr(lang, "한국 생활·행정 기능은 기존 스킬 가져오기를 사용합니다. 설치 후 필요한 서비스 설정과 도구 권한을 본체에서 확인해야 합니다.",
                    "Korean service skills use the existing engine importer. Service setup and permissions must be verified on the engine."))
                OutlinedButton(enabled = !busy, onClick = { confirmImport = true }) { Text(tr(lang, "한국형 k-skill 가져오기", "Import Korean k-skill")) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status)
        }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false },
        title = { Text(tr(lang, "한국형 스킬 가져오기", "Import Korean skills")) },
        text = { Text(tr(lang, "본체에 NomaDamas/k-skill 저장소를 가져옵니다. 외부 도구 실행 및 인증은 기존 본체 승인 정책을 따릅니다.",
            "Import NomaDamas/k-skill into the engine. Execution and credentials remain subject to engine policy.")) },
        confirmButton = { TextButton(onClick = {
            confirmImport = false
            action {
                val result = requireNotNull(api).importSkillRepository(mapOf("repo" to "https://github.com/NomaDamas/k-skill"))
                status = tr(lang, "가져온 스킬: ", "Imported skills: ") + result.added +
                    "\n" + result.skills.joinToString(", ")
            }
        }) { Text(tr(lang, "가져오기", "Import")) } },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(tr(lang, "취소", "Cancel")) } })
}
