package com.dominion.mobile.platform.avatar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dominion.mobile.data.StandardAvatarAsset
import kotlinx.coroutines.*

class StandardAvatarActivity: ComponentActivity() {
    private lateinit var store:StandardAvatarStore
    private lateinit var keyId:String
    private var asset by mutableStateOf<StandardAvatarAsset?>(null)
    private var status by mutableStateOf("저장된 모델 확인 중")
    private var loading by mutableStateOf(false)
    private var speechLevel by mutableFloatStateOf(0f)
    private var animations by mutableStateOf<List<String>>(emptyList())
    private var index by mutableIntStateOf(0)
    private var failed by mutableStateOf(false)
    private var expression by mutableStateOf<String?>(null)
    private var strength by mutableFloatStateOf(1f)
    private var viseme by mutableStateOf<String?>("aa")
    private var speech:Job?=null
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && !loading) {
            loading=true
            lifecycleScope.launch {
                try {
                    val result=withContext(Dispatchers.IO) {
                        require(uri.scheme=="content")
                        val bytes=requireNotNull(contentResolver.openInputStream(uri)).use { stream ->
                            val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                            while(true) {
                                val n=stream.read(chunk);if(n<0)break
                                require(out.size()+n<=StandardAvatarAsset.MAX_BYTES);out.write(chunk,0,n)
                            };out.toByteArray()
                        }
                        store.save(keyId,bytes)
                    }
                    animations=emptyList();index=0;failed=false;expression=null;asset=result
                    status="저장 완료 · 렌더러에서 모델 확인"
                } catch(e:CancellationException){throw e}
                catch(_:Exception){status="가져오기 실패 · 모델 형식·리소스·32MB 한도를 확인하세요."}
                finally{loading=false}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=StandardAvatarStore(noBackupFilesDir)
        keyId=AvatarModelStore.key(requireNotNull(intent.getStringExtra("model_scope")),
            requireNotNull(intent.getStringExtra("character_id")))
        val name=intent.getStringExtra("name").orEmpty()
        lifecycleScope.launch {
            try { asset=withContext(Dispatchers.IO){store.load(keyId)};status=if(asset==null)"GLB·VRM·glTF 또는 리소스 ZIP을 가져오세요." else "저장 모델 로드" }
            catch(e:CancellationException){throw e}
            catch(_:Exception){status="저장 모델 확인 실패"}
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row {
                            Text(name+" · 표준 3D 모델",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                            TextButton(onClick={finish()}){Text("닫기")}
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val model=asset
                            if(model!=null && !failed) StandardAvatarView(model,Modifier.fillMaxSize(),speechLevel,index,
                                onAnimations={animations=it},onFailure={failed=true;status="그래픽 모델 로드·렌더링 실패"},
                                expression=expression,expressionStrength=strength,viseme=viseme)
                            else Text(if(failed)"모델 렌더링 실패" else "저장된 모델 없음")
                        }
                        Text(status)
                        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row {
                            TextButton(enabled=!loading,onClick={picker.launch(arrayOf("*/*"))}){Text("모델 가져오기")}
                            TextButton(enabled=!loading && asset!=null,onClick={
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO){store.remove(keyId)}
                                    asset=null;animations=emptyList();status="표준 모델 연결 해제 · OBJ는 유지"
                                }
                            }){Text("연결 해제")}
                            TextButton(enabled=asset!=null && !failed,onClick={preview(name)}){Text(if(speech?.isActive==true)"음성 중단" else "음성 테스트")}
                        }
                        if(animations.isNotEmpty()) {
                            Text("애니메이션: "+animations.getOrElse(index){""})
                            Row {
                                TextButton(onClick={index=(index+animations.size-1)%animations.size}){Text("이전")}
                                TextButton(onClick={index=(index+1)%animations.size}){Text("다음")}
                            }
                        }
                        val expressionNames=remember(asset) {
                            runCatching{asset?.expressionRig?.expressions?.keys?.filter{it !in setOf("aa","ih","ou","ee","oh")}?.toList().orEmpty()}
                                .getOrDefault(emptyList())
                        }
                        if(expressionNames.isNotEmpty()){
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                var open by remember{mutableStateOf(false)}
                                TextButton(onClick={open=true}){Text("표정: "+(expression?:"기본"))}
                                DropdownMenu(expanded=open,onDismissRequest={open=false}){
                                    DropdownMenuItem(text={Text("기본")},onClick={expression=null;open=false})
                                    expressionNames.forEach{label->DropdownMenuItem(text={Text(label)},onClick={expression=label;open=false})}
                                }
                            }
                            Slider(strength,{strength=it})
                        }
                        Text("입 모양 확인")
                        Slider(speechLevel,{speechLevel=it},enabled=speech?.isActive!=true)
                        Text("VRM 표정과 음성 모프를 사용합니다. 스프링 본·재질 표정은 미지원입니다.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    private fun preview(name:String) {
        if(speech?.isActive==true){speech?.cancel();return}
        speech=lifecycleScope.launch {
            try {
                val repo=(application as com.dominion.mobile.DominionApplication).container.repository
                val text="안녕하세요. 저는 $name 입니다."
                val aligned=repo.synthesizeAlignedVoice(text)
                val wave=aligned?.wav?:repo.synthesizeVoice(text)
                com.dominion.mobile.platform.audio.EngineVoiceAudio.play(this@StandardAvatarActivity,wave,
                    onSpeechLevel={if(aligned==null)speechLevel=it},
                    onPositionMs=aligned?.let{speech->{ms:Int->
                        val cue=speech.timeline.at(ms)
                        viseme=when(cue?.viseme){"a"->"aa";"i"->"ih";"u"->"ou";"e"->"ee";"o"->"oh";else->null}
                        speechLevel=cue?.openness?:0f
                    }})
                status="음성 재생 완료"
            } catch(e:CancellationException){throw e}
            catch(_:Exception){status="본체 음성 재생 실패"}
            finally{speechLevel=0f;viseme="aa"}
        }
    }
    override fun onPause(){speech?.cancel();super.onPause()}
}
