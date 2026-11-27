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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.CloudAccountSnapshot
import com.dominion.mobile.data.remote.CloudChatMessage
import com.dominion.mobile.data.remote.CloudModelProtocol
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CloudAccountScreen(onBack: () -> Unit, onEngineSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context.applicationContext) {
        (context.applicationContext as DominionApplication).container.cloudAccount
    }
    val lang = LocalLang.current
    val p = LocalDominionPalette.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var snapshot by remember { mutableStateOf<CloudAccountSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val container=(context.applicationContext as DominionApplication).container
    var selected by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<CloudChatMessage>>(emptyList()) }
    var resultInfo by remember { mutableStateOf("") }
    var registerReview by remember { mutableStateOf(false) }
    var registered by remember { mutableStateOf("") }
    LaunchedEffect(snapshot?.accountId,snapshot?.pairingId) {
        history=emptyList();resultInfo="";registered="";ask=""
    }
    LaunchedEffect(snapshot?.models) {
        if(snapshot?.models.orEmpty().none{it.id==selected})
            selected=snapshot?.models.orEmpty().firstOrNull()?.id.orEmpty()
    }

    suspend fun refresh() {
        if (busy) return
        busy = true
        try {
            snapshot = repo.refresh()
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { busy = false }
    }

    fun openApproval(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { error = true }
    }

    LaunchedEffect(lifecycle, repo) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(if (snapshot?.status == "pending") 3000L else 30_000L)
            }
        }
    }

    fun runAction(action: suspend () -> CloudAccountSnapshot, open: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = action()
                snapshot = result
                error = false
                if (open) result.approvalUrl?.let(::openApproval)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }

    if(registerReview) {
        val engine=container.engine
        AlertDialog(onDismissRequest={registerReview=false},title={Text("본체에 무료 모델 연결")},text={
            Text("선택 모델: "+selected+"\n본체: "+(engine?.origin?:"미설정")+
                "\n현재 Google 계정의 무료 모델 전용 앱 세션을 이 본체의 공급자 저장소에 등록합니다. "+
                "결제 권한은 없으며 연결 해제 시 본체에서도 이 세션을 사용할 수 없습니다.")
        },confirmButton={TextButton(enabled=engine!=null && !busy,onClick={
            registerReview=false
            if(engine!=null){
                busy=true;error=false
                val model=selected
                scope.launch{
                    try{registered=repo.registerEngineModel(model,engine){container.engine===engine}}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){error=true}
                    finally{busy=false}
                }
            }
        }){Text("등록 승인")}},dismissButton={TextButton(onClick={registerReview=false}){Text("취소")}})
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = "DOMINION Cloud", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr(lang, "공통 계정", "Shared account"), style = MaterialTheme.typography.headlineSmall)
            Text(tr(lang, "기존 DOMINION Cloud Google 계정을 연결합니다. 로컬 서버 사용에는 로그인이 필요하지 않습니다.",
                "Connect your existing DOMINION Cloud Google account. Local server use does not require cloud sign-in."))
            Text(tr(lang, "실행 서버: ", "Execution server: ") +
                ((context.applicationContext as DominionApplication).container.engine?.origin ?: tr(lang, "미설정", "Not configured")))
            Text(tr(lang, "클라우드 계정 연결은 실행 서버 접근 권한과 별개입니다.", "Cloud account authorization does not grant execution server access."))
            OutlinedButton(onClick = onEngineSettings) { Text(tr(lang, "실행 서버 연결 및 진단", "Connect and diagnose engine")) }
            if (error) Text(tr(lang, "클라우드 연결 또는 확인에 실패했습니다. 다시 확인하세요.",
                "Cloud request or verification failed. Retry."), color = MaterialTheme.colorScheme.error)
            val current = snapshot
            if (current == null) {
                Text(tr(lang, "계정 상태 확인 중", "Checking account"))
            } else when (current.status) {
                "pending" -> {
                    Text(tr(lang, "Google 승인 대기 중", "Waiting for Google approval"))
                    Text(tr(lang, "브라우저에서 승인하거나 PC의 외부 앱 연결 화면에 코드를 입력하세요.",
                        "Approve in the browser or use the external app pairing screen on your PC."))
                    current.pairingId?.let { code ->
                        Text(code, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Text(tr(lang, "연결 코드 복사", "Copy pairing code"))
                        }
                    }
                    Button(onClick = { current.approvalUrl?.let(::openApproval) }, enabled = !busy) {
                        Text(tr(lang, "Google 승인 페이지 열기", "Open Google approval"))
                    }
                    OutlinedButton(onClick = { runAction(repo::disconnect) }, enabled = !busy) {
                        Text(tr(lang, "연결 취소", "Cancel pairing"))
                    }
                }
                "approved" -> {
                    Text(if (error) tr(lang, "마지막 확인: 연결됨", "Last verified: connected")
                        else tr(lang, "클라우드 계정 연결됨", "Cloud account connected"))
                    current.accountId?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    current.billing?.let {
                        Text(tr(lang, "플랜: ", "Plan: ") + it.plan)
                        it.balanceKrw()?.let { balance -> Text(tr(lang, "실제 잔액: ", "Verified balance: ") + balance) }
                        it.free_monthly_remaining?.let { remaining ->
                            Text(tr(lang, "월 잔여 요청: ", "Monthly remaining: ") + remaining + "/" + it.free_monthly_limit)
                        }
                        Text(tr(lang, "오늘 무료 모델 잔여 요청: ", "Free requests remaining today: ") +
                            "${it.free_daily_remaining} / ${it.free_daily_limit}")
                    }
                    Text(tr(lang,"Cloudflare 무료 모델","Cloudflare free models"),style=MaterialTheme.typography.titleMedium)
                    current.models.forEach { model ->
                        Row {
                            RadioButton(selected==model.id,onClick={selected=model.id;history=emptyList();resultInfo="";registered=""},enabled=!busy)
                            Text(model.name.ifBlank{model.id},Modifier.padding(top=12.dp))
                        }
                    }
                    if(selected.isNotBlank()) {
                        Text("직접 무료 모델 대화 · 본체 없이도 사용 가능",style=MaterialTheme.typography.titleSmall)
                        Text("직접 대화는 모델 추론만 사용합니다. 도구·작업·기억 실행은 아래 본체 등록을 이용하세요.",
                            style=MaterialTheme.typography.bodySmall)
                        history.forEach{m->Text((if(m.role=="user")"나: " else "AI: ")+m.content)}
                        OutlinedTextField(ask,{ask=it.take(4000)},label={Text("클라우드 모델에 요청")},enabled=!busy,maxLines=4,
                            modifier=Modifier.fillMaxWidth())
                        Row {
                            Button(enabled=!busy && ask.isNotBlank() && (current.billing?.free_daily_remaining?:0)>0,onClick={
                                val next=CloudModelProtocol.append(history,ask.trim())
                                val model=selected
                                busy=true;error=false;resultInfo=""
                                scope.launch {
                                    try {
                                        val answer=repo.complete(model,next)
                                        history=CloudModelProtocol.append(next,answer.content,"assistant")
                                        ask="";resultInfo="실제 응답 · "+answer.model
                                        snapshot=repo.refresh()
                                    }catch(e:CancellationException){throw e}
                                    catch(e:retrofit2.HttpException){resultInfo=if(e.code()==429)"무료 이용 한도 또는 공급자 제한" else "클라우드 HTTP "+e.code()}
                                    catch(_:Exception){resultInfo="클라우드 모델 응답 실패"}
                                    finally{busy=false}
                                }
                            }){Text("무료 모델 전송")}
                            TextButton(enabled=!busy,onClick={history=emptyList();resultInfo=""}){Text("새 대화")}
                        }
                        Text(resultInfo)
                        OutlinedButton(enabled=!busy && container.engine!=null,onClick={registerReview=true}){
                            Text("선택 모델을 DOMINION 본체에 등록")
                        }
                        if(container.engine==null)Text("본체가 연결되면 해당 모델을 기존 하네스에 등록할 수 있습니다.")
                        if(registered.isNotBlank())Text("본체 모델 등록 완료. 본체의 모델 선택에서 사용하세요. 등록 ID: "+registered)
                    }
                    OutlinedButton(onClick = { runAction(repo::disconnect) }, enabled = !busy) {
                        Text(tr(lang, "클라우드 연결 해제", "Disconnect cloud account"))
                    }
                }
                else -> {
                    if (current.status == "expired" || current.status == "revoked") {
                        Text(tr(lang, "이전 연결이 만료되었거나 해제되었습니다.", "Previous connection expired or was revoked."))
                    }
                    Button(onClick = { runAction(repo::begin, open = true) }, enabled = !busy) {
                        Text(tr(lang, "Google 계정 연결", "Connect Google account"))
                    }
                }
            }
            OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !busy) {
                Text(tr(lang, "상태 새로고침", "Refresh status"))
            }
        }
    }
}
