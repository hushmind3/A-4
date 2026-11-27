package com.dominion.mobile.ui.screens.explore

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
import com.dominion.mobile.data.remote.*
import kotlinx.coroutines.*

@Composable
internal fun PhoneBridgeDialog(onDismiss:()->Unit){
    val context=LocalContext.current
    val container=(context.applicationContext as DominionApplication).container
    val account=container.cloudAccount.savedAccountId()
    if(account==null){
        AlertDialog(onDismissRequest=onDismiss,title={Text("전화 대행")},
            text={Text("DOMINION 계정 로그인 후 사용하세요.")},
            confirmButton={TextButton(onClick=onDismiss){Text("닫기")}})
        return
    }
    val bridge=remember(account){PhoneBridge(context,account)}
    val saved=remember(bridge){runCatching{bridge.load()}.getOrNull()}
    val scope=rememberCoroutineScope()
    var origin by remember{mutableStateOf(saved?.config?.origin.orEmpty())}
    var token by remember{mutableStateOf(saved?.config?.token.orEmpty())}
    var number by remember{mutableStateOf(saved?.pending?.number.orEmpty())}
    var goal by remember{mutableStateOf(saved?.pending?.goal.orEmpty())}
    var pending by remember{mutableStateOf(saved?.pending)}
    var receipt by remember{mutableStateOf<PhoneBridgeReceipt?>(null)}
    var health by remember{mutableStateOf<PhoneBridgeHealth?>(null)}
    var missing by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    fun config()=PhoneBridgeConfig(origin,token).checked()
    fun validAccount()=check(container.cloudAccount.savedAccountId()==account)
    fun action(method:String,call:PhoneBridgeCall){
        scope.launch{
            busy=true;error=""
            try{
                validAccount()
                val cfg=config()
                if(method=="start"){
                    // Journal before POST. Unknown network results are queried,
                    // never silently redialed with a new identifier.
                    bridge.save(PhoneBridgeState(cfg,call));pending=call;receipt=null;missing=false
                }
                val result=bridge.request(cfg,call,method)
                validAccount()
                receipt=result;missing=result==null
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error="통화 상태를 확인하지 못했습니다. 동일 업무의 상태를 조회하세요. 자동 재발신하지 않습니다."}
            finally{busy=false}
        }
    }
    fun checkHealth(){
        if(busy)return
        scope.launch{
            busy=true;error="";health=null
            try{
                validAccount()
                val result=bridge.health(config())
                validAccount()
                health=result
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error="전화 어댑터 연결을 확인하지 못했습니다. HTTPS 주소·토큰·서버 상태를 확인하세요."}
            finally{busy=false}
        }
    }
    LaunchedEffect(bridge,pending?.id){
        if(pending!=null){
            while(isActive&&container.cloudAccount.savedAccountId()==account){
                if(!busy&&receipt?.terminal!=true&&!missing){
                    try{receipt=bridge.request(config(),requireNotNull(pending),"status");missing=receipt==null}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){}
                }
                delay(4000)
            }
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("AI 외부 전화 대행")},
        text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("자체 SIP 연결 어댑터가 필요한 실제 업체 전화 문의입니다. 발신 비용이 발생할 수 있습니다. 상대에게 AI임을 밝히고 동의 후 문의합니다.")
            OutlinedTextField(origin,{origin=it},enabled=pending==null&&!busy,label={Text("전화 어댑터 HTTPS 주소")},singleLine=true)
            OutlinedTextField(token,{token=it},enabled=pending==null&&!busy,label={Text("전화 어댑터 접속 토큰")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            if(pending==null){
                TextButton(enabled=!busy&&runCatching{config();true}.getOrDefault(false),onClick={checkHealth()}){Text("연결 확인")}
                health?.let{Text("어댑터 연결됨 · 승인된 발신 번호 "+it.approved_numbers+"개")}
            }
            OutlinedTextField(number,{number=it.take(14)},enabled=pending==null&&!busy,label={Text("승인된 업체 번호 (+82...)")},singleLine=true)
            OutlinedTextField(goal,{goal=it.take(800)},enabled=pending==null&&!busy,label={Text("전화 문의 목적")},maxLines=5)
            pending?.let{call->
                Text("업무 ID: "+call.id,style=MaterialTheme.typography.labelSmall)
                Text("통화 상태: "+when(receipt?.status){
                    "queued"->"실행 대기";"dialing"->"전화 연결 중";"connected"->"상대 연결";"cancel_requested"->"종료 요청 중"
                    "reported"->"음성 답변 기록 · 사용자 확인 필요";"ended"->"통화 종료 · 답변 미확인"
                    "cancelled"->"취소";"failed"->"실패";"expired"->"실행 만료";"unknown"->"결과 불명 · 재발신 금지"
                    else->if(missing)"서버에 해당 ID 없음" else "상태 확인 중"
                })
                receipt?.summary?.let{Text("업무 결과 후보: "+it)}
                receipt?.evidence?.let{Text("실제 음성 인식 인용: "+it)}
                if(receipt?.answered==1)Text("전화 연결 확인 · 통화 상대 및 합의는 직접 확인")
                TextButton(enabled=!busy,onClick={action("status",call)}){Text("상태 다시 조회")}
                if(receipt?.terminal!=true&&!missing)TextButton(enabled=!busy,onClick={action("cancel",call)}){Text("통화 중단 요청")}
                if(receipt?.terminal==true||missing)TextButton(enabled=!busy,onClick={
                    runCatching{validAccount();bridge.save(PhoneBridgeState(config(),null))}
                        .onSuccess{pending=null;receipt=null;missing=false}
                        .onFailure{error="기록 저장 실패"}
                }){Text("확인 후 새 업무 준비")}
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={if(pending==null)TextButton(enabled=!busy&&runCatching{config();true}.getOrDefault(false)&&
            number.matches(Regex("\\+82[1-9][0-9]{7,10}"))&&goal.trim().length>=3,onClick={confirm=true}){Text("발신 승인 검토")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("닫기")}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("실제 전화 발신 승인")},
        text={Text(number+"\n"+goal+"\n비용이 발생할 수 있습니다. 지정된 업체에 이 문의로 한 번만 전화합니다.")},
        confirmButton={TextButton(onClick={confirm=false;action("start",PhoneBridgeCall(java.util.UUID.randomUUID().toString(),number,goal.trim()))}){Text("실제 발신 승인")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("취소")}})
}
