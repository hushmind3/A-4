package com.dominion.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** D1-backed consumer messenger. Works without a PC or a local server. */
@Composable
fun CloudMessengerDialog(onDismiss: () -> Unit) {
    val context=LocalContext.current
    val repo=remember(context) {
        (context.applicationContext as DominionApplication).container.cloudMessenger
    }
    val p=LocalDominionPalette.current
    val scope=rememberCoroutineScope()
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var profile by remember{mutableStateOf<CloudMessengerProfile?>(null)}
    var friends by remember{mutableStateOf(CloudMessengerFriends())}
    var threads by remember{mutableStateOf<List<CloudMessengerThread>>(emptyList())}
    var selected by remember{mutableStateOf<CloudMessengerThread?>(null)}
    var messages by remember{mutableStateOf<List<CloudMessengerMessage>>(emptyList())}
    var nextBefore by remember{mutableStateOf<String?>(null)}
    var draft by remember{mutableStateOf("")}
    var sending by remember{mutableStateOf(false)}
    var handle by remember{mutableStateOf("")}
    var displayName by remember{mutableStateOf("")}
    var query by remember{mutableStateOf("")}
    var searchResults by remember{mutableStateOf<List<CloudMessengerProfile>>(emptyList())}
    var showGroup by remember{mutableStateOf(false)}
    var groupTitle by remember{mutableStateOf("")}
    var groupMembers by remember{mutableStateOf<Set<String>>(emptySet())}
    var busy by remember{mutableStateOf(false)}

    suspend fun reload() {
        loading=true
        try{
            val current=repo.profile()
            profile=current
            if(current!=null){
                friends=repo.friends()
                threads=repo.threads()
            }
            error=""
        }catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?.take(200)?:"DOMINION Cloud 연결을 확인해 주세요."}
        finally{loading=false}
    }
    fun run(block:suspend()->Unit){
        if(busy)return
        busy=true
        scope.launch{
            try{block();error=""}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?.take(200)?:"요청을 확인하지 못했습니다."}
            finally{busy=false}
        }
    }
    fun openThread(thread:CloudMessengerThread){
        selected=thread
        messages=emptyList();nextBefore=null
        scope.launch{
            try{
                val page=repo.messages(thread.id)
                messages=page.messages;nextBefore=page.nextBefore
                repo.read(thread.id)
                threads=threads.map{if(it.id==thread.id)it.copy(unread=0)else it}
                error=""
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?.take(200)?:"메시지를 불러오지 못했습니다."}
        }
    }
    LaunchedEffect(Unit){reload()}
    LaunchedEffect(selected?.id){
        val thread=selected?:return@LaunchedEffect
        val cursor=messages.lastOrNull()?.createdAt?:0L
        repo.events(cursor).collect{message->
            if(message.threadId==thread.id&&messages.none{it.id==message.id})messages=messages+message
            threads=threads.map{if(it.id==message.threadId)it.copy(lastText=message.text,
                lastSender=message.senderId,lastAt=message.createdAt,
                unread=if(it.id==thread.id)0 else it.unread+1)else it}
        }
    }
    // Refresh list while the dialog stays open; SSE carries the message edges.
    LaunchedEffect(profile?.accountId){
        if(profile==null)return@LaunchedEffect
        while(true){delay(8_000);runCatching{friends=repo.friends();threads=repo.threads()}}
    }

    BackHandler(enabled=selected!=null){selected=null;messages=emptyList()}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().background(p.screen),color=p.screen) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if(selected!=null){
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        TextButton(onClick={selected=null;messages=emptyList()}){Text("‹ 대화")}
                    }
                }else{
                    Box(Modifier.fillMaxWidth()){
                        ScreenTopBar(title="DOMINION 메신저",onBack=onDismiss)
                        if(profile!=null)TextButton(onClick={showGroup=true},
                            modifier=Modifier.align(androidx.compose.ui.Alignment.CenterEnd)){
                            Text("새 그룹")
                        }
                    }
                }
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,
                    modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp))
                if(profile==null&&!loading){
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("Cloud 메신저 프로필",style=MaterialTheme.typography.titleLarge)
                        Text("친구가 찾을 수 있는 아이디와 표시 이름을 정합니다. 계정 ID나 전화번호는 공개되지 않습니다.")
                        AppTextField(handle,{handle=it.lowercase().take(24)},label="아이디",placeholder="예: hong_gildong")
                        AppTextField(displayName,{displayName=it.take(40)},label="표시 이름",placeholder="예: 홍길동")
                        Button(enabled=!busy,onClick={
                            run{
                                profile=repo.saveProfile(handle,displayName)
                                reload()
                            }
                        }){Text("프로필 만들기")}
                    }
                }else if(profile!=null&&selected==null){
                    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
                        Text(profile!!.displayName+" · @"+profile!!.handle,
                            style=MaterialTheme.typography.titleMedium)
                        if(friends.incoming.isNotEmpty()){
                            Text("받은 친구 요청",style=MaterialTheme.typography.titleSmall)
                            friends.incoming.forEach{peer->
                                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                    Text("${peer.displayName} · @${peer.handle}",Modifier.weight(1f))
                                    TextButton(enabled=!busy,onClick={run{
                                        repo.respondFriend(peer.accountId,true);reload()
                                    }}){Text("수락")}
                                    TextButton(enabled=!busy,onClick={run{
                                        repo.respondFriend(peer.accountId,false);reload()
                                    }}){Text("거절")}
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            AppTextField(query,{query=it.take(40)},label="친구 찾기",modifier=Modifier.weight(1f))
                            TextButton(enabled=!busy&&query.isNotBlank(),onClick={run{
                                searchResults=repo.search(query.trim())
                            }}){Text("검색")}
                        }
                        searchResults.forEach{peer->
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                Text("${peer.displayName} · @${peer.handle}",Modifier.weight(1f))
                                TextButton(enabled=!busy,onClick={run{
                                    repo.requestFriend(peer.accountId);searchResults=emptyList();reload()
                                }}){Text("친구 요청")}
                            }
                        }
                        Text("대화",style=MaterialTheme.typography.titleSmall,
                            modifier=Modifier.padding(top=10.dp))
                        if(threads.isEmpty())Text("아직 대화가 없습니다. 친구를 추가하고 1:1 대화를 시작하세요.",
                            style=MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.weight(1f)){
                            items(threads,key={it.id}){thread->
                                Card(onClick={openThread(thread)},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){
                                    Row(Modifier.padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){
                                            Text(thread.title,style=MaterialTheme.typography.titleSmall,
                                                maxLines=1,overflow=TextOverflow.Ellipsis)
                                            Text(thread.lastText?:"새 대화",
                                                style=MaterialTheme.typography.bodySmall,maxLines=1,
                                                overflow=TextOverflow.Ellipsis)
                                        }
                                        if(thread.unread>0)Badge{Text(thread.unread.toString())}
                                    }
                                }
                            }
                        }
                        if(friends.accepted.isNotEmpty()){
                            Text("친구와 1:1 대화",style=MaterialTheme.typography.labelMedium)
                            LazyColumn(Modifier.heightIn(max=150.dp)){
                                items(friends.accepted,key={it.accountId}){peer->
                                    TextButton(enabled=!busy,onClick={run{
                                        val thread=repo.createThread("direct","",listOf(peer.accountId))
                                        reload();openThread(thread)
                                    }}){Text(peer.displayName+" · @"+peer.handle)}
                                }
                            }
                        }
                    }
                }else if(selected!=null){
                    val thread=selected!!
                    Text(thread.title,style=MaterialTheme.typography.titleMedium,
                        modifier=Modifier.padding(horizontal=16.dp))
                    Text(if(thread.kind=="group")"그룹 · ${thread.members.size}명" else "1:1 대화",
                        style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=16.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),
                        reverseLayout=false){
                        items(messages,key={it.id}){message->
                            Column(Modifier.fillMaxWidth().padding(vertical=3.dp),
                                horizontalAlignment=if(message.senderId==profile?.accountId)
                                    androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start){
                                if(thread.kind=="group")Text(message.senderName?:"",
                                    style=MaterialTheme.typography.labelSmall)
                                Surface(color=if(message.senderId==profile?.accountId)
                                    MaterialTheme.colorScheme.primaryContainer else p.card,
                                    shape=MaterialTheme.shapes.medium){
                                    Text(message.text,Modifier.padding(10.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment=androidx.compose.ui.Alignment.Bottom){
                        AppTextField(draft,{draft=it.take(4000)},label="메시지",
                            modifier=Modifier.weight(1f),singleLine=false,maxLines=4)
                        TextButton(enabled=!sending&&draft.isNotBlank(),onClick={
                            val text=draft.trim();if(text.isBlank())return@TextButton
                            sending=true
                            scope.launch{
                                try{
                                    val message=repo.send(thread.id,text,UUID.randomUUID().toString())
                                    if(messages.none{it.id==message.id})messages=messages+message
                                    draft="";error=""
                                }catch(e:CancellationException){throw e}
                                catch(e:Exception){error=e.message?.take(200)?:"메시지를 보내지 못했습니다."}
                                finally{sending=false}
                            }
                        }){Text(if(sending)"전송 중" else "보내기")}
                    }
                }
            }
        }
    }
    if(showGroup){
        AlertDialog(onDismissRequest={if(!busy)showGroup=false},title={Text("새 그룹")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){
                AppTextField(groupTitle,{groupTitle=it.take(80)},label="그룹 이름")
                Text("초대할 친구",style=MaterialTheme.typography.labelMedium)
                friends.accepted.forEach{peer->
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Checkbox(peer.accountId in groupMembers,{checked->
                            groupMembers=if(checked)groupMembers+peer.accountId else groupMembers-peer.accountId
                        })
                        Text(peer.displayName+" · @"+peer.handle)
                    }
                }
            }},
            confirmButton={TextButton(enabled=!busy&&groupTitle.isNotBlank()&&groupMembers.isNotEmpty(),onClick={
                run{
                    val thread=repo.createThread("group",groupTitle.trim(),groupMembers.toList())
                    showGroup=false;groupTitle="";groupMembers=emptySet();reload();openThread(thread)
                }
            }){Text("그룹 만들기")}},
            dismissButton={TextButton(enabled=!busy,onClick={showGroup=false}){Text("취소")}})
    }
}
