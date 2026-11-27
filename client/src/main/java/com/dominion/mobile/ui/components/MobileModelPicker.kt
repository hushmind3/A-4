package com.dominion.mobile.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.CloudModel
import com.dominion.mobile.data.remote.CustomModelConfig
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException

@Composable
fun MobileModelPicker(vm:AppViewModel,onDismiss:()->Unit){
    val context=LocalContext.current
    val state by vm.state.collectAsState()
    val cloud=remember(context){(context.applicationContext as DominionApplication).container.cloudAccount}
    val scope=rememberCoroutineScope()
    var custom by remember{mutableStateOf<CustomModelConfig?>(null)}
    var configure by remember{mutableStateOf(false)}
    var url by remember{mutableStateOf("")}
    var modelId by remember{mutableStateOf("")}
    var key by remember{mutableStateOf("")}
    var testing by remember{mutableStateOf(false)}
    var customError by remember{mutableStateOf("")}
    var models by remember{mutableStateOf<List<CloudModel>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf(state.settings.mobileModel?:"dominion/free-auto")}
    LaunchedEffect(Unit){
        try{
            custom=cloud.customModelConfig()
            val snapshot=cloud.refresh()
            check(snapshot.status=="approved")
            models=snapshot.models.filter{it.free}
            if(models.none{it.id==selected}&&!(selected==CustomModelConfig.SELECTION&&custom!=null))selected=models.firstOrNull()?.id.orEmpty()
        }catch(e:CancellationException){throw e}catch(_:Exception){error="DOMINION Cloud에서 Google 계정을 연결해 주세요."}
        finally{loading=false}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("모바일 실행 모델")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
            Text("DOMINION Cloud 또는 직접 지정한 OpenAI 호환 HTTPS 모델을 사용할 수 있습니다. 사용자 지정 모델은 해당 서버에 대화와 도구 관찰 데이터를 전송합니다.")
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            models.forEach{m->Row{RadioButton(selected==m.id,{selected=m.id});Text(m.name,Modifier.padding(top=12.dp))}}
            custom?.let{cfg->Row{RadioButton(selected==CustomModelConfig.SELECTION,{selected=CustomModelConfig.SELECTION});Text("직접 연결 · "+cfg.model,Modifier.padding(top=12.dp))}}
            TextButton(onClick={url=custom?.baseUrl.orEmpty();modelId=custom?.model.orEmpty();key=custom?.apiKey.orEmpty();customError="";configure=true}){Text("사용자 지정 API 연결")}

        }
    },confirmButton={TextButton(enabled=!loading&&(models.any{it.id==selected}||selected==CustomModelConfig.SELECTION&&custom!=null),onClick={vm.selectMobileModel(selected);onDismiss()}){Text("적용")}},
        dismissButton={TextButton(onClick=onDismiss){Text("닫기")}})
    if(configure)AlertDialog(onDismissRequest={if(!testing)configure=false},title={Text("OpenAI 호환 API 연결")},text={
        Column(Modifier.verticalScroll(rememberScrollState())){
            Text("예: https://your-server.example/v1. 신뢰하는 HTTPS 서버만 사용하세요. 연결 테스트는 OK 요청만 전송합니다.")
            OutlinedTextField(url,{url=it},label={Text("Base URL")},singleLine=true,enabled=!testing)
            OutlinedTextField(modelId,{modelId=it},label={Text("모델 ID")},singleLine=true,enabled=!testing)
            OutlinedTextField(key,{key=it},label={Text("API 키 (선택)")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!testing)
            if(testing)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(customError.isNotBlank())Text(customError,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(enabled=!testing&&url.isNotBlank()&&modelId.isNotBlank(),onClick={
        scope.launch{
            testing=true;customError=""
            try{
                val cfg=CustomModelConfig(url,modelId,key).checked()
                cloud.configureCustomModel(cfg)
                custom=cfg;selected=CustomModelConfig.SELECTION;configure=false
            }catch(e:CancellationException){throw e}catch(_:Exception){customError="연결 실패 · 주소, 모델 ID, 인증 및 서버 응답을 확인하세요."}
            finally{testing=false}
        }
    }){Text("연결 테스트 및 저장")}},dismissButton={TextButton(enabled=!testing,onClick={configure=false}){Text("취소")}})
}
