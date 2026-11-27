package com.dominion.mobile.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.platform.audio.AssistantVoiceService
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.*

@Composable
fun AssistantVoiceDialog(vm:AppViewModel,room:String,onDismiss:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val current by AssistantVoiceService.state.collectAsState()
    var accepted by remember{mutableStateOf(false)}
    var starting by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        if(result[Manifest.permission.RECORD_AUDIO]!=true ||
            Build.VERSION.SDK_INT>=33 && result[Manifest.permission.POST_NOTIFICATIONS]!=true){
            error="마이크·알림 권한이 필요합니다."
        }else{
            starting=true
            scope.launch{
                try{
                    check(vm.state.value.call==null && !vm.state.value.settings.appLock)
                    withTimeout(15_000L){while(!vm.refreshConversationNow(room))delay(300)}
                    val session=requireNotNull(vm.roomExecution(room).sessionId)
                    check(vm.state.value.conversation(room)?.sessionId==session)
                    AssistantVoiceService.start(context,room,session)
                }catch(e:CancellationException){throw e}
                catch(_:Exception){error="본체 연결 또는 음성 서비스 시작 실패"}
                finally{starting=false}
            }
        }
    }
    AlertDialog(onDismissRequest={if(!starting)onDismiss()},title={Text("백그라운드 AI 음성 비서")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("현재 대화방에 음성으로 작업을 맡기고 본체 응답을 듣습니다. 화면을 닫아도 사용자 시작 마이크 서비스가 유지됩니다. 녹음과 답변은 교대하며, 알림에서 즉시 중단할 수 있습니다.")
            Text("3회 연속 발화가 없으면 종료합니다. 기기 재시작 후 녹음을 자동 재개하지 않으며 접수 작업만 동기화합니다.")
            if(!current.active)Row{
                Checkbox(accepted,{accepted=it},enabled=!starting)
                Text("백그라운드 마이크 사용 및 본체 전송 동의",Modifier.padding(top=12.dp))
            }
            Text(current.phase)
            if(current.active && current.roomId!=room)Text("다른 대화방에서 음성 비서가 실행 중입니다.")
            if(current.roomId==room && current.transcript.isNotBlank())Text("나: "+current.transcript.take(600))
            if(current.roomId==room && current.answer.isNotBlank())Text("AI: "+current.answer.take(900))
            if(starting || current.active)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(enabled=current.active || accepted&&!starting,onClick={
        if(current.active)AssistantVoiceService.stop(context)
        else permission.launch(if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO))
    }){Text(if(current.active)"음성 비서 중지" else "시작")}},dismissButton={TextButton(enabled=!starting,onClick=onDismiss){Text("닫기")}})
}
