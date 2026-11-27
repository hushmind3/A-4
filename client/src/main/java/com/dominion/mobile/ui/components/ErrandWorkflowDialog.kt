package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.launch

/** Shared review surface: chat and Tasks use the same validated workflow registration. */
@Composable
fun ErrandWorkflowDialog(vm:AppViewModel,state:AppState,initialGoal:String="",onDismiss:()->Unit){
        val scope=androidx.compose.runtime.rememberCoroutineScope()
        var goal by remember(initialGoal){mutableStateOf(initialGoal)}
        var preview by remember{mutableStateOf<com.dominion.mobile.data.ErrandWorkflowDraft?>(null)}
        var finalIds by remember{mutableStateOf<List<String>>(emptyList())}
        var approvedExternal by remember{mutableStateOf<Set<Int>>(emptySet())}
        val finalCandidates=state.tasks.filter{com.dominion.mobile.data.TaskHandoff.eligible(it)&&
            it.phonePackage!=null&&(it.phoneTransaction!=null||it.kakaoMessage!=null)}
        var busy by remember{mutableStateOf(false)}
        var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("복합 업무 자동 분해")},
            text={Column{
                AppTextField(goal,{if(!busy){goal=it.take(1500);preview=null;finalIds=emptyList();approvedExternal=emptySet()}},label="전체 업무 목표",singleLine=false,maxLines=4)
                Text("AI가 조사부터 주문·예약·발송까지 필요한 업무를 제안합니다. 공개 조사 실패는 다른 방법으로 최대 1회 자동 재시도합니다. 외부 업무는 자동 재실행하지 않으며 실제 실행 직전 별도 승인합니다.")
                TextButton(enabled=!busy&&goal.trim().length>=3,onClick={
                    if(busy)return@TextButton
                    val requested=goal.trim()
                    busy=true;error=""
                    scope.launch{
                        try{
                            val draft=vm.planErrandWorkflow(requested)
                            check(goal.trim()==requested&&draft.goal==requested)
                            preview=draft;finalIds=emptyList();approvedExternal=emptySet()
                        }
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
                                stage.transaction?.let{tx->
                                    Text("실제 거래 제안: "+tx.kind+" · "+tx.target+" · "+tx.condition+
                                        " · 최대 "+tx.maxWon+"원")
                                }
                                stage.message?.let{msg->
                                    Text("발송 대상: "+msg.recipient+(msg.listing?.let{" · "+it}?:""))
                                    Text("발송 초안: "+msg.body)
                                }
                                if(stage.external)Row{
                                    Checkbox(index in approvedExternal,{checked->
                                        approvedExternal=if(checked)approvedExternal+index else approvedExternal-index
                                    },enabled=!busy)
                                    Text("이 대상과 내용으로 후속 업무 등록 승인",Modifier.padding(top=12.dp))
                                }
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
            confirmButton={TextButton(enabled=!busy&&preview?.let{draft->
                approvedExternal==draft.stages.indices.filter{draft.stages[it].external}.toSet()
            }==true,onClick={
                if(busy)return@TextButton
                val accepted=requireNotNull(preview)
                val selectedIds=finalIds.toList()
                val approvals=approvedExternal.toSet()
                busy=true;error=""
                scope.launch{
                    try{
                        check(accepted.goal==goal.trim())
                        vm.acceptErrandWorkflow(accepted,selectedIds,approvals)
                        onDismiss()
                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(_:Exception){error="업무 접수 확인 실패 · 같은 계획으로 다시 시도할 수 있습니다."}
                    finally{busy=false}
                }
            }){Text("검토한 단계 실행 등록")}},
            dismissButton={TextButton(enabled=!busy,onClick={onDismiss()}){Text("닫기")}})
}
