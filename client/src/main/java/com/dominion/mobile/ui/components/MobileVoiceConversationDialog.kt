package com.dominion.mobile.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.platform.audio.DeviceSpeech
import com.dominion.mobile.platform.audio.DeviceSpeechAvailability
import com.dominion.mobile.platform.audio.EngineVoiceAudio
import com.dominion.mobile.platform.audio.CloudVoicePlayback
import com.dominion.mobile.platform.audio.VoiceDelivery
import com.dominion.mobile.platform.audio.VoiceTtsMode
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** No on-device speech model: 16 kHz mic WAV -> Cloud STT -> existing
 * mobile message/harness/approval -> Cloud TTS MP3 -> hardware playback.
 * Recording is explicitly user-started and never persists on-device or cloud.
 * This is turn-based voice, not a full duplex WebSocket session. */
@Composable
fun MobileVoiceConversationDialog(vm:AppViewModel,roomId:String,onDismiss:()->Unit){
    val context=LocalContext.current
    val cloud=remember(context){(context.applicationContext as DominionApplication).container.cloudAccount}
    val state by vm.state.collectAsState()
    val room=state.conversation(roomId)
    val originalSession=remember(roomId){room?.sessionId}
    val language=remember(roomId){if(state.settings.lang==Lang.KO)"ko" else "en"}
    val scope=rememberCoroutineScope()
    val stopRecording=remember{AtomicBoolean(false)}
    var recording by remember(roomId){mutableStateOf(false)}
    var recognized by remember(roomId){mutableStateOf("")}
    var answer by remember(roomId){mutableStateOf("")}
    var phase by remember(roomId){mutableStateOf("마이크를 눌러 클라우드 음성 대화를 시작하세요")}
    var waiting by remember(roomId){mutableStateOf<String?>(null)}
    var recordingJob by remember{mutableStateOf<Job?>(null)}
    var speakingJob by remember{mutableStateOf<Job?>(null)}
    var checked by remember{mutableStateOf(false)}
    var continuous by remember(roomId){mutableStateOf(false)}
    var nextTurn by remember(roomId){mutableIntStateOf(0)}
    var originalAccount by remember(roomId){mutableStateOf(cloud.savedAccountId())}
    var deviceSpeech by remember{mutableStateOf<DeviceSpeechAvailability?>(null)}
    var bargeIn by remember(roomId){mutableStateOf(false)}
    val bargeAvailable=remember{EngineVoiceAudio.supportsBargeIn()}
    fun sessionValid()=vm.state.value.conversation(roomId)?.sessionId==originalSession &&
        !originalSession.isNullOrBlank() && originalAccount!=null && cloud.savedAccountId()==originalAccount
    var voiceCapabilities by remember{mutableStateOf<com.dominion.mobile.data.remote.CloudVoiceCapabilities?>(null)}
    val hostedTts=voiceCapabilities?.let{if(language=="ko")it.tts_ko else it.tts_en}==true
    val deviceTtsForLanguage=if(language=="ko")deviceSpeech?.korean==true else deviceSpeech?.english==true
    val ttsMode=VoiceDelivery.choose(hostedTts,deviceTtsForLanguage)
    val canBargeIn=bargeAvailable&&voiceCapabilities?.stt==true

    fun record(){
        if(recording||waiting!=null||!sessionValid())return
        val previousSpeech=speakingJob
        previousSpeech?.cancel()
        stopRecording.set(false)
        recording=true
        phase="🎙 듣는 중 · 말씀이 끝나면 자동 인식합니다"
        recordingJob=scope.launch{
            try{
                // Wait until audio focus/player cleanup finishes before starting the microphone.
                previousSpeech?.cancelAndJoin()
                check(sessionValid()){"대화방 또는 계정 변경"}
                val wav=EngineVoiceAudio.record(context,stopRecording,autoEndpoint=true,maxSeconds=20)
                check(sessionValid()){"대화방 또는 계정 변경"}
                phase="클라우드에서 음성 인식 중"
                val transcript=cloud.transcribeForMobile(wav,language)
                check(sessionValid()){"대화방 또는 계정 변경"}
                recognized=transcript
                answer=""
                val messageId=vm.sendMessage(roomId,transcript)
                if(messageId==null)error("대화방 작업 접수 실패")
                waiting=messageId
                phase="도미니온이 음성 지시를 처리 중"
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:com.dominion.mobile.platform.audio.NoSpeechDetected){
                continuous=false;phase="발화가 없어 연속 대화를 멈췄습니다. 다시 말씀해 주세요."
            }
            catch(e:Exception){continuous=false;phase="클라우드 음성 처리 실패 · "+(e.message?:"연결을 확인해 주세요")}
            finally{recording=false;recordingJob=null;stopRecording.set(true)}
        }
    }
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        if(it)record() else phase="마이크 권한이 필요합니다"
    }
    LaunchedEffect(roomId){
        try{
            voiceCapabilities=cloud.mobileVoiceCapabilities()
            if(originalAccount==null)originalAccount=cloud.savedAccountId()
            checked=true
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){phase="클라우드 연결을 확인해 주세요";checked=true}
    }
    LaunchedEffect(Unit){
        deviceSpeech=runCatching{DeviceSpeech.availability(context)}.getOrNull()
        if(bargeAvailable)phase="마이크를 눌러 클라우드 음성 대화를 시작하세요 · 자동 끼어들기 사용 가능"
    }
    // Auto-listen only after a successful response and explicit user opt-in.
    // Do not keep the microphone open while a job awaits approval or fails.
    LaunchedEffect(nextTurn){
        if(nextTurn>0&&continuous&&!recording&&waiting==null&&sessionValid()&&
            ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)record()
    }
    LaunchedEffect(room?.sessionId, state.settings.lang, checked, cloud.savedAccountId()){
        if(!checked)return@LaunchedEffect
        if(originalAccount==null){
            continuous=false
            phase="DOMINION Cloud 계정을 연결하면 음성 인식을 사용할 수 있습니다"
            return@LaunchedEffect
        }
        if(!sessionValid()||(if(state.settings.lang==Lang.KO)"ko" else "en")!=language){
            continuous=false;stopRecording.set(true);recordingJob?.cancel();speakingJob?.cancel();waiting=null
            phase="대화방 또는 계정 변경으로 음성 대화 중지"
        }
    }
    LaunchedEffect(room?.messages,waiting){
        val id=waiting?:return@LaunchedEffect
        val activeRoom=room?.takeIf{it.sessionId==originalSession}
        if(activeRoom==null||!sessionValid()){
            continuous=false;waiting=null;phase="대화 세션 변경으로 중지";return@LaunchedEffect
        }
        val owner=activeRoom.messages.firstOrNull{it.id==id}
        if(owner?.status==MessageStatus.FAILED||owner?.jobStatus in setOf("cancelled","failed","interrupted")){
            continuous=false;waiting=null;phase="작업 실패 · 대화 기록에서 확인해 주세요";return@LaunchedEffect
        }
        val reply=activeRoom.messages.firstOrNull{it.replyTo==id&&it.sender==MessageSender.ASSISTANT}
        if(reply!=null){
            waiting=null
            answer=reply.text
            if(owner?.jobStatus=="needs_review"){
                continuous=false
                phase="도구 실행 승인 필요 · 대화방에서 검토해 주세요"
            }else if(ttsMode==VoiceTtsMode.NONE){
                continuous=false
                phase="텍스트 답변 완료 · 이 기기에서 사용할 수 있는 음성 출력이 없습니다"
            }else{
                speakingJob?.cancel()
                speakingJob=scope.launch{
                    var device:DeviceSpeech.Session?=null
                    try{
                        var mode=ttsMode
                        if(mode==VoiceTtsMode.DEVICE){
                            device=DeviceSpeech.open(context,language)
                                ?: error("기기 음성 엔진을 시작하지 못했습니다")
                        }
                        phase=if(mode==VoiceTtsMode.CLOUD)"클라우드에서 답변 음성을 생성 중"
                            else "기기 음성으로 답변을 준비 중"
                        var interrupted=false
                        for(chunk in reply.text.take(2400).chunked(600)){
                            check(sessionValid()){"대화방 또는 계정 변경"}
                            val stop=AtomicBoolean(false)
                            var captured:ByteArray?=null
                            if(mode==VoiceTtsMode.CLOUD){
                                val audio=runCatching{cloud.speakForMobile(chunk,language)}.getOrNull()
                                if(audio==null){
                                    // A hosted TTS outage must not silence a configured
                                    // device engine. No cloud text leaves the device here.
                                    if(deviceTtsForLanguage){
                                        mode=VoiceTtsMode.DEVICE
                                        device=DeviceSpeech.open(context,language)
                                    }
                                    if(device==null)error("클라우드·기기 음성 출력을 사용할 수 없습니다")
                                }else{
                                    check(sessionValid()){"대화방 또는 계정 변경"}
                                    if(bargeIn&&bargeAvailable){
                                        phase="🔊 클라우드 답변 재생 중 · 말씀하시면 끼어들 수 있어요"
                                        captured=CloudVoicePlayback.playListening(context,audio)
                                    }else{
                                        phase="🔊 클라우드 음성 답변 재생 중"
                                        CloudVoicePlayback.play(context,audio)
                                    }
                                }
                            }
                            if(mode==VoiceTtsMode.DEVICE){
                                if(device==null)device=DeviceSpeech.open(context,language)
                                val speaker=requireNotNull(device){"기기 음성 엔진을 시작하지 못했습니다"}
                                if(bargeIn&&bargeAvailable){
                                    phase="🔊 기기 음성 답변 재생 중 · 말씀하시면 끼어들 수 있어요"
                                    captured=speaker.speakListening(context,chunk,stop)
                                }else{
                                    phase="🔊 기기 음성 답변 재생 중"
                                    check(speaker.speak(chunk,stop,communication=false)){"기기 음성 재생 실패"}
                                }
                            }
                            if(captured!=null){
                                check(sessionValid()){"대화방 또는 계정 변경"}
                                phase="끼어들기 감지 · 음성 인식 중"
                                val transcript=cloud.transcribeForMobile(captured,language)
                                check(sessionValid()){"대화방 또는 계정 변경"}
                                recognized=transcript
                                val nextMessage=vm.sendMessage(roomId,transcript) ?: error("대화방 작업 접수 실패")
                                answer=""
                                waiting=nextMessage
                                phase="도미니온이 끼어든 요청을 처리 중"
                                interrupted=true
                                break
                            }
                        }
                        if(!interrupted){
                            phase=if(continuous)"답변 완료 · 다음 발화 준비 중" else "답변 완료 · 마이크를 눌러 이어서 말씀하세요"
                            if(continuous&&sessionValid())nextTurn++
                        }
                    }catch(cancelled:CancellationException){throw cancelled}
                    catch(e:Exception){
                        continuous=false
                        phase="텍스트 답변 완료 · 음성 출력을 확인해 주세요 ("+(e.message?:"오디오 오류")+")"
                    }finally{
                        device?.close()
                        speakingJob=null
                    }
                }
            }
        }
    }
    DisposableEffect(roomId){
        onDispose{
            stopRecording.set(true)
            recordingJob?.cancel()
            speakingJob?.cancel()
        }
    }

    AlertDialog(
        onDismissRequest={stopRecording.set(true);recordingJob?.cancel();speakingJob?.cancel();onDismiss()},
        title={Text("도미니온 · 클라우드 음성 비서")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(phase,style=MaterialTheme.typography.titleSmall)
                if(checked&&voiceCapabilities==null)
                    Text("DOMINION Cloud 계정 연결이 필요합니다.",
                        color=MaterialTheme.colorScheme.error)
                if(checked&&voiceCapabilities?.stt==false)
                    Text("클라우드 음성 인식이 아직 설정되지 않았습니다.",
                        color=MaterialTheme.colorScheme.error)
                if(checked&&ttsMode==VoiceTtsMode.DEVICE)
                    Text(if(voiceCapabilities==null)
                        "이 기기의 TTS 엔진은 정상입니다. 계정 연결 후 답변 재생에 사용됩니다."
                        else "서버 음성 합성 대신 이 기기의 TTS로 답변을 읽습니다.",
                        style=MaterialTheme.typography.bodySmall)
                if(checked&&ttsMode==VoiceTtsMode.NONE)
                    Text("사용 가능한 음성 출력이 없어 텍스트 답변만 표시됩니다.",
                        style=MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(continuous,{enabled->
                        continuous=enabled
                        if(!enabled&&recording){stopRecording.set(true);recordingJob?.cancel();phase="연속 대화 중지"}
                    },enabled=voiceCapabilities?.stt==true)
                    Column(Modifier.padding(top=6.dp)) {
                        Text("연속 음성 대화")
                        Text("답변 재생이 끝나면 다음 발화를 자동으로 듣습니다. 끄거나 닫으면 자동 듣기가 중지됩니다.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    Checkbox(bargeIn,{bargeIn=it},enabled=canBargeIn)
                    Column(Modifier.padding(top=6.dp)) {
                        Text(if(bargeAvailable)"자동 끼어들기 (기기 반향 제거)" else "자동 끼어들기 (기기 미지원)")
                        Text("답변을 듣는 중에 말하면 재생을 멈추고 그 발화를 새 요청으로 접수합니다.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(recognized.isNotBlank())Text("나: "+recognized)
                if(answer.isNotBlank())Text("도미니온: "+answer.take(1800))
                Text("음성 인식은 서버에서 실행합니다. 클라우드 합성이 없으면 기기 TTS만 사용하며, 휴대폰에 음성 AI 모델을 설치하지 않습니다.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            if(recording)TextButton(onClick={stopRecording.set(true);phase="발화 마무리 중"}){Text("발화 끝내기")}
            else TextButton(
                enabled=waiting==null && sessionValid() && voiceCapabilities?.stt==true,
                onClick={
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==
                        PackageManager.PERMISSION_GRANTED)record()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }){Text(if(speakingJob?.isActive==true)"🎙 끼어들기" else "🎙 말하기")}
        },
        dismissButton={
            Row{
                if(speakingJob?.isActive==true)TextButton(onClick={
                    continuous=false;speakingJob?.cancel();phase="음성 재생 중단"
                }){Text("읽기 중단")}
                TextButton(onClick={
                    stopRecording.set(true)
                    recordingJob?.cancel();speakingJob?.cancel();onDismiss()
                }){Text("닫기")}
            }
        }
    )
}
