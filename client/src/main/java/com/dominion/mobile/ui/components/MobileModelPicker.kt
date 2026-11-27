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
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException

@Composable
fun MobileModelPicker(vm:AppViewModel,onDismiss:()->Unit){
    val context=LocalContext.current
    val state by vm.state.collectAsState()
    var models by remember{mutableStateOf<List<CloudModel>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf(state.settings.mobileModel?:"dominion/free-auto")}
    LaunchedEffect(Unit){
        try{
            val cloud=(context.applicationContext as DominionApplication).container.cloudAccount.refresh()
            check(cloud.status=="approved")
            models=cloud.models.filter{it.free}
            if(models.none{it.id==selected})selected=models.firstOrNull()?.id.orEmpty()
        }catch(e:CancellationException){throw e}catch(_:Exception){error="DOMINION Cloud에서 Google 계정을 연결해 주세요."}
        finally{loading=false}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("모바일 실행 모델")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
            Text("모바일 비서는 DOMINION Cloud 계정의 AI 모델을 사용합니다. PC 설치가 필요하지 않습니다.")
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            models.forEach{m->Row{RadioButton(selected==m.id,{selected=m.id});Text(m.name,Modifier.padding(top=12.dp))}}
        }
    },confirmButton={TextButton(enabled=!loading&&models.any{it.id==selected},onClick={vm.selectMobileModel(selected);onDismiss()}){Text("적용")}},
        dismissButton={TextButton(onClick=onDismiss){Text("닫기")}})
}
