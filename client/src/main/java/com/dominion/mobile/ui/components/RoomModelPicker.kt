package com.dominion.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.*
import kotlinx.coroutines.*

private data class ModelChoice(val provider:String,val model:String,val label:String,val cloud:Boolean)

@Composable
fun RoomModelPicker(roomId:String,session:String?,onDismiss:()->Unit) {
    val container=(LocalContext.current.applicationContext as DominionApplication).container
    val connection=remember(roomId,session){container.engine}
    val scope=rememberCoroutineScope()
    var options by remember{mutableStateOf<List<ModelChoice>>(emptyList())}
    var current by remember{mutableStateOf<EngineRoom?>(null)}
    var selected by remember{mutableStateOf<ModelChoice?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var filter by remember{mutableStateOf("")}
    var registration by remember{mutableStateOf(false)}
    val cloud=container.cloudAccount
    var cloudModels by remember{mutableStateOf<List<CloudModel>>(emptyList())}
    var cloudSelected by remember{mutableStateOf("")}
    fun execute(block:suspend ()->Unit) {
        if(busy)return
        busy=true;error=""
        scope.launch {
            try{block()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error="본체 연결·모델 설정 또는 계정 상태를 확인하세요."}
            finally{busy=false}
        }
    }
    fun api():DominionApi {
        check(connection!=null && container.engine===connection)
        check(!session.isNullOrBlank() && container.repository.state.value.conversation(roomId)?.sessionId==session)
        return ApiFactory.create(requireNotNull(connection))
    }
    suspend fun load() {
        val server=api()
        val live=server.engineRoom(roomId)
        check(live.id==roomId && live.sessionId==session)
        val providers=server.cloudProviderRecords()
        options=providers.mapNotNull{p->
            val id=p.get("id")?.takeIf{it.isJsonPrimitive}?.asString.orEmpty()
            val model=p.get("model")?.takeIf{it.isJsonPrimitive}?.asString.orEmpty()
            val name=p.get("name")?.takeIf{it.isJsonPrimitive}?.asString.orEmpty()
            val base=p.get("baseUrl")?.takeIf{it.isJsonPrimitive}?.asString.orEmpty()
            if(id.isBlank() || model.isBlank() || p.get("enabled")?.asBoolean==false)null
            else ModelChoice(id,model,name.ifBlank{model},base.trimEnd('/')=="$CLOUD_ORIGIN/v1")
        }.distinctBy{it.provider to it.model}
        current=live
        selected=options.firstOrNull{o->live.models.size==1 && !live.autoModels &&
            live.models.single().providerId==o.provider && live.models.single().model==o.model}
        // Cloud authentication is independent; an unavailable cloud never hides local providers.
        try{cloudModels=cloud.refresh().models;cloudSelected=cloudModels.firstOrNull()?.id.orEmpty()}
        catch(e:CancellationException){throw e}
        catch(_:Exception){cloudModels=emptyList()}
    }
    LaunchedEffect(connection,roomId,session){execute{load()}}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("현재 대화방 실행 모델")},text={
        Column(Modifier.heightIn(max=490.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("본체의 기존 모델 공급자와 하네스를 그대로 사용합니다.")
            current?.let{r->Text(if(r.autoModels)"현재 자동 모델 선택" else "현재: "+r.models.joinToString{it.model})}
            OutlinedTextField(filter,{filter=it.take(100)},label={Text("모델 검색")},singleLine=true)
            LazyColumn(Modifier.heightIn(max=230.dp)){
                items(options.filter{(it.label+" "+it.model).contains(filter,true)},key={it.provider+"|"+it.model}){choice->
                    Row{
                        RadioButton(selected==choice,{selected=choice},enabled=!busy)
                        Column(Modifier.padding(top=10.dp)){
                            Text(choice.label)
                            Text((if(choice.cloud)"Cloudflare · " else "본체 공급자 · ")+choice.model,
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if(cloudModels.isNotEmpty()) {
                Text("Google 계정 무료 모델 등록",style=MaterialTheme.typography.titleSmall)
                var expand by remember{mutableStateOf(false)}
                Box{
                    TextButton(enabled=!busy,onClick={expand=true}){Text(cloudSelected)}
                    DropdownMenu(expand,{expand=false}){
                        cloudModels.forEach{m->DropdownMenuItem(text={Text(m.name)},onClick={cloudSelected=m.id;expand=false})}
                    }
                }
                TextButton(enabled=!busy && cloudSelected.isNotBlank(),onClick={registration=true}){Text("무료 모델을 본체에 추가")}
            }else Text("Cloudflare 무료 모델은 설정의 DOMINION Cloud에서 Google 계정을 먼저 연결하세요.",
                style=MaterialTheme.typography.bodySmall)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            TextButton(enabled=!busy,onClick={execute{load()}}){Text("모델 목록 갱신")}
        }
    },confirmButton={TextButton(enabled=!busy && current!=null && selected!=null,onClick={
        val choice=selected?:return@TextButton
        execute{
            val server=api()
            val before=server.engineRoom(roomId)
            val snapshot=server.roomExecution(roomId)
            check(before.id==roomId && before.sessionId==session && snapshot.sessionId==session)
            check(snapshot.jobs.orEmpty().none{it.status in setOf("queued","running","paused","needs_review","interrupted")})
            val saved=server.updateRoom(roomId,RoomConfigUpdate(toolsEnabled=before.toolsEnabled,
                autoModels=false,models=listOf(RoomModel(choice.provider,choice.model))))
            val confirmed=server.engineRoom(roomId)
            check(saved.id==roomId && confirmed.sessionId==session && !confirmed.autoModels &&
                confirmed.toolsEnabled==before.toolsEnabled && confirmed.models.size==1 &&
                confirmed.models.single().providerId==choice.provider && confirmed.models.single().model==choice.model)
            current=confirmed
            onDismiss()
        }
    }){Text("현재 방에 적용")}},dismissButton={TextButton(enabled=!busy,onClick={
        if(current!=null)execute{
            val server=api();val before=server.engineRoom(roomId)
            check(before.sessionId==session)
            val snapshot=server.roomExecution(roomId)
            check(snapshot.sessionId==session && snapshot.jobs.orEmpty().none{it.status in setOf("queued","running","paused","needs_review","interrupted")})
            server.updateRoom(roomId,RoomConfigUpdate(toolsEnabled=before.toolsEnabled,autoModels=true))
            val confirmed=server.engineRoom(roomId)
            check(confirmed.sessionId==session && confirmed.autoModels)
            onDismiss()
        }else onDismiss()
    }){Text(if(current!=null)"자동 모델로 전환" else "닫기")}})
    if(registration)AlertDialog(onDismissRequest={registration=false},title={Text("무료 모델 등록 승인")},
        text={Text("현재 Google 계정의 무료 추론 전용 세션을 "+(connection?.origin?:"본체")+"의 공급자 저장소에 등록합니다. 결제 권한은 공유하지 않습니다.")},
        confirmButton={TextButton(enabled=!busy && connection!=null,onClick={
            registration=false
            execute{
                val engine=requireNotNull(connection)
                val id=cloud.registerEngineModel(cloudSelected,engine){container.engine===engine}
                load()
                selected=options.firstOrNull{it.provider==id}
            }
        }){Text("등록")}},dismissButton={TextButton(onClick={registration=false}){Text("취소")}})
}
