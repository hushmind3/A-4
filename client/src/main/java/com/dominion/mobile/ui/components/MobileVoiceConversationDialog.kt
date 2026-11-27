package com.dominion.mobile.ui.components

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.ui.AppViewModel
import java.util.Locale
import java.util.UUID

/** Mobile-only voice turns: Android STT -> normal DOMINION mobile job -> Android TTS.
 * No PC, engine Whisper/Piper or mobile realtime server is required. This is
 * explicit turn-taking, not a low-latency full-duplex voice/video call.
 */
@Composable
fun MobileVoiceConversationDialog(vm:AppViewModel,roomId:String,onDismiss:()->Unit){
    val context=LocalContext.current
    val state by vm.state.collectAsState()
    val room=state.conversation(roomId)
    val originalSession=remember(roomId){room?.sessionId}
    val language=if(state.settings.lang==Lang.KO)Locale.KOREAN else Locale.US
    var recognized by remember(roomId){mutableStateOf("")}
    var answer by remember(roomId){mutableStateOf("")}
    var phase by remember(roomId){mutableStateOf("마이크를 눌러 대화하세요")}
    var waiting by remember(roomId){mutableStateOf<String?>(null)}
    var listening by remember(roomId){mutableStateOf(false)}
    var ttsReady by remember{mutableStateOf(false)}
    var tts by remember{mutableStateOf<TextToSpeech?>(null)}
    var playing by remember{mutableStateOf<String?>(null)}
    val handler=remember{Handler(Looper.getMainLooper())}

    DisposableEffect(context){
        var disposed=false
        val engine=TextToSpeech(context.applicationContext){code->
            handler.post{if(!disposed)ttsReady=code==TextToSpeech.SUCCESS}
        }
        engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(id:String){handler.post{if(!disposed&&playing==id)phase="음성 답변 재생 중"}}
            override fun onDone(id:String){handler.post{if(!disposed&&playing==id){playing=null;phase="답변 완료 · 마이크를 눌러 계속"}}}
            override fun onError(id:String){handler.post{if(!disposed&&playing==id){playing=null;phase="기기 음성 출력 실패"}}}
        })
        tts=engine
        onDispose{
            disposed=true
            engine.stop()
            engine.shutdown()
        }
    }

    fun speak(text:String){
        val engine=tts
        if(!ttsReady||engine==null){phase="답변 완료 · 기기 음성 합성 사용 불가";return}
        val availability=engine.setLanguage(language)
        if(availability==TextToSpeech.LANG_MISSING_DATA||availability==TextToSpeech.LANG_NOT_SUPPORTED){
            phase="답변 완료 · 기기에서 해당 언어 음성을 지원하지 않습니다"
            return
        }
        val id="mobile-voice-"+UUID.randomUUID()
        playing=id
        val result=engine.speak(text.take(minOf(3000,TextToSpeech.getMaxSpeechInputLength())),
            TextToSpeech.QUEUE_FLUSH,null,id)
        if(result!=TextToSpeech.SUCCESS){playing=null;phase="기기 음성 합성 요청 실패"}
        else phase="음성 답변 준비 중"
    }

    val recognize=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        listening=false
        val phrase=if(result.resultCode==Activity.RESULT_OK)
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        else null
        if(phrase.isNullOrBlank()){
            phase="인식된 음성이 없습니다. 다시 말씀해 주세요"
        }else if(room?.sessionId!=originalSession || originalSession.isNullOrBlank()){
            phase="대화 세션이 변경되었습니다. 대화방을 다시 열어 주세요"
        }else{
            recognized=phrase
            answer=""
            val id=vm.sendMessage(roomId,phrase)
            if(id==null)phase="메시지 접수 실패 · 대화방에서 확인해 주세요"
            else{waiting=id;phase="도미니온 클라우드 작업 처리 중"}
        }
    }

    fun listen(){
        if(waiting!=null||listening||room?.sessionId!=originalSession||originalSession.isNullOrBlank())return
        tts?.stop()
        playing=null
        listening=true
        phase="기기 음성 인식 열기"
        runCatching{
            recognize.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,if(state.settings.lang==Lang.KO)"ko-KR" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT,"도미니온에게 말씀해 주세요")
            })
        }.onFailure{listening=false;phase="기기 음성 인식 서비스를 열 수 없습니다"}
    }

    LaunchedEffect(room?.messages,waiting){
        val id=waiting?:return@LaunchedEffect
        if(room?.sessionId!=originalSession){waiting=null;phase="대화 세션 변경으로 중지";return@LaunchedEffect}
        val owner=room.messages.firstOrNull{it.id==id}
        if(owner?.status==MessageStatus.FAILED || owner?.jobStatus in setOf("cancelled","failed","interrupted")){
            waiting=null;phase="작업 실패 · 대화방에서 확인해 주세요";return@LaunchedEffect
        }
        val reply=room.messages.firstOrNull{it.replyTo==id&&it.sender==MessageSender.ASSISTANT}
        if(reply!=null){
            waiting=null
            answer=reply.text
            if(owner?.jobStatus=="needs_review"){
                phase="기기 작업 승인 필요 · 대화방의 도구 실행에서 검토해 주세요"
            }else speak(reply.text)
        }
    }

    AlertDialog(
        onDismissRequest={tts?.stop();onDismiss()},
        title={Text("도미니온 · 기기 음성 대화")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(phase,style=MaterialTheme.typography.titleSmall)
                if(recognized.isNotBlank())Text("나: "+recognized)
                if(answer.isNotBlank())Text("도미니온: "+answer.take(2400))
                Text("기기 음성 인식과 음성 합성을 사용합니다. 모델과 작업 도구는 모바일 클라우드 연결을 사용합니다.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            TextButton(enabled=waiting==null&&!listening&&room?.sessionId==originalSession,
                onClick={listen()}){Text("🎙 말하기")}
        },
        dismissButton={
            Row{
                if(playing!=null)TextButton(onClick={tts?.stop();playing=null;phase="음성 중단"}){Text("읽기 중단")}
                TextButton(onClick={tts?.stop();onDismiss()}){Text("닫기")}
            }
        }
    )
}
