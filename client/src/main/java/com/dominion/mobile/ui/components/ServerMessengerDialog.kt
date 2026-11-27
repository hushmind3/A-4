package com.dominion.mobile.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominion.mobile.data.remote.*
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.*

@Composable
fun ServerMessengerDialog(vm:AppViewModel,onDismiss:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var directory by remember{mutableStateOf<MessengerDirectory?>(null)}
    var target by remember{mutableStateOf<String?>(null)}
    var page by remember{mutableStateOf<MessengerPage?>(null)}
    var query by remember{mutableStateOf("")}
    var draft by remember{mutableStateOf("")}
    var uploads by remember{mutableStateOf<List<MessengerAttachment>>(emptyList())}
    var journal by remember{mutableStateOf<List<MobileJournalEntry>>(emptyList())}
    var friends by remember{mutableStateOf<List<MobilePeer>>(emptyList())}
    var selected by remember{mutableStateOf(setOf<String>())}
    var title by remember{mutableStateOf("")}
    var creating by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var fileToSave by remember{mutableStateOf<Pair<MessengerAttachment,ByteArray>?>(null)}
    suspend fun reload() {
        val current=vm.messengerThreads()
        if(directory?.accountId!=null && directory?.accountId!=current.accountId) {
            target=null;page=null;uploads=emptyList();draft="";selected=emptySet()
        }
        directory=current
        journal=vm.mobileJournal().filter{it.intent.accountId==current.accountId && it.intent.action.startsWith("messenger.")}
        val id=target
        if(id!=null && current.threads.orEmpty().any{it.id==id}) {
            val rows=vm.messengerMessages(id,query.takeIf{it.isNotBlank()})
            check(rows.accountId==current.accountId)
            page=rows
        }else{target=null;page=null;uploads=emptyList()}
    }
    fun run(block:suspend ()->Unit) {
        if(busy)return
        busy=true;error=""
        scope.launch {
            try{block()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error="실제 본체 메신저 연결·요청 상태를 확인하세요.";try{reload()}catch(e:CancellationException){throw e}catch(_:Exception){}}
            finally{busy=false}
        }
    }
    LaunchedEffect(vm){run{reload()}}
    LaunchedEffect(target,busy,query) {
        val id=target ?: return@LaunchedEffect
        if(busy || query.isNotBlank())return@LaunchedEffect
        while(isActive) {
            delay(5000)
            try {
                val rows=vm.messengerMessages(id)
                check(rows.accountId==directory?.accountId)
                page=rows
            }catch(e:CancellationException){throw e}catch(_:Exception){error="메시지 동기화 중단";break}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri ->
        val thread=target
        if(uri!=null && thread!=null)run {
            val file=withContext(Dispatchers.IO) {
                require(uri.scheme=="content")
                val resolver=context.contentResolver
                val name=resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
                    if(it.moveToFirst())it.getString(0)else null
                }?:"attachment"
                val mime=resolver.getType(uri)?:"application/octet-stream"
                val bytes=requireNotNull(resolver.openInputStream(uri)).use{input->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024);out.write(buffer,0,n)}
                    out.toByteArray()
                }
                Triple(name,mime,bytes)
            }
            val result=vm.messengerUpload(thread,file.first,file.second,file.third)
            check(target==thread && result.accountId==directory?.accountId)
            uploads=(uploads+result).distinctBy{it.id}.take(5)
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri:Uri?->
        val payload=fileToSave;fileToSave=null
        if(uri!=null && payload!=null)run{
            withContext(Dispatchers.IO){
                requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use{it.write(payload.second)}
            }
            error="첨부 사본 저장 완료"
        }
    }
    Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
            Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Row{
                    Text("서버 메신저",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    TextButton(enabled=!busy,onClick=onDismiss){Text("닫기")}
                }
                if(directory==null)Text("본체의 messenger_v2 지원 필요 · 가짜 메시지를 생성하지 않습니다.")
                TextButton(enabled=!busy,onClick={run{reload()}}){Text("목록·접수 갱신")}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                if(target==null){
                    TextButton(enabled=!busy && directory!=null,onClick={run{
                        friends=vm.mobilePeers().filter{it.relationship=="friend"}
                        creating=true
                    }}){Text("그룹 만들기")}
                    LazyColumn(Modifier.weight(1f)){
                        items(directory?.threads.orEmpty(),key={it.id.orEmpty()}){thread->
                            ListItem(headlineContent={Text(thread.title.orEmpty())},
                                supportingContent={Text(thread.kind.orEmpty()+" · "+thread.members.orEmpty().size+"명")},
                                trailingContent={TextButton(enabled=!busy,onClick={target=thread.id;query="";run{reload()}}){Text("열기")}})
                        }
                    }
                }else{
                    Row{
                        TextButton(enabled=!busy,onClick={target=null;page=null;draft="";uploads=emptyList();query=""}){Text("목록")}
                        Text(directory?.threads.orEmpty().firstOrNull{it.id==target}?.title.orEmpty())
                    }
                    OutlinedTextField(query,{query=it.take(200)},label={Text("서버 메시지 검색")},singleLine=true,enabled=!busy)
                    Row{
                        TextButton(enabled=!busy,onClick={run{reload()}}){Text("검색")}
                        TextButton(enabled=!busy && page?.nextBefore!=null,onClick={run{
                            val id=requireNotNull(target)
                            val older=vm.messengerMessages(id,query.takeIf{it.isNotBlank()},page?.nextBefore)
                            check(older.accountId==directory?.accountId)
                            page=older.copy(messages=(older.messages.orEmpty()+page?.messages.orEmpty()).distinctBy{it.id})
                        }}){Text("이전 메시지")}
                    }
                    LazyColumn(Modifier.weight(1f)){
                        items(page?.messages.orEmpty().sortedBy{it.createdAt},key={it.id.orEmpty()}){message->
                            Text((if(message.senderId==directory?.accountId)"나" else message.senderId.orEmpty())+" · "+message.text.orEmpty())
                            Text("읽음 "+message.readBy.orEmpty().size+"명",style=MaterialTheme.typography.labelSmall)
                            message.attachments.orEmpty().forEach{file->
                                TextButton(enabled=!busy,onClick={run{
                                    val id=requireNotNull(target)
                                    val bytes=vm.messengerDownload(id,file)
                                    fileToSave=file to bytes
                                    save.launch(file.name.orEmpty().ifBlank{"attachment"})
                                }}){Text("첨부 저장 · "+file.name.orEmpty())}
                            }
                            if(directory?.accountId !in message.readBy.orEmpty())TextButton(enabled=!busy,onClick={run{
                                vm.messengerOperation(MobileAction.THREAD_READ,requireNotNull(target),MessengerPayload(messageId=message.id));reload()
                            }}){Text("읽음 확인")}
                            HorizontalDivider()
                        }
                    }
                    uploads.forEach{file->Row{
                        Text(file.name.orEmpty(),Modifier.weight(1f))
                        TextButton(enabled=!busy,onClick={uploads=uploads.filterNot{it.id==file.id}}){Text("제거")}
                    }}
                    OutlinedTextField(draft,{draft=it.take(8000)},label={Text("메시지")},enabled=!busy,maxLines=4)
                    Row{
                        TextButton(enabled=!busy && uploads.size<5,onClick={picker.launch(arrayOf("*/*"))}){Text("첨부")}
                        TextButton(enabled=!busy && (draft.isNotBlank()||uploads.isNotEmpty()) &&
                            journal.none{it.intent.target==target && it.intent.action==MobileAction.THREAD_SEND.wire && it.receipt?.terminal()!=true},
                            onClick={run{
                                vm.messengerOperation(MobileAction.THREAD_SEND,requireNotNull(target),
                                    MessengerPayload(text=draft.trim().takeIf{it.isNotBlank()},attachments=uploads.mapNotNull{it.id}))
                                draft="";uploads=emptyList();reload()
                            }}){Text("전송")}
                    }
                }
                journal.filter{it.receipt?.terminal()!=true}.take(3).forEach{row->
                    TextButton(enabled=!busy,onClick={run{vm.refreshMobileOperation(row.intent.nonce);reload()}}){
                        Text("접수 재조회 · "+row.intent.action+" · "+(row.receipt?.status?:"미확인"))
                    }
                }
            }
        }
    }
    if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("그룹 만들기")},text={
        Column(Modifier.heightIn(max=450.dp)){
            OutlinedTextField(title,{title=it.take(120)},label={Text("그룹명")})
            LazyColumn(Modifier.heightIn(max=300.dp)){
                items(friends,key={it.id.orEmpty()}){friend->
                    val id=requireNotNull(friend.id)
                    Row{
                        Checkbox(id in selected,{checked->selected=if(checked && selected.size<30)selected+id else selected-id})
                        Text(friend.displayName.orEmpty(),Modifier.padding(top=12.dp))
                    }
                }
            }
        }
    },confirmButton={TextButton(enabled=!busy && title.isNotBlank() && selected.size>=2,onClick={
        creating=false;run{vm.messengerOperation(MobileAction.GROUP_CREATE,"group",MessengerPayload(title=title.trim(),members=selected.toList()));selected=emptySet();title="";reload()}
    }){Text("그룹 생성 요청")}},dismissButton={TextButton(onClick={creating=false}){Text("취소")}})
}
