package com.dominion.mobile.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dominion.mobile.data.WaveAudio
import com.dominion.mobile.platform.audio.EngineVoiceAudio
import com.dominion.mobile.ui.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
private fun StopOnBackground(onStop: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onStop)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) latest.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun EngineVoiceInputDialog(vm: AppViewModel, onDismiss: () -> Unit, onTranscript: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var recording by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf<ByteArray?>(null) }
    var transcript by remember { mutableStateOf("") }
    val stop = remember { AtomicBoolean(false) }
    LaunchedEffect(Unit) {
        try { available = vm.voiceStatus().whisper?.available == true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "본체 음성 상태를 조회하지 못했습니다."; available = false }
    }
    fun begin() {
        if (recording || converting || available != true || vm.state.value.call != null) return
        error = null; audio = null; transcript = ""; stop.set(false); recording = true
        job = scope.launch {
            try { audio = EngineVoiceAudio.record(context, stop) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "녹음 실패 · 마이크 권한과 녹음 길이를 확인하세요." }
            finally { recording = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) begin() else error = "마이크 권한이 필요합니다."
    }
    StopOnBackground { stop.set(true); job?.cancel(); audio = null; onDismiss() }
    DisposableEffect(Unit) { onDispose { stop.set(true); job?.cancel() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("본체 음성 인식") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("최대 60초 녹음 후 직접 변환을 누르면 설정된 본체로 WAV 음성을 보냅니다. 변환한 문장은 확인 후 대화에 넣습니다.")
                Text(when (available) { true -> "Whisper 준비 확인"; false -> "본체 Whisper 미설치 또는 연결 실패"; null -> "본체 확인 중" })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (recording) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick = { stop.set(true) }) { Text("녹음 끝내기") }
                } else if (!converting) {
                    OutlinedButton(enabled = available == true, onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text(if (audio == null) "녹음 시작" else "다시 녹음")
                    }
                }
                if (audio != null && !recording) {
                    Button(enabled = !converting, onClick = {
                        val wav = audio ?: return@Button
                        error = null
                        converting = true
                        job = scope.launch {
                            try { transcript = vm.transcribeVoice(wav) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "음성 변환 실패 · 녹음은 유지합니다." }
                            finally { converting = false }
                        }
                    }) { Text("본체에서 문자로 변환") }
                }
                if (converting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (transcript.isNotBlank()) OutlinedTextField(transcript, { transcript = it.take(32000) },
                    label = { Text("변환 결과 확인") }, maxLines = 4)
            }
        },
        confirmButton = { TextButton(enabled = transcript.isNotBlank() && !converting && !recording,
            onClick = { onTranscript(transcript) }) { Text("작성창에 넣기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
fun EngineSpeechDialog(vm: AppViewModel, text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var position by remember { mutableIntStateOf(0) }
    val chunks = remember(text) { runCatching { WaveAudio.speechChunks(text) }.getOrNull().orEmpty() }
    LaunchedEffect(Unit) {
        try { ready = vm.voiceStatus().piper?.available == true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { ready = false }
    }
    StopOnBackground { job?.cancel(); onDismiss() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("본체 음성으로 읽기") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("선택한 메시지를 설정된 본체 Piper 음성으로 합성합니다.")
            Text(when (ready) { true -> "Piper 준비 확인"; false -> "Piper 미설치 또는 본체 연결 실패"; null -> "확인 중" })
            if (chunks.isEmpty()) Text("읽을 수 있는 메시지는 최대 32,000자입니다.")
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("음성 합성·재생 " + position + "/" + chunks.size) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = ready == true && !busy && chunks.isNotEmpty(),
            onClick = {
                busy = true; error = null
                job = scope.launch {
                    try {
                        for ((index, chunk) in chunks.withIndex()) {
                            position = index + 1
                            EngineVoiceAudio.play(context, vm.synthesizeVoice(chunk))
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "음성 합성 또는 재생 실패" }
                    finally { busy = false }
                }
            }) { Text("읽기 시작") } },
        dismissButton = { TextButton(onClick = { job?.cancel(); onDismiss() }) { Text(if (busy) "중단" else "닫기") } })
}
