package com.dominion.mobile.platform.avatar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import com.dominion.mobile.data.ObjAvatarMesh
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.CharacterShape
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

/** Non-exported native 3D/AR surface. Camera is acquired only in explicitly selected AR mode. */
class AvatarSceneActivity : ComponentActivity() {
    private var ar = false
    private var installedRequested = false
    private var permissionRequested = false
    private var session: Session? = null
    private var resumedSurface = false
    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: AvatarSceneRenderer
    private var status by mutableStateOf("준비 중")
    private var name = "DOMINION"
    private var importing by mutableStateOf(false)
    private var imported by mutableStateOf(false)
    private var expression by mutableStateOf("idle")
    private var savedModel by mutableStateOf(false)
    private var hasMouth by mutableStateOf(false)
    private var hasRig by mutableStateOf(false)
    private var headPose by mutableFloatStateOf(0f)
    private var jawPose by mutableFloatStateOf(0f)
    private var mouthPreview by mutableFloatStateOf(0f)
    private fun showScene(scene: AvatarModelStore.Scene) {
        renderer.replaceMesh(scene.mesh, scene.mouth, scene.rig)
        hasRig = scene.rig != null
        headPose = 0f; jawPose = 0f; renderer.jointPose(0f,0f)
        mouthPreview = 0f
        renderer.morphPreview(0f)
        imported = true
        hasMouth = scene.mouth != null
        savedModel = true
        modelStatus = "저장된 OBJ · " + scene.mesh.size / 18 + "개 삼각형" +
            (if (hasMouth) " · 입 모양 연결됨" else "") + (if (hasRig) " · 머리·턱 리그 연결됨" else "")
    }
    private var modelStatus by mutableStateOf("")
    private var speaking by mutableStateOf(false)
    private var speechStatus by mutableStateOf("")
    private var speechJob: Job? = null

    private fun previewSpeech() {
        if (speaking) { speechJob?.cancel(); return }
        val container = (application as com.dominion.mobile.DominionApplication).container
        val repo = container.repository
        if (container.engine == null) { speechStatus = "본체 연결 필요"; return }
        speaking = true
        speechStatus = "본체 Piper 음성 준비 중"
        speechJob = lifecycleScope.launch {
            try {
                val wav = repo.synthesizeVoice("안녕하세요. 저는 $name 입니다. 무엇을 도와드릴까요?")
                check(container.repository === repo)
                speechStatus = "실제 음성 재생 중"
                renderer.expression("voice")
                com.dominion.mobile.platform.audio.EngineVoiceAudio.play(
                    this@AvatarSceneActivity, wav, onSpeechLevel = renderer::speechLevel)
                speechStatus = "음성 재생 완료"
            } catch (e: CancellationException) { speechStatus = "음성 재생 중단"; throw e }
            catch (_: Exception) { speechStatus = "본체 음성 합성 또는 재생 실패" }
            finally { renderer.speechLevel(0f); renderer.expression(expression); speaking = false }
        }
    }
    private lateinit var modelStore: AvatarModelStore
    private lateinit var modelKey: String

