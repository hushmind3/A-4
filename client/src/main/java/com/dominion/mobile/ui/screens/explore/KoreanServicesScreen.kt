package com.dominion.mobile.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.launch
import com.dominion.mobile.data.remote.SkillReview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominion.mobile.data.KoreanService
import com.dominion.mobile.data.KoreanServices
import com.dominion.mobile.data.KoreanWorkflows
import android.content.Intent
import android.net.Uri
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.DeviceActions
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.components.*
import com.dominion.mobile.ui.theme.LocalDominionPalette

@Composable
fun KoreanServicesScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit,
    onOpenChat: (String) -> Unit, onTasks: () -> Unit, onAgenda: () -> Unit, onDeviceActions: () -> Unit, onEngineSettings: () -> Unit) {
    val p = LocalDominionPalette.current
    val context = LocalContext.current
    val connection by vm.connection.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<KoreanService?>(null) }
    var institutionDraft by remember { mutableStateOf<Pair<String, String>?>(null) }
    var scheduleDraft by remember { mutableStateOf<ServiceScheduleDraft?>(null) }
    var query by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf<List<com.dominion.mobile.data.remote.EngineSkillState>?>(null) }
    var skillError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var skillReview by remember { mutableStateOf<SkillReview?>(null) }
    var skillBusy by remember { mutableStateOf(false) }

    fun reviewSkill(id: String) {
        if (skillBusy) return
        skillBusy = true
        scope.launch {
            try { skillReview = vm.inspectEngineSkill(id) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { context.toast("스킬 원문 조회 실패") }
            finally { skillBusy = false }
        }
    }
    LaunchedEffect(connection, refreshKey) {
        skills = null
        skillError = false
        try { skills = vm.loadEngineSkills() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { skillError = true }
    }
    val results = KoreanServices.entries.filter {
        query.isBlank() || (it.title + " " + it.summary).contains(query, true)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "한국 생활·행정 비서", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("공식 기관 조회 → AI 업무 접수 → 결과·승인 확인", color = p.textSecondary)
            Text("본체 상태: " + connection.name + " · 인증이 필요한 신청은 직접 확인",
                style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            AppTextField(query, { query = it }, placeholder = "복지, 민원, 학교 등 검색")
            OutlinedButton(onClick = onDeviceActions, modifier = Modifier.fillMaxWidth()) { Text("휴대폰 연결 · 연락처 / 문자 / 이메일 / 앱") }
            Row {
                TextButton(onClick = onTasks) { Text("내가 맡긴 업무") }
                TextButton(onClick = onAgenda) { Text("내 일정") }
                TextButton(onClick = { refreshKey++ }) { Text("스킬 확인") }
                TextButton(onClick = { if (!DeviceActions.dial(context, "110")) context.toast("전화 앱을 열 수 없습니다.") }) { Text("110") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { service ->
                Card(onClick = { selected = service }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(service.title, style = MaterialTheme.typography.titleMedium)
                        Text(service.summary, style = MaterialTheme.typography.bodySmall)
                        if (service.skillNames.isNotEmpty()) {
                            val active = skills?.filter { it.name in service.skillNames && it.isActive() }?.mapNotNull { it.name }?.distinct().orEmpty()
                            val registered = skills?.filter { it.name in service.skillNames }?.mapNotNull { it.name }?.distinct().orEmpty()
                            Text(when {
                                skillError -> "본체 스킬 상태 조회 실패"
                                skills == null -> "스킬 상태 조회 중"
                                active.isNotEmpty() -> "활성 스킬 " + active.size + "/" + service.skillNames.size + " · 실행 환경 별도 확인"
                                registered.isNotEmpty() -> "등록됨 · 활성화 필요"
                                else -> "관련 스킬 미등록 · 본체의 기존 도구 사용 가능"
                            }, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                if (!DeviceActions.openOfficial(context, service)) context.toast("공식 사이트를 열 수 없습니다.")
                            }) { Text(KoreanServices.sourceLabel(service)) }
                            TextButton(onClick = { selected = service }) { Text("AI에게 맡기기") }
                        }
                    }
                }
            }
        }
    }

    skillReview?.let { review ->
        AlertDialog(onDismissRequest = { if (!skillBusy) skillReview = null },
            title = { Text(review.name + " · 실행 지시문 확인") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("활성화는 본체 전체 스킬 설정에 반영됩니다. 외부 코드와 API 사용 여부를 확인하세요. 활성화만으로 실행 환경이 준비되지는 않습니다.")
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(review.instructions) }
                }
            },
            confirmButton = {
                TextButton(enabled = !skillBusy, onClick = {
                    skillBusy = true
                    scope.launch {
                        try {
                            vm.changeEngineSkill(review, !review.enabled)
                            skillReview = null
                            refreshKey++
                            context.toast("본체 스킬 설정 저장 및 재조회 확인")
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { context.toast("설정 변경을 확인하지 못했습니다. 다시 조회하세요.") }
                        finally { skillBusy = false }
                    }
                }) { Text(if (review.enabled) "비활성화" else "내용 확인 후 활성화") }
            },
            dismissButton = { TextButton(enabled = !skillBusy, onClick = { skillReview = null }) { Text("닫기") } })
    }

    scheduleDraft?.let { draft ->
        ServiceScheduleDialog(vm, draft, onDismiss = { scheduleDraft = null }, onSaved = {
            scheduleDraft = null
            context.toast("반복 AI 업무 예약을 저장했습니다.")
        })
    }
    institutionDraft?.let { (serviceId, request) ->
        com.dominion.mobile.ui.components.MobileOperationsDialog(vm,
            action = com.dominion.mobile.data.remote.MobileAction.INSTITUTION,
            title = "기관 업무 인증·신청·결제 검토", target = serviceId, text = request,
            onDismiss = { institutionDraft = null })
    }
    selected?.let { service ->
        key(service.id) {
            var region by remember { mutableStateOf("") }
            var details by remember { mutableStateOf("") }
            val workflows = remember(service.id) { KoreanWorkflows.forService(service.id) }
            var workflow by remember { mutableStateOf(workflows.first()) }
            var fields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            val ready = workflow.fields.all { !it.required || !fields[it.label].isNullOrBlank() } &&
                (workflow.fields.isNotEmpty() || details.isNotBlank())
            fun request(): String = service.request(region, KoreanWorkflows.compose(workflow, fields, details))
            var agent by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }
            AppBottomSheet(onDismiss = { selected = null }) {
                SheetTitle(service.title, "사용자가 선택한 내용만 본체에 전달")
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(region, { region = it.take(100) }, label = "지역 (선택)", placeholder = "시·군·구")
                    Text("업무 유형", style = MaterialTheme.typography.titleSmall)
                    workflows.forEach { option ->
                        Row {
                            RadioButton(selected = workflow.id == option.id, onClick = {
                                workflow = option
                                fields = emptyMap()
                            })
                            Text(option.title, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    workflow.fields.forEach { field ->
                        AppTextField(fields[field.label].orEmpty(), { value ->
                            fields = fields + (field.label to value.take(300))
                        }, label = field.label + if (field.required) " (필수)" else " (선택)",
                            placeholder = field.example)
                    }
                    AppTextField(details, { details = it.take(1200) }, label = "추가 요청",
                        placeholder = service.summary, singleLine = false, maxLines = 4)
                    KoreanWorkflows.portals(service.id).forEach { portal ->
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portal.url))) }
                                .onFailure { context.toast("공식 사이트를 열 수 없습니다.") }
                        }, modifier = Modifier.fillMaxWidth()) { Text(portal.label + " 열기") }
                    }
                    Text("주민등록번호·인증번호·비밀번호는 입력하지 마세요.", style = MaterialTheme.typography.labelSmall)
                    if (service.skillNames.isNotEmpty()) {
                        Text("관련 도구: " + service.skillNames.joinToString(", "), style = MaterialTheme.typography.labelSmall)
                        if (skills?.any { it.name in service.skillNames } == false) {
                            TextButton(onClick = { selected = null; onEngineSettings() }) { Text("본체 스킬 가져오기·설정") }
                        }
                        skills.orEmpty().filter { it.name in service.skillNames && !it.id.isNullOrBlank() }.forEach { item ->
                            TextButton(enabled = !skillBusy, onClick = { reviewSkill(requireNotNull(item.id)) }) {
                                Text(item.name.orEmpty() + if (item.isActive()) " · 활성" else " · 원문 확인 및 설정")
                            }
                        }
                    }
                    state.characters.forEach { character ->
                        Row {
                            RadioButton(selected = agent == character.id, onClick = { agent = character.id })
                            Text(character.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    Row {
                        service.hotline?.let { phone ->
                            TextButton(onClick = { if (!DeviceActions.dial(context, phone)) context.toast("전화 앱을 열 수 없습니다.") }) { Text("문의 " + phone) }
                        }
                        service.mapQuery?.let { query ->
                            TextButton(onClick = {
                                if (!DeviceActions.map(context, (region.trim() + " " + query).trim()))
                                    context.toast("지도 앱을 열 수 없습니다.")
                            }) { Text("가까운 기관") }
                        }
                    }
                    Button(enabled = ready && state.character(agent) != null,
                        onClick = {
                            vm.createServiceTask(service.title + " · " + workflow.title, agent, request(), service.steps, onOpenChat)
                            selected = null
                        }) { Text("실제 업무 접수") }
                    OutlinedButton(enabled = ready, onClick = {
                        institutionDraft = service.id to request()
                        selected = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("기관 실제 접수·인증·결제 연동") }
                    if (workflow.repeatable) OutlinedButton(enabled = ready && state.character(agent) != null && request().length <= 3000,
                        onClick = {
                            scheduleDraft = ServiceScheduleDraft(service.title + " · " + workflow.title, agent, request())
                            selected = null
                        }) { Text("정기 조사 예약") }
                    Text("접수 실패 시 할 일 메뉴에서 동일 업무를 재확인할 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
            }
        }
    }
}
