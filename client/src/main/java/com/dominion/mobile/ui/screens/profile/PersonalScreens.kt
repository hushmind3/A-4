package com.dominion.mobile.ui.screens.profile

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import com.dominion.mobile.data.model.ScheduleItem
import com.dominion.mobile.data.ReminderTiming
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.CheckCircle
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphButton
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.AssistantAvatar
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.FilterChipItem
import com.dominion.mobile.ui.components.AppTextField
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.PrimaryDialog
import com.dominion.mobile.ui.components.SearchField
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SheetRow
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.TextPromptDialog
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asRelative

@Composable
fun MemoriesScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var selectedCharacter by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val filtered = state.memories.filter { it.text.contains(query, true) }
    val syncStatus by vm.memoryStatus.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refreshMemories() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "기억", "Memories"),
            subtitle = tr(lang, "${state.memories.size}개의 기억을 저장했어요", "${state.memories.size} memories saved"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, tr(lang, "기억 검색", "Search memories"))
            TextButton(onClick = vm::refreshMemories) { Text(if(vm.localExecution)"기기 기억 새로고침"else tr(lang, "본체 기억 새로고침", "Refresh engine memories")) }
            syncStatus?.let { Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.blue.copy(alpha = 0.08f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.MEMORY, p.blue, size = 19.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            tr(lang, "도미니온은 대화에서 중요한 내용을 기억해요. 직접 추가할 수도 있어요.", "Your Dominion remembers what matters. You can add memories too."),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { memory ->
                val character = state.character(memory.characterId)
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistantAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(character?.name ?: "Dominion", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(memory.createdAt.asRelative(lang), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "⋯",
                            color = p.textTertiary,
                            modifier = Modifier.clickable { deleteTarget = memory.id }.padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(memory.text, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(memory.source, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                    if (memory.serverBacked != true) {
                        Text(if(vm.localExecution)if(memory.retrievalEligible==true)"기기 기억 · 검색 대상"else"기기 기억"else
                            tr(lang, "기기 보관 · 본체 미동기화", "On device · not synced"), color = p.textSecondary)
                        TextButton(onClick = { vm.syncMemory(memory.id) }) { Text(if(vm.localExecution)"기기 검색에 반영"else tr(lang, "본체 기억으로 저장", "Sync to engine")) }
                    } else {
                        Text(if (memory.retrievalEligible == true) tr(lang, "본체 저장 · 검색 대상", "Engine · eligible for retrieval")
                            else tr(lang, "본체 저장 · 검색 상태 확인 필요", "Engine · check retrieval status"),
                            style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    com.dominion.mobile.ui.components.EmptyState(
                        glyph = Glyph.MEMORY,
                        title = tr(lang, "기억이 없어요", "No memories yet"),
                        body = tr(lang, "대화에서 기억에 저장하거나 직접 추가해보세요.", "Save from a chat or add one yourself."),
                        actionLabel = tr(lang, "기억 추가", "Add memory"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AppBottomSheet(onDismiss = { showAdd = false }) {
            var text by remember { mutableStateOf("") }
            SheetTitle(tr(lang, "기억 추가", "Add a memory"), tr(lang, "어떤 도미니온이 기억할까요?", "Which Dominion should remember?"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.characters, key = { it.id }) { character ->
                    val selected = selectedCharacter == character.id
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) p.actionPrimary else p.raised)
                            .clickable { selectedCharacter = character.id }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistantAvatar(Color(character.color), character.shape, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            character.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) p.onActionPrimary else p.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 24.dp)) {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = tr(lang, "예: 나는 아침에 커피를 마셔요", "e.g. I drink coffee in the morning"),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = tr(lang, "저장", "Save"),
                    enabled = text.isNotBlank() && selectedCharacter != null,
                    onClick = {
                        selectedCharacter?.let { vm.addMemory(it, text) }
                        showAdd = false
                    }
                )
            }
        }
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 기억을 삭제할까요?", "Delete this memory?"),
            body = tr(lang, "선택한 기억 기록을 삭제합니다. 원본 대화는 유지됩니다.", "Delete this memory record. Original conversations are retained."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteMemory(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
fun TasksScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var filter by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showWorkflow by remember { mutableStateOf(false) }
    var showAutoWorkflow by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val busyTasks by vm.taskBusy.collectAsStateWithLifecycle()
    var receiptTarget by remember { mutableStateOf<String?>(null) }
    var followupTarget by remember { mutableStateOf<String?>(null) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshDispatchedTasks()
                kotlinx.coroutines.delay(5000)
            }
        }
    }
    val tabs = listOf(tr(lang, "진행 중", "Active"), tr(lang, "완료", "Done"), tr(lang, "전체", "All"))
    val filtered = when (filter) {
        1 -> state.tasks.filter { it.done }
        2 -> state.tasks
        else -> state.tasks.filterNot { it.done }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "할 일", "Tasks"),
            subtitle = tr(lang, "${state.tasks.count { !it.done }}개 진행 중", "${state.tasks.count { !it.done }} active"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { index ->
                FilterChipItem(tabs[index], filter == index, onClick = { filter = index })
            }
        }
        if(vm.localExecution)TextButton(onClick={showAutoWorkflow=true},modifier=Modifier.padding(horizontal=16.dp)){
            Text("AI에게 복합 업무 맡기기")
        }
        if(vm.localExecution)TextButton(onClick={showWorkflow=true},modifier=Modifier.padding(horizontal=16.dp)){
            Text("비서 연계 업무 · 실행 순서 설정")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)) {
            items(filtered, key = { it.id }) { task ->
                TaskCard(
                    state,
                    task,
                    onToggleStep = { index -> vm.toggleTaskStep(task.id, index) },
                    onToggleDone = { vm.toggleTaskDone(task.id) },
                    onDelete = { deleteTarget = task.id },
                    onDispatch = { vm.dispatchTask(task.id) },
                    onOpenChat = { task.engineRoomId?.let(onOpenChat) },
                    onReceipt = { receiptTarget = task.id },
                    onFollowup = { followupTarget=task.id },
                    busy = task.id in busyTasks
                )
            }
            if (filtered.isEmpty()) {
                item {
                    com.dominion.mobile.ui.components.EmptyState(
                        glyph = Glyph.TASK,
                        title = tr(lang, "할 일이 없어요", "No tasks"),
                        body = tr(lang, "도미니온에게 일을 맡기거나 직접 추가해보세요.", "Ask your Dominion or add one yourself."),
                        actionLabel = tr(lang, "할 일 추가", "Add task"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if(showAutoWorkflow){
        val scope=androidx.compose.runtime.rememberCoroutineScope()
        var goal by remember{mutableStateOf("")}
        var preview by remember{mutableStateOf<com.dominion.mobile.data.ErrandWorkflowDraft?>(null)}
        var finalIds by remember{mutableStateOf<List<String>>(emptyList())}
        val finalCandidates=state.tasks.filter{com.dominion.mobile.data.TaskHandoff.eligible(it)&&
            it.phonePackage!=null&&(it.phoneTransaction!=null||it.kakaoMessage!=null)}
        var busy by remember{mutableStateOf(false)}
        var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={if(!busy)showAutoWorkflow=false},title={Text("복합 업무 자동 분해")},
            text={Column{
                AppTextField(goal,{goal=it.take(1500);preview=null;finalIds=emptyList()},label="전체 업무 목표",singleLine=false,maxLines=4)
                Text("AI가 조사·검색 단계를 배정합니다. 이미 등록한 주문·예약·발송 업무를 마지막에 연결할 수 있습니다. 최종 거래는 실제 화면에서 별도 승인합니다.")
                TextButton(enabled=!busy&&goal.trim().length>=3,onClick={
                    scope.launch{
                        busy=true;error=""
                        try{preview=vm.planErrandWorkflow(goal);finalIds=emptyList()}
                        catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(_:Exception){error="계획을 만들지 못했습니다. 요청 또는 연결을 확인하세요."}
                        finally{busy=false}
                    }
                }){Text(if(busy)"업무 분해 중" else "실행 계획 생성")}
                preview?.let{draft->
                    LazyColumn(modifier=Modifier.height(260.dp)){
                        itemsIndexed(draft.stages){index,stage->
                            Column(Modifier.padding(vertical=6.dp)){
                                Text("${index+1}. "+stage.title,style=MaterialTheme.typography.titleSmall)
                                Text((state.character(stage.agentId)?.name?:stage.agentId)+" · "+(stage.app?:"백그라운드 공개 조사"))
                                Text(stage.request,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        item{
                            Text("후속 실제 업무 선택 (선택 사항)",style=MaterialTheme.typography.titleSmall)
                            if(finalCandidates.isEmpty())Text("탐색 메뉴에서 주문·예약·발송 업무를 먼저 등록하면 여기에 연결할 수 있습니다.")
                        }
                        items(finalCandidates,key={it.id}){task->
                            val i=finalIds.indexOf(task.id)
                            TextButton(enabled=!busy,onClick={
                                finalIds=if(i>=0)finalIds.filterNot{it==task.id}
                                    else if(draft.stages.size+finalIds.size<6)finalIds+task.id else finalIds
                            }){
                                Text((if(i>=0)"${draft.stages.size+i+1}. " else "＋ ")+task.title+
                                    (task.phoneTransaction?.let{" · "+it.target+" / 최대 "+it.maxWon+"원"}?:""))
                            }
                        }
                    }
                }
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            }},
            confirmButton={TextButton(enabled=!busy&&preview!=null,onClick={
                vm.acceptErrandWorkflow(requireNotNull(preview),finalIds);showAutoWorkflow=false
            }){Text("검토한 단계 실행 등록")}},
            dismissButton={TextButton(enabled=!busy,onClick={showAutoWorkflow=false}){Text("닫기")}})
    }
    if(showWorkflow){
        var selected by remember{mutableStateOf<List<String>>(emptyList())}
        val eligible=state.tasks.filter(com.dominion.mobile.data.TaskHandoff::eligible)
        AlertDialog(onDismissRequest={showWorkflow=false},title={Text("비서 연계 업무")},
            text={
                Column{
                    Text("실행할 순서대로 2~4개 할 일을 선택하세요. 각 할 일에 지정된 비서가 담당합니다. 선행 업무의 실제 실행 근거가 있어야 다음 단계가 열립니다.")
                    LazyColumn(modifier=Modifier.height(260.dp)){
                        items(eligible,key={it.id}){task->
                            val index=selected.indexOf(task.id)
                            TextButton(onClick={
                                selected=if(index>=0)selected.filterNot{it==task.id}
                                    else if(selected.size<4)selected+task.id else selected
                            }){Text((if(index>=0)"${index+1}. " else "＋ ")+task.title+" · "+(state.character(task.characterId)?.name?:""))}
                        }
                    }
                    Text("앱 조작·주문·발송은 해당 단계에서 별도 승인합니다.")
                }
            },
            confirmButton={TextButton(enabled=selected.size in 2..4,onClick={vm.linkTaskWorkflow(selected);showWorkflow=false}){Text("연계 실행 등록")}},
            dismissButton={TextButton(onClick={showWorkflow=false}){Text("취소")}})
    }
    followupTarget?.let{id->
        val task=state.tasks.firstOrNull{it.id==id}
        if(task!=null&&task.replyCandidate!=null&&task.replyCandidateAt!=null&&task.kakaoMessage!=null){
            ErrandFollowupDialog(task,vm,onDismiss={followupTarget=null},onOpenChat=onOpenChat)
        }
    }
    receiptTarget?.let { id ->
        com.dominion.mobile.ui.components.TaskReceiptDialog(vm, id,
            onDismiss = { receiptTarget = null })
    }

    if (showAdd) {
        AddTaskDialog(
            state = state,
            onDismiss = { showAdd = false },
            onAdd = { title, characterId, due, steps ->
                vm.addTask(title, characterId, due, steps)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 할 일을 삭제할까요?", "Delete this task?"),
            body = tr(lang, "기기 체크리스트만 삭제합니다. 실행 중인 클라우드 작업은 결과 화면에서 먼저 중단하세요.", "Delete the device checklist only. Cancel active cloud work in its receipt first."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteTask(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun ErrandFollowupDialog(task:TaskItem,vm:AppViewModel,onDismiss:()->Unit,onOpenChat:(String)->Unit){
    val original=requireNotNull(task.kakaoMessage)
    val observedAt=requireNotNull(task.replyCandidateAt)
    val scope=androidx.compose.runtime.rememberCoroutineScope()
    var confirmed by remember(task.id,observedAt){mutableStateOf(task.replyReviewedAt!=null)}
    var goal by remember{mutableStateOf("")}
    var draft by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var appointment by remember{mutableStateOf<com.dominion.mobile.data.ReplyAppointment?>(null)}
    var appointmentSaved by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("답변 확인 및 후속 업무")},
        text={
            Column{
                Text("대화방: "+original.recipient)
                original.listing?.let{Text("상품: "+it)}
                Text("알림 후보: "+task.replyCandidate.orEmpty().take(500))
                if(!confirmed){
                    Text("실제 앱 대화와 일치하는지 확인하세요. 확인 후 연결한 다음 업무가 진행됩니다.")
                    TextButton(enabled=!busy,onClick={
                        scope.launch{
                            busy=true
                            try{vm.confirmErrandReply(task.id,observedAt);confirmed=true;error=""}
                            catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(_:Exception){error="답변 확인 실패"}
                            finally{busy=false}
                        }
                    }){Text("실제 대화 확인 완료")}
                }else{
                    AppTextField(goal,{goal=it.take(500)},label="다음 응답 목표",placeholder="약속 시간을 확정해 주세요",singleLine=false,maxLines=3)
                    TextButton(enabled=!busy&&goal.isNotBlank(),onClick={
                        scope.launch{
                            busy=true
                            try{draft=vm.draftErrandReply(task.id,observedAt,goal);error=""}
                            catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(_:Exception){error="AI 초안 생성 실패. 직접 작성할 수 있습니다."}
                            finally{busy=false}
                        }
                    }){Text("선택한 메시지를 AI에 보내 답장 초안 생성")}
                    AppTextField(draft,{draft=it.take(500)},label="실제 보낼 답장",singleLine=false,maxLines=5)
                    Text("발송 업무 등록 후 기존 화면·대상 검증과 최종 승인을 다시 진행합니다.")
                    TextButton(enabled=!busy&&!appointmentSaved,onClick={
                        scope.launch{
                            busy=true;error=""
                            try{
                                appointment=vm.suggestReplyAppointment(task.id,observedAt)
                                if(appointment==null)error="답변에서 명확한 약속 날짜·시간을 찾지 못했습니다."
                            }catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(_:Exception){error="약속 추출 실패"}
                            finally{busy=false}
                        }
                    }){Text("확인한 대화에서 약속 일정 추출")}
                    appointment?.let{candidate->
                        Text("약속 후보 · "+candidate.title+" · "+candidate.at)
                        Text("근거: "+candidate.evidence,style=MaterialTheme.typography.bodySmall)
                        TextButton(enabled=!busy&&!appointmentSaved,onClick={
                            scope.launch{
                                busy=true
                                try{vm.saveReplyAppointment(task.id,observedAt,candidate);appointmentSaved=true;error=""}
                                catch(e:kotlinx.coroutines.CancellationException){throw e}
                                catch(_:Exception){error="일정을 저장하지 못했습니다."}
                                finally{busy=false}
                            }
                        }){Text(if(appointmentSaved)"기기 일정 저장 완료" else "확인한 날짜·시간으로 일정 등록")}
                    }
                }
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={TextButton(enabled=confirmed&&!busy&&draft.isNotBlank(),onClick={
            val next=original.copy(body=draft.trim()).checked()
            val request="지정 앱 대화방: "+next.recipient+
                (next.listing?.let{"\n거래 상품: "+it}?:"")+"\n사용자가 지정한 후속 메시지를 실제 대화방 확인 후 발송"
            vm.createPrivateErrand("후속 답장 · "+next.recipient,task.characterId,request,next.app,onOpenChat,next)
            onDismiss()
        }){Text("후속 발송 업무 등록")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("닫기")}})
}

@Composable
private fun TaskCard(
    state: AppState,
    task: TaskItem,
    onToggleStep: (Int) -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onDispatch: () -> Unit,
    onOpenChat: () -> Unit,
    onReceipt: () -> Unit,
    onFollowup:()->Unit,
    busy: Boolean
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val character = state.character(task.characterId)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .border(0.5.dp, p.hairline, RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                )
                Text("${character?.name ?: ""} · ${task.due}", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
            Text(
                "⋯",
                color = p.textTertiary,
                modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 6.dp, vertical = 4.dp)
            )
            CheckCircle(task.done, onClick = onToggleDone)
        }
        Spacer(Modifier.height(8.dp))
        Text(tr(lang, "체크리스트는 직접 관리 · AI 실행은 본체 상태로 표시", "Manual checklist · engine execution status shown separately"),
            style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
        task.workflowState?.let{status->
            Text("연계 업무 · "+when(status){
                "waiting"->"선행 작업 근거 대기"
                "ready"->"다음 단계 실행 가능"
                "running"->"실행 중"
                "verified"->"근거 확인 · 다음 단계 전달"
                "blocked"->"실행 근거 부족 · 연계 중단"
                else->status
            },style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
        }
        task.replyWatchUntil?.let{until->
            Text(if(until>System.currentTimeMillis())"지정 대화방 답변 알림 대기 중 (최대 48시간)" else "답변 대기 기간 종료",
                style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
        }
        task.replyCandidate?.let{
            Text("답변 알림 후보 · 대화방에서 확인 필요",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
            if(state.settings.hideContent||!state.settings.messagePreview)Text("알림 내용은 숨김 설정에 따라 표시하지 않습니다.")
            else Text(it.take(500),style=MaterialTheme.typography.bodySmall,color=p.textPrimary)
            TextButton(onClick=onFollowup){Text(if(task.replyReviewedAt==null)"답변 확인·후속 업무" else "후속 답장 작성")}
        }
        task.engineStatus?.let { status ->
            val label = when (status) {
                "submitting", "cloud_submitting" -> tr(lang, "접수 확인 필요", "Submission needs verification")
                "queued" -> tr(lang, "작업 대기", "Queued")
                "running" -> tr(lang, "실행 중", "Running")
                "completed", "done" -> tr(lang, "AI 작업 완료", "AI completed")
                "failed" -> tr(lang, "본체 작업 실패", "Engine failed")
                "cancelled" -> tr(lang, "작업 중단", "Cancelled")
                else -> status
            }
            Text(label + " · " + tr(lang, "마지막 조회 상태", "Last known status"), color = p.textSecondary)
        }
        Row {
            TextButton(enabled = !busy&&task.workflowState !in setOf("waiting","blocked","verified"), onClick = onDispatch) {
                Text(if (busy) tr(lang, "확인 중…", "Checking…")
                    else if (task.engineJobId != null) tr(lang, "상태 확인", "Check status")
                    else if (task.engineRoomId != null) tr(lang, "접수 재확인", "Recheck submission")
                    else tr(lang, "AI에게 맡기기", "Assign to AI"))
            }
            if (task.engineRoomId != null && state.conversation(task.engineRoomId)!=null) TextButton(onClick = onOpenChat) { Text(tr(lang, "작업 대화 열기", "Open job chat")) }
        }
        if (task.engineJobId != null) TextButton(onClick = onReceipt) {
            Text(tr(lang, "실제 실행 결과·근거", "Execution result and evidence"))
        }
        if (task.steps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            task.steps.forEachIndexed { index, step ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleStep(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckCircle(step.done)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        step.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.done) p.textTertiary else p.textPrimary,
                        textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(p.control)) {
                    Box(Modifier.fillMaxWidth(task.progress).height(4.dp).clip(CircleShape).background(p.blue))
                }
                Spacer(Modifier.width(8.dp))
                Text("${(task.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, List<String>) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var characterId by remember { mutableStateOf(state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(tr(lang, "할 일 추가", "Add a task"), style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = {
            Column {
                AppTextField(title, { title = it }, placeholder = tr(lang, "무엇을 할까요?", "What needs doing?"))
                Spacer(Modifier.height(8.dp))
                AppTextField(due, { due = it }, placeholder = tr(lang, "언제까지? 예: 오늘 오후 3시", "When? e.g. today 3pm"))
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    steps, { steps = it },
                    placeholder = tr(lang, "단계는 줄바꿈으로 구분해요", "Separate steps with new lines"),
                    singleLine = false, maxLines = 4
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.characters.size) { index ->
                        val character = state.characters[index]
                        val selected = characterId == character.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) p.actionPrimary else p.raised)
                                .clickable { characterId = character.id }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistantAvatar(Color(character.color), character.shape, size = 20.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(character.name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.onActionPrimary else p.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        title,
                        characterId,
                        due.ifBlank { tr(lang, "오늘", "Today") },
                        steps.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    )
                }
            ) { Text(tr(lang, "추가", "Add"), color = if (title.isNotBlank()) p.blue else p.textDisabled) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "취소", "Cancel"), color = p.textSecondary) } }
    )
}

@Composable
fun SchedulesScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onAgenda: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<ScheduleItem?>(null) }
    val context = LocalContext.current
    var permission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val upcoming = state.schedules.filter { it.enabled && it.scheduledAt != null }
        .mapNotNull { item -> ReminderTiming.nextUndelivered(item, System.currentTimeMillis())?.let { item to it } }
        .sortedBy { it.second }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "일정", "Schedules"),
            subtitle = tr(lang, "기기 예약 " + upcoming.size + "개", upcoming.size.toString() + " device reminders"),
            onBack = onBack,
            actions = { GlyphButton(Glyph.PLUS, onClick = { showAdd = true }) }
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                TextButton(onClick = onAgenda, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(tr(lang, "휴대폰 캘린더 일정 확인", "View phone calendar"))
                }
                Text(tr(lang, "기기에서 알림을 예약합니다. 배터리 정책으로 시간이 늦어질 수 있습니다.", "Device reminders may be delayed by Android battery policies."),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = p.textTertiary)
                if (!permission || !state.settings.notifications) {
                    Text(tr(lang, "알림이 꺼져 있습니다. 예약을 유지하지만 알림은 표시되지 않습니다.", "Notifications are off; reminders remain saved."),
                        modifier = Modifier.padding(horizontal = 18.dp), color = p.textSecondary)
                    TextButton(onClick = {
                        vm.setNotifications(true)
                        if (Build.VERSION.SDK_INT >= 33 && !permission)
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                        }
                    }) { Text(tr(lang, "알림 설정", "Enable notifications")) }
                }
            }
            item {
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.cardSoft)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(tr(lang, "다가오는 일정", "Coming up"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        upcoming.take(2).forEach { (schedule, due) ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.CALENDAR, p.blue, size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(schedule.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                                    Text(ReminderTiming.format(due, schedule.timeZone), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                                }
                            }
                        }
                        if (upcoming.isEmpty()) {
                            Text(tr(lang, "켜져 있는 일정이 없어요.", "No active reminders."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                        }
                    }
                }
            }
            items(state.schedules, key = { it.id }) { schedule ->
                val character = state.character(schedule.characterId)
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .clickable { editTarget = schedule; showAdd = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistantAvatar(Color(character?.color ?: 0xFF4797EF), character?.shape ?: com.dominion.mobile.data.model.CharacterShape.BLOB, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(schedule.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(
                            (schedule.scheduledAt?.let { ReminderTiming.format(it, schedule.timeZone) }
                                ?: schedule.whenText + " · " + tr(lang, "시간 지정 필요", "Choose time")) + " · " + schedule.repeat.label(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary
                        )
                    }
                    if (schedule.scheduledAt != null) {
                        TextButton(onClick = {
                            val due = ReminderTiming.nextUndelivered(schedule, System.currentTimeMillis()) ?: schedule.scheduledAt
                            if (!com.dominion.mobile.platform.DeviceActions.calendar(context, schedule.title,
                                    due, schedule.whenText, schedule.repeat))
                                context.toast(tr(lang, "캘린더 앱을 열 수 없습니다.", "Calendar app unavailable"))
                        }) { Text(tr(lang, "달력", "Calendar")) }
                    }
                    if (!schedule.workRequest.isNullOrBlank()) {
                        Text((if (schedule.watchCondition.isNullOrBlank()) "" else "조건 감시 · ") +
                            (schedule.dispatchStatus ?: "AI 예약"), color = p.blue, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { deleteTarget = schedule.id }) { Text("⋯") }
                    Switch(
                        checked = schedule.enabled && schedule.scheduledAt != null,
                        onCheckedChange = {
                            if (schedule.scheduledAt == null) { editTarget = schedule; showAdd = true }
                            else vm.toggleSchedule(schedule.id)
                        },
                        colors = com.dominion.mobile.ui.components.appSwitchColors()
                    )
                }
            }
            if (state.schedules.isEmpty()) {
                item {
                    com.dominion.mobile.ui.components.EmptyState(
                        glyph = Glyph.CALENDAR,
                        title = tr(lang, "일정이 없어요", "No schedules"),
                        body = tr(lang, "도미니온이 알려줄 일정을 추가해보세요.", "Add something your Dominion should remind you about."),
                        actionLabel = tr(lang, "일정 추가", "Add schedule"),
                        onAction = { showAdd = true }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            state = state, existing = editTarget,
            onDismiss = { showAdd = false; editTarget = null },
            onAdd = { title, whenText, characterId, repeat, due, zone, request, condition ->
                vm.saveSchedule(editTarget?.id, title, whenText, characterId, repeat, due, zone, request,
                    watchCondition = condition) {
                    showAdd = false; editTarget = null
                }
            }
        )
    }

    deleteTarget?.let { id ->
        PrimaryDialog(
            title = tr(lang, "이 일정을 삭제할까요?", "Delete this schedule?"),
            body = tr(lang, "향후 예약 접수를 중지합니다. 이미 본체에 전달된 작업은 할 일에서 별도로 확인·중단하세요.", "Stop future submissions; review or cancel already submitted jobs separately."),
            confirmLabel = tr(lang, "삭제", "Delete"),
            onConfirm = { vm.deleteSchedule(id); deleteTarget = null },
            dismissLabel = tr(lang, "취소", "Cancel"),
            onDismiss = { deleteTarget = null },
            destructive = true
        )
    }
}

@Composable
private fun AddScheduleDialog(
    state: AppState,
    onDismiss: () -> Unit,
    existing: ScheduleItem?,
    onAdd: (String, String, String, Repeat, Long, String, String?, String?) -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var whenText by remember(existing?.id) { mutableStateOf(existing?.whenText.orEmpty()) }
    var runWork by remember(existing?.id) { mutableStateOf(!existing?.workRequest.isNullOrBlank()) }
    var workRequest by remember(existing?.id) { mutableStateOf(existing?.workRequest.orEmpty()) }
    var conditional by remember(existing?.id) { mutableStateOf(!existing?.watchCondition.isNullOrBlank()) }
    var condition by remember(existing?.id) { mutableStateOf(existing?.watchCondition.orEmpty()) }
    var repeat by remember(existing?.id) { mutableStateOf(existing?.repeat ?: Repeat.NONE) }
    val zone = remember(existing?.id) { runCatching { ZoneId.of(existing?.timeZone ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault()) }
    var due by remember(existing?.id) { mutableStateOf(existing?.let { ReminderTiming.nextUndelivered(it, System.currentTimeMillis()) }
        ?.takeIf { it > System.currentTimeMillis() } ?: ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0).toInstant().toEpochMilli()) }
    var characterId by remember(existing?.id) { mutableStateOf(existing?.characterId ?: state.myCharacter()?.id ?: state.characters.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(tr(lang, "일정 추가", "Add a schedule"), style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = {
            Column {
                AppTextField(title, { title = it }, placeholder = tr(lang, "무엇을 알려줄까요?", "What should we remind you about?"))
                Spacer(Modifier.height(8.dp))
                AppTextField(whenText, { whenText = it }, placeholder = tr(lang, "메모 (선택)", "Note (optional)"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = runWork, onCheckedChange = { runWork = it })
                    Text(tr(lang, "예약 시간에 AI 업무도 접수", "Submit AI work on schedule"))
                }
                if (runWork) {
                    AppTextField(workRequest, { workRequest = it.take(3000) },
                        placeholder = "예: 공식 기관의 새 공고를 조사해줘", singleLine = false, maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = conditional, onCheckedChange = { conditional = it })
                        Text("조건 충족 시에만 결과 알림")
                    }
                    if (conditional) {
                        AppTextField(condition, { condition = it.take(600) }, label = "감시 조건",
                            placeholder = "예: 공식 공고에 새 모집 일정이 발표됨", singleLine = false, maxLines = 3)
                        Text("본체 AI가 근거를 제시한 판단을 알립니다. 판단 불가나 실패도 알립니다.",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("휴대폰이 켜지고 본체에 연결될 때 접수합니다. 알림 설정과 별개이며 지연될 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall)
                }
                if (existing?.pendingDispatchAt != null) Text("접수 확인 중인 업무가 있습니다. 할 일에서 상태를 확인하세요.",
                    color = p.textSecondary)
                TextButton(onClick = {
                    val local = Instant.ofEpochMilli(due).atZone(zone)
                    DatePickerDialog(context, { _, year, month, day ->
                        val chosen = java.time.LocalDate.of(year, month + 1, day)
                        TimePickerDialog(context, { _, hour, minute ->
                            due = chosen.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        }, local.hour, local.minute, true).show()
                    }, local.year, local.monthValue - 1, local.dayOfMonth).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) { Text(ReminderTiming.format(due, zone.id)) }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Repeat.entries) { option ->
                        FilterChipItem(option.label(lang), repeat == option, onClick = { repeat = option })
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.characters.size) { index ->
                        val character = state.characters[index]
                        val selected = characterId == character.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) p.actionPrimary else p.raised)
                                .clickable { characterId = character.id }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistantAvatar(Color(character.color), character.shape, size = 20.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(character.name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.onActionPrimary else p.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && due > System.currentTimeMillis() && existing?.pendingDispatchAt == null &&
                    (!runWork || (workRequest.isNotBlank() && characterId.isNotBlank() && (!conditional || condition.isNotBlank()))),
                onClick = { onAdd(title, whenText.ifBlank { ReminderTiming.format(due, zone.id) }, characterId, repeat, due, zone.id,
                    workRequest.takeIf { runWork }, condition.takeIf { runWork && conditional }) }
            ) { Text(tr(lang, "예약 저장", "Save reminder"), color = if (title.isNotBlank()) p.blue else p.textDisabled) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "취소", "Cancel"), color = p.textSecondary) } }
    )
}
