package com.dominion.mobile.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.launch
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
    onOpenChat: (String) -> Unit, onTasks: () -> Unit, onAgenda: () -> Unit, onDeviceActions: () -> Unit) {
    val p = LocalDominionPalette.current
    val context = LocalContext.current
    var selected by remember { mutableStateOf<KoreanService?>(null) }
    var privateErrand by remember { mutableStateOf<com.dominion.mobile.data.KoreanErrand?>(null) }
 
    var institutionDraft by remember { mutableStateOf<Pair<String, String>?>(null) }
    var scheduleDraft by remember { mutableStateOf<ServiceScheduleDraft?>(null) }
    var query by remember { mutableStateOf("") }
    val errands=com.dominion.mobile.data.KoreanErrands.entries.filter{
        query.isBlank() || (it.title+" "+it.examples).contains(query,true)
    }
    val results = KoreanServices.entries.filter {
        query.isBlank() || (it.title + " " + it.summary).contains(query, true)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "한국 생활·행정 비서", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("공식 기관 조회 → AI 업무 접수 → 결과·승인 확인", color = p.textSecondary)
            Text("본인 인증이나 제출이 필요한 업무는 직접 확인합니다.",
                style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            AppTextField(query, { query = it }, placeholder = "복지, 민원, 학교 등 검색")
            OutlinedButton(onClick = onDeviceActions, modifier = Modifier.fillMaxWidth()) { Text("휴대폰 연결 · 연락처 / 문자 / 이메일 / 앱") }
            Row {
                TextButton(onClick = onTasks) { Text("내가 맡긴 업무") }
                TextButton(onClick = onAgenda) { Text("내 일정") }
                TextButton(onClick = { if (!DeviceActions.dial(context, "110")) context.toast("전화 앱을 열 수 없습니다.") }) { Text("110") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(vm.localExecution)items(errands,key={"private:"+it.id}){errand->
                Card(onClick={privateErrand=errand}){
                    Column(Modifier.fillMaxWidth().padding(14.dp)){
                        Text(errand.title,style=MaterialTheme.typography.titleMedium)
                        Text(errand.examples,style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={privateErrand=errand}){Text("AI 앱 업무 실행")}
                    }
                }
            }
            items(results, key = { it.id }) { service ->
                Card(onClick = { selected = service }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(service.title, style = MaterialTheme.typography.titleMedium)
                        Text(service.summary, style = MaterialTheme.typography.bodySmall)
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

    privateErrand?.let{errand->
        key(errand.id){
            val installed=remember(errand.id){DeviceActions.launchableApps(context)}
            val preferred=installed.filter{errand.matches(it.packageName,it.label)}
            var selectedApp by remember{mutableStateOf(preferred.singleOrNull())}
            var allApps by remember{mutableStateOf(preferred.isEmpty())}
            var appQuery by remember{mutableStateOf("")}
            var details by remember{mutableStateOf("")}
            var agent by remember{mutableStateOf(state.myCharacter()?.id?:state.characters.firstOrNull()?.id.orEmpty())}
            AppBottomSheet(onDismiss={privateErrand=null}){
                SheetTitle(errand.title,"설치된 앱에서 실제 탐색")
                Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("대상 앱을 선택하면 AI가 실제 화면을 관찰하고 다음 단계를 계획합니다. 최종 거래·인증은 직접 확인합니다.")
                    TextButton(onClick={allApps=!allApps}){Text(if(allApps)"추천 앱만 보기" else "다른 설치 앱 선택")}
                    if(allApps)AppTextField(appQuery,{appQuery=it.take(100)},placeholder="설치 앱 이름")
                    (if(allApps)installed.filter{it.label.contains(appQuery,true)||it.packageName.contains(appQuery,true)}
                        else preferred).take(80).forEach{app->
                        Row{
                            RadioButton(selected=selectedApp==app,onClick={selectedApp=app})
                            Column(Modifier.padding(top=8.dp)){
                                Text(app.label);Text(app.packageName,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if(preferred.isEmpty())Text("추천 앱을 자동 확인하지 못했습니다. 설치된 공식 앱을 직접 선택하세요.")
                    selectedApp?.let{Text("실행 대상: "+it.label+" ("+it.packageName+")")}
                    AppTextField(details,{details=it.take(2000)},label="맡길 업무",placeholder=errand.examples,singleLine=false,maxLines=5)
                    state.characters.forEach{character->
                        Row{RadioButton(selected=agent==character.id,onClick={agent=character.id});Text(character.name,Modifier.padding(top=12.dp))}
                    }
                    Button(enabled=selectedApp!=null&&details.isNotBlank()&&state.character(agent)!=null,onClick={
                        val app=requireNotNull(selectedApp)
                        val request=com.dominion.mobile.data.KoreanErrands.request(errand.id,app.packageName,app.label,details)
                        vm.createPrivateErrand(errand.title,agent,request,app.packageName,onOpenChat)
                        privateErrand=null
                    }){Text("업무 등록 및 실행 검토")}
                }
            }
        }
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
            val nativeSource=when {
                service.id in setOf("kakao-place","naver-news","coupang","danawa")->service.id
                service.id=="food"&&workflow.id in setOf("danawa","coupang")->workflow.id
                service.id=="health"&&workflow.id=="kakao-place"->workflow.id
                else->null
            }
            val nativeLookup=vm.localExecution&&nativeSource!=null
            fun lookup()=if(nativeLookup)com.dominion.mobile.platform.network.KoreanLookup(
                requireNotNull(nativeSource),fields["검색어"].orEmpty()).checked() else null
            fun request(): String = service.request(region, KoreanWorkflows.compose(workflow, fields, details))
            var agent by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }
            AppBottomSheet(onDismiss = { selected = null }) {
                SheetTitle(service.title, "비서에게 맡길 내용")
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if(!nativeLookup)AppTextField(region, { region = it.take(100) }, label = "지역 (선택)", placeholder = "시·군·구")
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
                    if(!nativeLookup)AppTextField(details, { details = it.take(1200) }, label = "추가 요청",
                        placeholder = service.summary, singleLine = false, maxLines = 4)
                    KoreanWorkflows.portals(service.id).forEach { portal ->
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portal.url))) }
                                .onFailure { context.toast("공식 사이트를 열 수 없습니다.") }
                        }, modifier = Modifier.fillMaxWidth()) { Text(portal.label + " 열기") }
                    }
                    Text("주민등록번호·인증번호·비밀번호는 입력하지 마세요.", style = MaterialTheme.typography.labelSmall)
                    if(nativeLookup)Text(if(nativeSource=="danawa")"공개 검색어를 다나와에 직접 전송합니다." else
                        "공개 검색어만 k-skill 외부 프록시에 전송합니다. 개인 주소·연락처·계정정보를 넣지 마세요.",
                        style=MaterialTheme.typography.labelSmall)
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
                    Button(enabled = ready && state.character(agent) != null &&
                        (!nativeLookup || runCatching{lookup()}.isSuccess),
                        onClick = {
                            val call=lookup()
                            val exactRequest=if(call!=null)"공개 검색: "+call.query+" ("+call.source+")" else request()
                            vm.createServiceTask(service.title + " · " + workflow.title, agent, exactRequest, service.steps, onOpenChat,call)
                            selected = null
                        }) { Text(if(nativeLookup)"실제 공개 검색 실행" else "실제 업무 접수") }
                    if(!nativeLookup)OutlinedButton(enabled = ready, onClick = {
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
