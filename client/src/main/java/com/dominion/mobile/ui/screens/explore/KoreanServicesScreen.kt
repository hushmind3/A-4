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
    var phoneBridge by remember { mutableStateOf(false) }
 
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
            OutlinedButton(onClick={phoneBridge=true},modifier=Modifier.fillMaxWidth()){Text("AI 외부 전화 문의 대행")}
            Row {
                TextButton(onClick = onTasks) { Text("내가 맡긴 업무") }
                TextButton(onClick = onAgenda) { Text("내 일정") }
                TextButton(onClick = { if (!DeviceActions.dial(context, "110")) context.toast("전화 앱을 열 수 없습니다.") }) { Text("110") }
            }
            state.governmentReceipts.orEmpty().take(3).forEach { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("최근 공식 접수 · "+record.serviceTitle, style=MaterialTheme.typography.labelMedium)
                        Text(record.receiptNumber?.let{"접수번호 "+it} ?: "접수 결과 화면 확인",
                            style=MaterialTheme.typography.bodySmall)
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.createdAt)),
                            style=MaterialTheme.typography.labelSmall, color=p.textTertiary)
                    }
                }
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

    if(phoneBridge)PhoneBridgeDialog{phoneBridge=false}
    privateErrand?.let{errand->
        key(errand.id){
            val installed=remember(errand.id){DeviceActions.launchableApps(context)}
            val preferred=installed.filter{errand.matches(it.packageName,it.label)}
            var selectedApp by remember{mutableStateOf(preferred.singleOrNull())}
            var allApps by remember{mutableStateOf(preferred.isEmpty())}
            var appQuery by remember{mutableStateOf("")}
            var details by remember{mutableStateOf("")}
            var sendMessage by remember{mutableStateOf(false)}
            var publicRead by remember{mutableStateOf(false)}
            var transact by remember{mutableStateOf(false)}
            var privateField by remember{mutableStateOf<String?>(null)}
            val canPrivate=selectedApp?.let{com.dominion.mobile.data.PrivateAccountRead.supports(errand.id,it.packageName)}==true
            var billPay by remember{mutableStateOf(false)}
            var target by remember{mutableStateOf("")}
            var condition by remember{mutableStateOf("")}
            var maxWon by remember{mutableStateOf("")}
            val canTransact=selectedApp?.let{com.dominion.mobile.data.PhoneTransactions.supports(errand.id,it.packageName)}==true
            val canPublicRead=errand.id in setOf("search","shopping","delivery","transport","used")&&
                selectedApp?.packageName in com.dominion.mobile.data.PhonePublicResult.packages
            var recipient by remember{mutableStateOf("")}
            var listing by remember{mutableStateOf("")}
            var messageBody by remember{mutableStateOf("")}
            var waitReply by remember{mutableStateOf(false)}
            var negotiationGoal by remember{mutableStateOf("")}
            val isDaangn=errand.id=="used"&&selectedApp?.packageName=="com.towneers.www"
            val isKakao=errand.id=="messenger"&&selectedApp?.packageName=="com.kakao.talk"||isDaangn
            var agent by remember{mutableStateOf(state.myCharacter()?.id?:state.characters.firstOrNull()?.id.orEmpty())}
            AppBottomSheet(onDismiss={privateErrand=null}){
                SheetTitle(errand.title,"설치된 앱에서 실제 탐색")
                Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("대상 앱을 선택하면 AI가 실제 화면을 관찰하고 업무를 실행합니다. 거래 확정은 최종 화면에서 별도로 승인합니다.")
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
                    if(canPrivate&&!(canTransact&&transact)){
                        Text("기기에서만 개인 조회",style=MaterialTheme.typography.titleSmall)
                        (listOf<String?>(null)+com.dominion.mobile.data.PrivateAccountRead.fields(errand.id)).forEach{field->
                            Row{RadioButton(selected=privateField==field,onClick={privateField=field})
                                Text(field?:"일반 메뉴 탐색",Modifier.padding(top=12.dp))}
                        }
                        Text("로그인은 직접 진행합니다. 선택한 금액·데이터 수치만 기기에서 읽고 저장하며 AI 서버로 보내지 않습니다.")
                    }
                    if(canTransact){
                        Row{Checkbox(transact,{transact=it;if(it){publicRead=false;privateField=null}})
                            Text(when(errand.id){"finance"->"기기 전용 이체 실행";"telecom"->"기기 전용 요금제·부가서비스 변경";else->"실제 주문·예약·호출 실행"},Modifier.padding(top=12.dp))}
                    }
                    if(canTransact&&transact){
                        if(errand.id=="finance"){
                            Row{RadioButton(selected=!billPay,onClick={billPay=false});Text("이체",Modifier.padding(top=12.dp))
                                RadioButton(selected=billPay,onClick={billPay=true});Text("공과금 납부",Modifier.padding(top=12.dp))}
                        }
                        AppTextField(target,{target=it.take(100)},label=when(errand.id){"finance"->if(billPay)"정확한 납부처 이름" else "정확한 수취인 이름";"telecom"->"정확한 요금제·부가서비스 명칭";else->"정확한 상품·가게·목적지·숙소·병원"})
                        AppTextField(condition,{condition=if(errand.id=="finance")it.filter(Char::isDigit).take(4)else it.take(160)},
                            label=if(errand.id=="finance"){if(billPay)"고지서·납부자 번호 끝 4자리" else "수취 계좌 끝 4자리"} else "화면에서 확인할 조건",
                            placeholder=if(errand.id=="finance")"전체 식별 번호는 공식 은행 앱에 직접 입력" else "옵션·수량·날짜·시간·출발지 등 실제 표시 문구")
                        AppTextField(maxWon,{maxWon=it.filter(Char::isDigit).take(9)},label=when(errand.id){"finance"->if(billPay)"정확한 납부 총액(원)" else "정확한 이체 금액(원)";"telecom"->"최대 월 이용료(원)";else->"최대 승인 금액(원)"},placeholder="무료는 0")
                        if(errand.id in setOf("finance","telecom"))Text("개인정보와 인증은 공식 앱에서 직접 처리합니다. AI 서버에 화면을 보내지 않으며 확인된 최종 버튼만 승인 후 실행합니다.")
                        Text("실제 화면의 대상·조건·최종 금액을 대조하고 마지막 실행 버튼을 별도로 승인합니다. 정보가 다르면 확정하지 않습니다.")
                    }
                    if(canPublicRead&&!(canTransact&&transact)){
                        Row{Checkbox(publicRead,{publicRead=it});Text("공개 검색 결과 수집 및 완료",Modifier.padding(top=12.dp))}
                        if(publicRead)Text("상품·가격·업체 등 화면의 공개 결과를 확인 후 기록합니다. 주문·예약·로그인은 실행하지 않습니다.")
                    }
                    if(isKakao){
                        Row{Checkbox(sendMessage,{sendMessage=it});Text("직접 지정한 메시지 발송",Modifier.padding(top=12.dp))}
                    }
                    if(isKakao&&sendMessage){
                        AppTextField(recipient,{recipient=it.take(60)},label="정확한 대화방 이름·판매자")
                        if(isDaangn)AppTextField(listing,{listing=it.take(100)},label="정확한 거래 상품명")
                        AppTextField(messageBody,{messageBody=it.take(500)},label="발송할 메시지",singleLine=false,maxLines=5)
                        Text("메시지 원문은 기기에 저장합니다. 실제 대화방과 입력 내용을 확인한 뒤 발송 직전에 별도로 승인합니다.")
                        Row{Checkbox(waitReply,{waitReply=it});Text("발송 확인 후 48시간 답변 알림 대기",Modifier.padding(top=12.dp))}
                        if(waitReply){
                            AppTextField(negotiationGoal,{negotiationGoal=it.take(500)},label="반복 협상 목표 (선택)",
                                placeholder="가격 상한·가능한 시간·합의 조건",singleLine=false,maxLines=3)
                            if(negotiationGoal.isNotBlank())Text("최대 5회 발송·답변 조율을 유지합니다. 매 발송은 검토하며 합의 확인 후 다음 업무가 열립니다.")
                            Text("알림 접근 권한을 허용한 경우, 지정한 앱과 대화방 이름에 일치하는 새 알림만 기기에 보관합니다. 알림은 답변 후보이며 직접 확인이 필요합니다.")
                            TextButton(onClick={runCatching{context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}}){Text("알림 접근 권한 설정")}
                        }
                    }else if(!(canPrivate&&privateField!=null))AppTextField(details,{details=it.take(2000)},label=if(canTransact&&transact)"추가 업무 조건 (선택)" else "맡길 업무",placeholder=errand.examples,singleLine=false,maxLines=5)
                    state.characters.forEach{character->
                        Row{RadioButton(selected=agent==character.id,onClick={agent=character.id});Text(character.name,Modifier.padding(top=12.dp))}
                    }
                    val payload=if(isKakao&&sendMessage)runCatching{
                        com.dominion.mobile.data.KakaoMessageRequest(recipient.trim(),messageBody.trim(),
                            requireNotNull(selectedApp).packageName,if(isDaangn)listing.trim() else null,waitReply,
                            negotiationGoal.trim().takeIf{waitReply&&it.isNotBlank()}).checked()
                    }.getOrNull() else null
                    val privateRead=if(canPrivate&&!(canTransact&&transact))privateField?.let{
                        com.dominion.mobile.data.PhonePrivateRead(errand.id,it).checked(requireNotNull(selectedApp).packageName)
                    }else null
                    val transaction=if(canTransact&&transact)runCatching{
                        com.dominion.mobile.data.PhoneTransactionRequest(if(errand.id=="finance"&&billPay)"finance_bill" else errand.id,target.trim(),condition.trim(),
                            requireNotNull(maxWon.toLongOrNull())).checked(requireNotNull(selectedApp).packageName)
                    }.getOrNull() else null
                    Button(enabled=selectedApp!=null&&state.character(agent)!=null&&
                        (if(isKakao&&sendMessage)payload!=null else if(canTransact&&transact)transaction!=null else privateRead!=null||details.isNotBlank()),onClick={
                        val app=requireNotNull(selectedApp)
                        val request=if(payload!=null)"지정 앱 대화방 이름: "+payload.recipient+
                            (payload.listing?.let{"\n거래 상품: "+it}?:"")+
                            "\n지정한 대화방을 찾아 사용자 입력 메시지를 별도 승인 후 발송합니다. 메시지 본문은 기기에만 저장되어 있습니다."
                            else if(privateRead!=null)"기기 전용 개인 조회: "+privateRead.field
                            else if(transaction!=null&&com.dominion.mobile.data.PhoneTransactions.deviceOnly(transaction.kind))
                                "기기 전용 공식 앱 거래 · "+errand.id+" · 대상·조건·금액은 로컬 승인 화면에 표시"
                            else if(transaction!=null)"실제 "+errand.title+" 업무\n대상: "+transaction.target+
                                "\n조건: "+transaction.condition+"\n최대 승인 금액: "+transaction.maxWon+"원\n"+details
                            else com.dominion.mobile.data.KoreanErrands.request(errand.id,app.packageName,app.label,details)
                        vm.createPrivateErrand(errand.title,agent,request,app.packageName,onOpenChat,payload,
                            payload==null&&transaction==null&&canPublicRead&&publicRead,transaction,privateRead)
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
                    if(!nativeLookup&&!vm.localExecution)OutlinedButton(enabled = ready, onClick = {
                        institutionDraft = service.id to request()
                        selected = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("기관 실제 접수·인증·결제 연동") }
                    if(!nativeLookup&&vm.localExecution){
                        Button(enabled = true, onClick = {
                            runCatching {
                                context.startActivity(
                                    com.dominion.mobile.platform.web.GovernmentWebActivity.intent(context, service.id))
                            }.onFailure { context.toast("공식 사이트 화면을 열 수 없습니다.") }
                        }, modifier = Modifier.fillMaxWidth()) { Text("공식 사이트에서 신청·작성·제출") }
                        Text(
                            "공식 사이트에 직접 로그인·인증한 뒤 AI가 화면의 입력란과 첨부를 채웁니다. " +
                                "최종 제출은 별도 승인 후 실행하고 접수번호를 기기에 기록합니다.",
                            style=MaterialTheme.typography.bodySmall,color=p.textSecondary)
                    }
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
