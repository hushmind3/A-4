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
    private var modelStatus by mutableStateOf("")
    private lateinit var modelStore: AvatarModelStore
    private lateinit var modelKey: String

    private fun restoreSavedModel() {
        if (importing) return
        importing = true
        lifecycleScope.launch {
            try {
                val mesh = withContext(Dispatchers.IO) { modelStore.load(modelKey) }
                savedModel = mesh != null
                if (mesh != null) {
                    renderer.replaceMesh(mesh)
                    imported = true
                    modelStatus = "저장된 OBJ · " + mesh.size / 18 + "개 삼각형"
                }
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
                    }
                    renderer.replaceMesh(mesh)
                    imported = true
                    savedModel = true
                    modelStatus = "OBJ 모델 저장 완료 · " + mesh.size / 18 + "개 삼각형"
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { modelStatus = "OBJ 저장 실패 · 5MB 이하 삼각형 메시 파일을 선택하세요. 기존 모델은 유지됩니다." }
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
                                renderer.restoreMesh(); imported = false
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