    private fun restoreSavedModel() {
        if (importing) return
        importing = true
        lifecycleScope.launch {
            try {
                val scene = withContext(Dispatchers.IO) { modelStore.loadScene(modelKey) }
                savedModel = scene != null
                if (scene != null) showScene(scene)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { modelStatus = "저장 모델 읽기 실패 · 기존 파일은 유지됩니다." }
            finally { importing = false }
        }
    }
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) {
            importing = true
            lifecycleScope.launch {
                try {
                    val mesh = withContext(Dispatchers.IO) {
                        require(uri.scheme == "content")
                        val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val n = input.read(chunk)
                                if (n < 0) break
                                require(output.size() + n <= ObjAvatarMesh.MAX_BYTES)
                                output.write(chunk,0,n)
                            }
                            output.toByteArray()
                        }
                        modelStore.save(modelKey, bytes)
                        requireNotNull(modelStore.loadScene(modelKey))
                    }
                    showScene(mesh)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { modelStatus = "OBJ 저장 실패 · 5MB 이하 삼각형 메시 파일을 선택하세요. 기존 모델은 유지됩니다." }
                finally { importing = false }
            }
        }
    }
    private val mouthPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && imported) {
            importing = true
            lifecycleScope.launch {
                try {
                    val scene = withContext(Dispatchers.IO) {
                        require(uri.scheme == "content")
                        val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val n = input.read(chunk)
                                if (n < 0) break
                                require(output.size() + n <= ObjAvatarMesh.MAX_BYTES)
                                output.write(chunk, 0, n)
                            }
                            output.toByteArray()
                        }
                        modelStore.saveMouth(modelKey, bytes)
                    }
                    showScene(scene)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { modelStatus = "입 모양 연결 실패 · 원본과 동일한 정점·삼각형 순서의 OBJ가 필요합니다." }
                finally { importing = false }
            }
        }
    }
    private val rigPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && imported && !speaking) {
            importing = true
            lifecycleScope.launch {
                try {
                    val scene = withContext(Dispatchers.IO) {
                        require(uri.scheme == "content")
                        val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val n = input.read(chunk)
                                if (n < 0) break
                                require(output.size() + n <= ObjAvatarMesh.MAX_BYTES)
                                output.write(chunk,0,n)
                            }
                            output.toByteArray()
                        }
                        modelStore.saveRig(modelKey, bytes)
                    }
                    showScene(scene)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { modelStatus = "리그 연결 실패 · 원본 SHA-256과 정점 가중치를 확인하세요." }
                finally { importing = false }
            }
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) startScene()
        else if (!granted) status = "AR 카메라 권한이 필요합니다."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ar = intent.getBooleanExtra("ar",false)
        modelStore = AvatarModelStore(noBackupFilesDir)
        modelKey = AvatarModelStore.key(
            requireNotNull(intent.getStringExtra("model_scope")),
            requireNotNull(intent.getStringExtra("character_id")))
        name = intent.getStringExtra("name").orEmpty().take(80).ifBlank { "DOMINION" }
        val shape = runCatching { CharacterShape.valueOf(intent.getStringExtra("shape").orEmpty()) }
            .getOrDefault(CharacterShape.BLOB)
        val color = intent.getLongExtra("color",0xFF4797EF).toInt()
        renderer = AvatarSceneRenderer(ar,shape,color,
            rotation = { display?.rotation ?: 0 },
            status = { message -> runOnUiThread { status = message } })
        surface = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { renderer.touchDown(event.x,event.y); true }
                    MotionEvent.ACTION_MOVE -> { if (!ar) renderer.drag(event.x,event.y); true }
                    MotionEvent.ACTION_UP -> { if (ar) renderer.place(event.x,event.y); performClick(); true }
                    else -> true
                }
            }
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(name + if (ar) " · AR" else " · 3D", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { finish() }) { Text("닫기") }
                        }
                        AndroidView(factory = { surface }, modifier = Modifier.weight(1f).fillMaxWidth())
                        Text(status, modifier = Modifier.padding(12.dp))
                        if (modelStatus.isNotBlank()) Text(modelStatus,
                            modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { renderer.changeSize(-1) }) { Text("축소") }
                            TextButton(onClick = { renderer.changeSize(1) }) { Text("확대") }
                            TextButton(onClick = { renderer.reset() }) { Text(if (ar) "배치 지우기" else "회전 초기화") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(enabled = !importing, onClick = { modelPicker.launch(arrayOf("*/*")) }) { Text("OBJ 가져오기·저장") }
                            TextButton(enabled = imported && !importing, onClick = {
                                renderer.restoreMesh(); imported = false; hasMouth = false; hasRig = false
                                modelStatus = "기본 모델 표시 중 · 저장된 OBJ는 유지됩니다."
                            }) { Text("기본 모델") }
                            if (savedModel && !imported) TextButton(enabled = !importing,
                                onClick = { restoreSavedModel() }) { Text("저장 모델") }
                        }
                        if (!imported) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("idle" to "기본", "happy" to "웃음", "talk" to "말하기").forEach { (mode, title) ->
                                FilterChip(selected = expression == mode, onClick = {
                                    expression = mode; renderer.expression(mode)
                                }, label = { Text(title) })
                            }
                        }
                        if (imported) {
                            TextButton(enabled = !importing && !speaking, onClick = {
                                mouthPicker.launch(arrayOf("*/*"))
                            }) { Text("입 벌린 OBJ 연결") }
                            TextButton(enabled = !importing && !speaking, onClick = {
                                rigPicker.launch(arrayOf("application/json","text/plain","*/*"))
                            }) { Text("머리·턱 리그 JSON 연결") }
                            if (hasRig) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    Text("머리", modifier = Modifier.padding(top = 12.dp))
                                    Slider(value = headPose, onValueChange = {
                                        headPose = it; renderer.jointPose(it,jawPose)
                                    }, valueRange = -35f..35f, modifier = Modifier.weight(1f))
                                    Text("턱", modifier = Modifier.padding(top = 12.dp))
                                    Slider(value = jawPose, onValueChange = {
                                        jawPose = it; renderer.jointPose(headPose,it)
                                    }, valueRange = 0f..30f, enabled = !speaking, modifier = Modifier.weight(1f))
                                }
                            }
                            if (hasMouth) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    Text("입 모양", modifier = Modifier.padding(top = 12.dp))
                                    Slider(value = mouthPreview, onValueChange = {
                                        mouthPreview = it; renderer.morphPreview(it)
                                    }, enabled = !speaking, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (!imported || hasMouth || hasRig) TextButton(enabled = !importing, onClick = { previewSpeech() }) {
                            Text(if (speaking) "음성 중단" else "본체 음성으로 말하기")
                        }
                        if (speechStatus.isNotBlank()) Text(speechStatus,
                            modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
                        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (ar) Text("AR은 Google Play Services for AR를 사용합니다. 카메라 영상은 기기에서 공간 추적·표시에 사용되며 DOMINION 본체에 자동 전송되지 않습니다. Google의 AR 개인정보 처리방침: developers.google.com/ar/develop/privacy",
                            modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        restoreSavedModel()
    }

    override fun onResume() { super.onResume(); startScene() }

    private fun startScene() {
        if (!::surface.isInitialized || resumedSurface || isFinishing) return
        if (!ar) {
            status = "화면을 드래그하여 3D 캐릭터 회전"
            surface.onResume(); resumedSurface = true; return
        }
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequested) { permissionRequested = true; permission.launch(Manifest.permission.CAMERA) }
            else status = "AR 카메라 권한을 허용해야 배치할 수 있습니다."
            return
        }
        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(this,!installedRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installedRequested = true
                        status = "Google Play Services for AR 설치·업데이트 필요"
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }
                val created = Session(this)
                try {
                    created.configure(Config(created).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    })
                    session = created
                } catch (e: Exception) { created.close(); throw e }
            }
            requireNotNull(session).resume()
            renderer.setSession(session)
            surface.onResume()
            resumedSurface = true
            status = "주변 바닥·책상을 비추고 평면을 탐색하세요."
        } catch (_: Exception) {
            runCatching { session?.close() }
            session = null
            renderer.setSession(null)
            status = "AR을 시작하지 못했습니다. 지원 기기·카메라·Google Play Services for AR 상태를 확인하세요."
        }
    }

    override fun onPause() {
        speechJob?.cancel()
        if (resumedSurface) {
            surface.onPause()
            resumedSurface = false
            runCatching { session?.pause() }
        }
        super.onPause()
    }
    override fun onDestroy() {
        renderer.setSession(null)
        runCatching { session?.close() }
        session = null
        super.onDestroy()
    }

    companion object {
        fun open(context: Context, character: Character, ar: Boolean) {
            val app = context.applicationContext as com.dominion.mobile.DominionApplication
            val scope = if (app.containerReady) app.container.reminderScope() else "unconfigured"
            val launch = Intent(context,AvatarSceneActivity::class.java).apply {
                putExtra("model_scope", scope); putExtra("character_id", character.id)
                putExtra("ar",ar); putExtra("name",character.name)
                putExtra("shape",character.shape.name); putExtra("color",character.color)
            }
            context.startActivity(launch)
        }
    }
}
