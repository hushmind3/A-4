package com.dominion.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.remote.*
import kotlinx.coroutines.*

@Composable
internal fun CloudPcPanel(repo:CloudRemoteAccount,account:String,engine:EngineConnection?,
    onConnect:suspend(EngineConnection)->Unit,onDisconnect:suspend()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var state by remember(account){mutableStateOf(RemotePcState())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    fun open(url:String){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
    fun runAction(block:suspend()->Unit){
        if(busy)return
        busy=true;error=""
        scope.launch{
            try{block()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error="계정 승인 또는 PC 연결을 확인하세요."}
            finally{busy=false}
        }
    }
    LaunchedEffect(repo,account){
        while(isActive){
            if(!busy)try{
                state=repo.refresh(account)
                // Discovery is automatic; switching the execution target is explicit.
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error="클라우드 중계 상태 조회 실패"}
            delay(if(state.status=="pending")3000 else 60000)
        }
    }
    Text("선택 기능 · PC 원격 업무",style=MaterialTheme.typography.titleMedium)
    Text("본체 포트 4317 유지 · Cloudflare WebSocket · PC 등록 권한과 무료 모델 권한은 분리")
    when(state.status){
        "pending"->{
            Text("동일 Google 계정으로 PC 제어 권한을 승인하세요.")
            state.approvalUrl?.let{url->OutlinedButton(enabled=!busy,onClick={open(url)}){Text("Google 승인 페이지")}}
        }
        "approved"->{
            if(state.devices.isEmpty())Text("등록된 4317 본체 없음 · PC에서 전용 연결기 실행 필요")
            state.devices.forEach{pc->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){
                        Text(pc.name)
                        Text(if(pc.online)"온라인" else "오프라인",style=MaterialTheme.typography.labelSmall)
                    }
                    TextButton(enabled=pc.online&&!busy,onClick={runAction{onConnect(repo.connection(account,pc.id))}}){
                        Text(if(engine?.relayDeviceId==pc.id)"다시 연결" else "연결")
                    }
                }
            }
            Text("서버 푸시: "+if(state.nativePushReady)"설정됨" else "FCM 설정 필요")
            state.inbox.takeLast(5).reversed().forEach{notice->
                Text(notice.text.take(200),style=MaterialTheme.typography.bodySmall)
            }
        }
        "account_mismatch"->Text("다른 Google 계정의 PC 권한이 저장되어 있습니다. 연결 권한을 해제하고 다시 승인하세요.")
        else->Button(enabled=!busy,onClick={runAction{
            state=repo.begin(account);state.approvalUrl?.let(::open)
        }}){Text("동일 Google 계정으로 PC 연결 승인")}
    }
    if(state.status in setOf("pending","approved","account_mismatch"))OutlinedButton(enabled=!busy,onClick={runAction{
        onDisconnect();repo.disconnect();state=RemotePcState()
    }}){Text("PC 원격 권한 해제")}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
}
