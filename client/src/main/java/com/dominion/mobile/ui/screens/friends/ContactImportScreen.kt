package com.dominion.mobile.ui.screens.friends

import android.app.Activity
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.LocalContactCard
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.ContactQr
import com.dominion.mobile.platform.DeviceContactPicker
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.components.*
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.*

@Composable
fun ContactImportScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalDominionPalette.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var choosingEmail by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    fun apply(card: LocalContactCard) {
        name = card.name
        phone = card.phone.orEmpty()
        email = card.email.orEmpty()
        notice = "연락처를 읽었습니다. 내용 검토 후 저장하세요."
    }
    fun importCode(code: String) {
        runCatching { LocalContactCard.decode(code.trim()) }
            .onSuccess(::apply).onFailure { notice = "지원하는 연락처 QR이 아닙니다. 링크는 자동으로 열지 않습니다." }
    }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val isEmail = choosingEmail
            busy = true
            scope.launch {
                try { apply(DeviceContactPicker.readNamed(context, uri, isEmail)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { notice = "연락처 읽기 실패 · 직접 입력할 수 있습니다." }
                finally { busy = false }
            }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try { importCode(ContactQr.readImage(context, uri)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { notice = "사진에서 QR 코드를 읽지 못했습니다." }
                finally { busy = false }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            busy = true
            scope.launch {
                try { importCode(withContext(Dispatchers.Default) { ContactQr.decode(bitmap) }) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { notice = "QR이 선명하게 나오도록 다시 촬영하세요." }
                finally { busy = false; bitmap.recycle() }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { camera.launch(null) }.onFailure { notice = "카메라 실행 실패" }
        else notice = "카메라 권한이 필요합니다."
    }
    fun pick(isEmail: Boolean) {
        choosingEmail = isEmail
        runCatching { contact.launch(DeviceContactPicker.intent(isEmail)) }.onFailure { notice = "주소록 선택 실패" }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "친구·연락처 추가", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("선택한 연락처는 이 기기에만 저장됩니다. 서버 계정 검색·친구 수락은 아직 본체에서 지원하지 않습니다.", color = p.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { pick(false) }) { Text("주소록 전화번호") }
                OutlinedButton(enabled = !busy, onClick = { pick(true) }) { Text("주소록 이메일") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED)
                        runCatching { camera.launch(null) }.onFailure { notice = "카메라 실행 실패" }
                    else permission.launch(Manifest.permission.CAMERA)
                }) { Text("연락처 QR 촬영") }
                OutlinedButton(enabled = !busy, onClick = { photo.launch("image/*") }) { Text("QR 사진 선택") }
            }
            AppTextField(payload, { payload = it.take(2048) }, placeholder = "공유받은 연락처 코드 붙여넣기", singleLine = false, maxLines = 2)
            TextButton(enabled = payload.isNotBlank() && !busy, onClick = { importCode(payload) }) { Text("코드 읽기") }
            HorizontalDivider()
            AppTextField(name, { name = it.take(80) }, label = "이름")
            AppTextField(phone, { phone = it.take(40) }, label = "전화번호")
            AppTextField(email, { email = it.take(254) }, label = "이메일")
            Button(enabled = !busy && name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    if (vm.saveLocalContact(LocalContactCard(name, phone, email))) {
                        notice = "연락처를 기기에 저장했습니다."
                        name = ""; phone = ""; email = ""; payload = ""
                    } else notice = "이름·번호·이메일 형식을 확인하세요."
                }) { Text("연락처 저장") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (notice.isNotBlank()) Text(notice, color = p.textSecondary)
            HorizontalDivider()
            Text("기존 아이디 기록", style = MaterialTheme.typography.titleSmall)
            AppTextField(handle, { handle = it.take(80) }, placeholder = "미확인 아이디 메모")
            TextButton(enabled = handle.isNotBlank(), onClick = {
                vm.addFriendByHandle(handle)
                handle = ""
                notice = "아이디를 기기 메모로 저장했습니다. 서버 연결은 아닙니다."
            }) { Text("아이디 메모 저장") }
            TextButton(onClick = { ContactQr.shareProject(context) }) { Text("DOMINION 프로젝트 링크 공유") }
        }
    }
}

@Composable
fun ContactInviteScreen(state: AppState, onBack: () -> Unit) {
    val p = LocalDominionPalette.current
    val context = LocalContext.current
    var name by remember { mutableStateOf(state.account.name.take(80)) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val qr = remember(code) { code?.let { runCatching { ContactQr.bitmap(it) }.getOrNull() } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "친구 초대·연락처 공유", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("프로젝트 링크를 공유하거나 직접 입력한 연락처를 QR로 교환할 수 있습니다. 계정 권한·친구 수락·초대 보상은 부여되지 않습니다.")
            TextButton(onClick = { ContactQr.shareProject(context) }) { Text("공식 프로젝트 링크 공유") }
            HorizontalDivider()
            Text("내 연락처 QR", style = MaterialTheme.typography.titleMedium)
            AppTextField(name, { name = it.take(80); code = null }, label = "공유할 이름")
            AppTextField(phone, { phone = it.take(40); code = null }, label = "공유할 전화번호 (선택)")
            AppTextField(email, { email = it.take(254); code = null }, label = "공유할 이메일 (선택)")
            Button(onClick = {
                runCatching { LocalContactCard(name, phone, email).encode() }
                    .onSuccess { code = it; error = "" }
                    .onFailure { code = null; error = "이름과 유효한 전화번호 또는 이메일을 입력하세요." }
            }) { Text("연락처 QR 생성") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            qr?.let { image ->
                Image(image.asImageBitmap(), "연락처 QR", modifier = Modifier.size(260.dp))
                Text("QR에는 직접 입력한 연락처가 포함됩니다. 공유 상대를 확인하세요.", color = p.textSecondary)
                TextButton(onClick = { ContactQr.share(context, requireNotNull(code)) }) { Text("연락처 코드 공유") }
                SelectionContainer { Text(code.orEmpty(), style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { code = null }) { Text("QR 닫기") }
            }
        }
    }
}
