package com.dominion.mobile.platform.audio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import com.dominion.mobile.data.VoiceTurn
import com.dominion.mobile.data.WaveAudio
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.platform.background.BackgroundJobMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class AssistantVoiceState(val active:Boolean=false,val id:String?=null,val roomId:String?=null,
    val phase:String="중지",val nonce:String?=null,val transcript:String="",val answer:String="")

/** A user-started native microphone FGS. Inference and all actual work are
 * native DOMINION room jobs. Process death does not restart recording. */
class AssistantVoiceService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var worker:Job?=null
    private var voiceId:String?=null
    private val stopCapture=AtomicBoolean(false)
    private var playback:Job?=null
    override fun onBind(intent:Intent?):IBinder?=null

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP || intent?.action==END_SPEECH || intent?.action==SKIP){
            if(intent.getStringExtra("id")==voiceId){
                when(intent.action){
                    STOP->stopSelf()
                    END_SPEECH->stopCapture.set(true)
                    SKIP->playback?.cancel()
                }
            }
            return START_NOT_STICKY
        }
        if(worker?.isActive==true)return START_NOT_STICKY
        val id=intent?.getStringExtra("id")
        val roomId=intent?.getStringExtra("room")
        val session=intent?.getStringExtra("session")
        if(intent?.action!=START || id.isNullOrBlank() || roomId.isNullOrBlank() || session.isNullOrBlank()){
            stopSelf();return START_NOT_STICKY
        }
        val app=application as DominionApplication
        if(!app.containerReady || ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stopSelf();return START_NOT_STICKY
        }
        val container=app.container
        val engine=container.engine
        val repo=container.repository
        if(engine==null || repo.state.value.call!=null || repo.state.value.settings.appLock ||
            repo.state.value.conversation(roomId)?.sessionId!=session){stopSelf();return START_NOT_STICKY}
        voiceId=id
        _state.value=AssistantVoiceState(true,id,roomId,"본체 음성 준비")
        try {
            ServiceCompat.startForeground(this,NOTIFICATION,notification(engine.origin,roomId,id,"본체 음성 준비"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }catch(_:Exception){_state.value=_state.value.copy(active=false,phase="마이크 서비스 시작 실패");stopSelf();return START_NOT_STICKY}
        fun valid(){
            check(container.engine===engine && container.repository===repo &&
                repo.state.value.call==null && !repo.state.value.settings.appLock &&
                repo.state.value.conversation(roomId)?.sessionId==session)
        }
        fun phase(value:String){
            _state.update{it.copy(phase=value)}
            runCatching{getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(engine.origin,roomId,id,value))}
        }
        worker=scope.launch{
            try{
                val caps=repo.voiceStatus()
                check(caps.whisper?.available==true && caps.piper?.available==true)
                repo.refreshRoomHistory(roomId);valid()
                coroutineScope{
                    val guard=launch{
                        while(isActive){valid();delay(300)}
                    }
                    try{
                        var silence=0
                        while(isActive){
                            valid();stopCapture.set(false);phase("듣는 중")
                            val wav=try{EngineVoiceAudio.record(this@AssistantVoiceService,stopCapture,autoEndpoint=true)}
                                catch(_:NoSpeechDetected){
                                    if(++silence>=3){phase("발화 없음 · 음성 비서 중지");break}
                                    continue
                                }
                            silence=0
                            valid();phase("본체 음성 인식 중")
                            val transcript=repo.transcribeVoice(wav).trim()
                            valid()
                            if(transcript.isBlank())continue
                            val nonce="voice_"+UUID.randomUUID()
                            _state.update{it.copy(nonce=nonce,transcript=transcript,answer="")}
                            phase("본체 작업 접수 중")
                            repo.submitAssistantVoice(roomId,session,nonce,transcript)
                            valid()
                            while(isActive){
                                valid()
                                try{repo.refreshRoomHistory(roomId)}
                                catch(e:CancellationException){throw e}
                                catch(_:Exception){phase("본체 연결 복구 대기");delay(2500);continue}
                                val execution=repo.roomExecution(roomId)
                                check(execution.sessionId==session)
                                val owner=requireNotNull(VoiceTurn.owner(requireNotNull(repo.state.value.conversation(roomId)).messages,roomId,nonce))
                                if(execution.approvals.orEmpty().any{it.actionableIn(session)}){
                                    phase("도구 승인 필요 · 대화방에서 확인");delay(1800);continue
                                }
                                if(owner.status==MessageStatus.FAILED || owner.jobStatus in setOf("failed","cancelled","paused","interrupted","stopped","needs_review")){
                                    phase("작업 확인 필요 · 대화방에 기록 유지");return@coroutineScope
                                }
                                val replies=VoiceTurn.replies(requireNotNull(repo.state.value.conversation(roomId)).messages,roomId,owner)
                                if(replies.isEmpty()){
                                    phase("본체 작업·응답 대기");delay(1500);continue
                                }
                                _state.update{it.copy(answer=replies.joinToString("\n"){reply->reply.text}.take(8000))}
                                phase("본체 음성 응답 중")
                                val reading=launch{
                                    for(chunk in replies.flatMap{WaveAudio.speechChunks(it.text)}){
                                        valid()
                                        val speech=repo.synthesizeVoice(chunk)
                                        valid()
                                        EngineVoiceAudio.play(this@AssistantVoiceService,speech,communication=true)
                                    }
                                }
                                playback=reading
                                try{reading.join()}finally{playback=null}
                                _state.update{it.copy(nonce=null)}
                                delay(400)
                                break
                            }
                        }
                    }finally{guard.cancelAndJoin()}
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){phase("음성·본체 연결 중단 · 접수 작업은 대화방에 유지")}
            finally{stopCapture.set(true);stopSelf()}
        }
        return START_NOT_STICKY
    }

    private fun notification(origin:String,room:String,id:String,phase:String):Notification{
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"DOMINION 음성 비서",NotificationManager.IMPORTANCE_LOW))
        val open=Intent(this,MainActivity::class.java).apply{
            this.flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("dominion_notification_origin",BackgroundJobMonitor.originKey(origin))
            putExtra("dominion_notification_room",room)
            (application as DominionApplication).container.engine?.let {
                putExtra("dominion_notification_credential",com.dominion.mobile.platform.background.NotificationReplyReceiver.credentialKey(it))
            }
        }
        val content=PendingIntent.getActivity(this,NOTIFICATION,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun command(action:String,code:Int)=PendingIntent.getService(this,code,
            Intent(this,AssistantVoiceService::class.java).setAction(action).putExtra("id",id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DOMINION 음성 비서").setContentText(phase).setContentIntent(content)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0,"중지",command(STOP,NOTIFICATION+1))
        if(phase=="듣는 중")builder.addAction(0,"발화 끝내기",command(END_SPEECH,NOTIFICATION+2))
        if(phase=="본체 음성 응답 중")builder.addAction(0,"응답 건너뛰기",command(SKIP,NOTIFICATION+3))
        return builder.build()
    }
    override fun onDestroy(){
        stopCapture.set(true)
        scope.cancel()
        if(_state.value.id==voiceId)_state.update{it.copy(active=false,phase=if(it.phase.contains("중단")||it.phase.contains("중지")||it.phase.contains("확인 필요"))it.phase else "중지 · 접수한 작업은 본체에 유지")}
        ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object{
        private const val CHANNEL="dominion_assistant_voice"
        private const val NOTIFICATION=4310
        private const val START="com.dominion.mobile.voice.START"
        private const val STOP="com.dominion.mobile.voice.STOP"
        private const val END_SPEECH="com.dominion.mobile.voice.END"
        private const val SKIP="com.dominion.mobile.voice.SKIP"
        private val _state=MutableStateFlow(AssistantVoiceState())
        val state=_state.asStateFlow()
        fun start(context:Context,room:String,session:String){
            check(!state.value.active)
            ContextCompat.startForegroundService(context,Intent(context,AssistantVoiceService::class.java)
                .setAction(START).putExtra("id",UUID.randomUUID().toString()).putExtra("room",room).putExtra("session",session))
        }
        fun stop(context:Context){context.stopService(Intent(context,AssistantVoiceService::class.java))}
    }
}
