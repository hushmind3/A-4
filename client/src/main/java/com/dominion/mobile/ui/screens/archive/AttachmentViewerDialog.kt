package com.dominion.mobile.ui.screens.archive

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominion.mobile.data.model.ArchiveItem
import com.dominion.mobile.platform.attachments.ArchiveShare
import com.dominion.mobile.platform.attachments.LocalAttachment
import com.dominion.mobile.ui.components.StopOnBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Actual attachment data, fetched through the existing room-authorized API. */
@Composable
fun AttachmentViewerDialog(item: ArchiveItem, load: suspend () -> ByteArray, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var retry by remember(item.id) { mutableIntStateOf(0) }
    var busy by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, retry) {
        bytes = null
        error = null
        try { bytes = load().also { require(it.isNotEmpty() && it.size <= LocalAttachment.MAX_BYTES) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "실제 원본 조회 실패 · 본체 연결과 파일 권한을 확인하세요." }
    }
    StopOnBackground(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text("다시 조회") }
                } else if (bytes == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("본체에서 파일을 읽는 중")
                }
                bytes?.let { content ->
                    val mime = item.mimeType.orEmpty().lowercase()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            mime.startsWith("image/") -> ImagePreview(content)
                            mime == "application/pdf" -> PdfPreview(content)
                            mime.startsWith("video/") || mime.startsWith("audio/") -> MediaPreview(content, mime)
                            mime.startsWith("text/") || mime in setOf("application/json","application/xml") ||
                                item.title.endsWith(".md", true) || item.title.endsWith(".html", true) -> {
                                SelectionContainer {
                                    Text(content.toString(Charsets.UTF_8).take(150000),
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp))
                                }
                            }
                            else -> Text("이 파일 형식은 설치된 문서 앱에서 열 수 있습니다.")
                        }
                    }
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val intent = withContext(Dispatchers.IO) { ArchiveShare.viewIntent(context, item, content) }
                                context.startActivity(Intent.createChooser(intent, "원본 파일 열기"))
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "이 파일을 열 수 있는 앱이 없습니다." }
                            finally { busy = false }
                        }
                    }) { Text("외부 앱에서 원본 열기") }
                    Text("실제 파일 · " + content.size + " bytes", style = MaterialTheme.typography.labelSmall)
                } ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

private suspend fun decodePreview(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096 ||
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > 12_000_000L) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun ImagePreview(bytes: ByteArray) {
    var bitmap by remember(bytes) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }
    LaunchedEffect(bytes) {
        try { bitmap = decodePreview(bytes); failed = bitmap == null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    if (failed) Text("이미지 해석 실패")
    else bitmap?.let { ZoomBitmap(it) } ?: CircularProgressIndicator()
}

@Composable
private fun ZoomBitmap(bitmap: Bitmap) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var x by remember(bitmap) { mutableFloatStateOf(0f) }
    var y by remember(bitmap) { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(bitmap) {
            detectTransformGestures { _, pan, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 5f)
                x = if (zoom == 1f) 0f else (x + pan.x).coerceIn(-size.width * zoom, size.width * zoom)
                y = if (zoom == 1f) 0f else (y + pan.y).coerceIn(-size.height * zoom, size.height * zoom)
            }
        }, contentAlignment = Alignment.Center) {
            Image(bitmap.asImageBitmap(), contentDescription = "실제 파일",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = x; translationY = y
                })
        }
        TextButton(onClick = { zoom = 1f; x = 0f; y = 0f }) { Text("확대 초기화") }
    }
}

@Composable
private fun PdfPreview(bytes: ByteArray) {
    val context = LocalContext.current
    var index by remember(bytes) { mutableIntStateOf(0) }
    var total by remember(bytes) { mutableIntStateOf(0) }
    var bitmap by remember(bytes) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(bytes) { mutableStateOf(false) }
    LaunchedEffect(bytes, index) {
        bitmap = null; failure = false
        try {
            val result = withContext(Dispatchers.IO) {
                val file = File.createTempFile("dominion-pdf-", ".pdf", context.cacheDir)
                try {
                    file.writeBytes(bytes)
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
                    renderer.use {
                        val count = it.pageCount
                        require(index in 0 until count)
                        it.openPage(index).use { page ->
                            val scale = minOf(2f, 2048f / maxOf(page.width, page.height))
                            val image = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            image.eraseColor(android.graphics.Color.WHITE)
                            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            count to image
                        }
                    }
                } finally { file.delete() }
            }
            total = result.first; bitmap = result.second
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure = true }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (failure) Text("PDF 열기 실패 · 암호화 또는 손상 여부를 확인하세요.")
            else bitmap?.let { ZoomBitmap(it) } ?: CircularProgressIndicator()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = index > 0, onClick = { index-- }) { Text("이전") }
            Text((index + 1).toString() + "/" + total)
            TextButton(enabled = index + 1 < total, onClick = { index++ }) { Text("다음") }
        }
    }
}

@Composable
private fun MediaPreview(bytes: ByteArray, mime: String) {
    val context = LocalContext.current
    var file by remember(bytes) { mutableStateOf<File?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(bytes) {
        try {
            file = withContext(Dispatchers.IO) {
                val extension = if (mime.startsWith("audio/")) ".audio" else ".video"
                val temp = File.createTempFile("dominion-media-", extension, context.cacheDir)
                try { temp.writeBytes(bytes); temp } catch (e: Exception) { temp.delete(); throw e }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    DisposableEffect(file) {
        val owned = file
        onDispose {
            view?.stopPlayback()
            owned?.delete()
        }
    }
    if (failed) Text("지원하지 않는 미디어 또는 재생 오류")
    else file?.let { source ->
        AndroidView(factory = { ctx ->
            VideoView(ctx).apply {
                view = this
                val controls = android.widget.MediaController(ctx)
                controls.setAnchorView(this)
                setMediaController(controls)
                setOnErrorListener { _, _, _ -> failed = true; true }
                setVideoPath(source.absolutePath)
                setOnPreparedListener { seekTo(1); controls.show(0) }
            }
        }, modifier = Modifier.fillMaxSize())
    } ?: CircularProgressIndicator()
}
