package com.dominion.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.CloudAccountSnapshot
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Consumer account and inference preference only. No PC or tool administration. */
@Composable
fun CloudAccountScreen(vm:AppViewModel,onBack:()->Unit) {
    val context=LocalContext.current
    val repo=remember(context.applicationContext){
        (context.applicationContext as DominionApplication).container.cloudAccount
    }
    val lang=LocalLang.current
    val p=LocalDominionPalette.current
    val lifecycle=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val appState by vm.state.collectAsState()
    var snapshot by remember{mutableStateOf<CloudAccountSnapshot?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var showModel by remember{mutableStateOf(false)}
    fun open(url:String){
        runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
            .onFailure{error=true}
    }
    fun showAccount(next:CloudAccountSnapshot){
        snapshot=next
        val container=(context.applicationContext as DominionApplication).container
        if(next.status=="approved")
            com.dominion.mobile.platform.background.FirebasePush.ensureEventually(context,repo)
        if(container.switchCloudAccount(next.accountId)){
            // Clear the previous account's ViewModel and Compose tree on a scope change.
            var candidate:android.content.Context=context
            while(candidate is android.content.ContextWrapper){
                if(candidate is com.dominion.mobile.MainActivity){candidate.recreateForCloudAccount();break}
                candidate=candidate.baseContext
            }
        }
    }
    suspend fun refresh(){
        if(busy)return
        busy=true
        try{showAccount(repo.refresh());error=false}
        catch(e:CancellationException){throw e}
        catch(_:Exception){error=true}
        finally{busy=false}
    }
    fun action(block:suspend()->CloudAccountSnapshot,approve:Boolean=false){
        if(busy)return
        busy=true
        scope.launch{
            try{
                val result=block();showAccount(result);error=false
                if(approve)result.approvalUrl?.let(::open)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error=true}
            finally{busy=false}
        }
    }
    LaunchedEffect(lifecycle,repo){
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            refresh()
        }
    }
    LaunchedEffect(snapshot?.status){
        if(snapshot?.status=="pending")while(snapshot?.status=="pending"){
            delay(3000);refresh()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)){
        ScreenTopBar(title=tr(lang,"계정 및 AI 모델","Account and AI model"),onBack=onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("DOMINION Cloud",style=MaterialTheme.typography.headlineSmall)
            Text(tr(lang,"Google 계정을 연결하면 대화와 비서 업무에 AI를 사용할 수 있어요.",
                "Connect Google to use AI in chats and assistant tasks."))
            if(error)Text(tr(lang,"연결 상태를 확인하지 못했습니다. 다시 시도해 주세요.","Could not verify the connection. Try again."),
                color=MaterialTheme.colorScheme.error)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            val current=snapshot
            when(current?.status){
                null->Text(tr(lang,"계정 확인 중","Checking account"))
                "pending"->{
                    Text(tr(lang,"Google 로그인 승인 대기 중","Waiting for Google approval"))
                    Button(enabled=!busy,onClick={current.approvalUrl?.let(::open)}){
                        Text(tr(lang,"Google 로그인 계속","Continue Google sign-in"))
                    }
                    OutlinedButton(enabled=!busy,onClick={action(repo::disconnect)}){
                        Text(tr(lang,"취소","Cancel"))
                    }
                }
                "approved"->{
                    Text(tr(lang,"Google 계정 연결됨","Google account connected"),style=MaterialTheme.typography.titleMedium)
                    current.billing?.let{billing->
                        Text(tr(lang,"이용 플랜: ","Plan: ")+billing.plan)
                        billing.balanceKrw()?.let{Text(tr(lang,"잔액: ","Balance: ")+it)}
                        Text(tr(lang,"오늘 남은 무료 요청: ","Free requests remaining today: ")+
                            billing.free_daily_remaining+" / "+billing.free_daily_limit)
                        billing.free_monthly_remaining?.let{
                            Text(tr(lang,"이번 달 남은 요청: ","Monthly requests remaining: ")+it+" / "+billing.free_monthly_limit)
                        }
                    }
                    HorizontalDivider()
                    val model=appState.settings.mobileModel?:"dominion/free-auto"
                    Text(tr(lang,"AI 모델","AI model"),style=MaterialTheme.typography.titleMedium)
                    Text(current.models.firstOrNull{it.id==model}?.name?.takeIf{it.isNotBlank()}
                        ?: if(model=="dominion/free-auto")tr(lang,"자동 선택","Automatic")else model)
                    OutlinedButton(enabled=!busy,onClick={showModel=true}){
                        Text(tr(lang,"모델 변경","Change model"))
                    }
                    Text(tr(lang,"대화방에서 요청하면 비서가 필요한 기능을 알아서 사용합니다.",
                        "Ask in your chat. Your assistant selects the appropriate actions."),
                        style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    OutlinedButton(enabled=!busy,onClick={action(repo::disconnect)}){
                        Text(tr(lang,"로그아웃","Sign out"))
                    }
                }
                else->Button(enabled=!busy,onClick={action(repo::begin,true)}){
                    Text(tr(lang,"Google 계정 연결","Connect Google account"))
                }
            }
            TextButton(enabled=!busy,onClick={scope.launch{refresh()}}){Text(tr(lang,"새로고침","Refresh"))}
        }
    }
    if(showModel)com.dominion.mobile.ui.components.MobileModelPicker(vm,onDismiss={showModel=false})
}
