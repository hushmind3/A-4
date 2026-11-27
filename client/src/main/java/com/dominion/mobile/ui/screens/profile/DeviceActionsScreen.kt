package com.dominion.mobile.ui.screens.profile

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.platform.DeviceActions
import com.dominion.mobile.platform.DeviceContactPicker
import com.dominion.mobile.ui.components.*
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DeviceActionsScreen(state: AppState, roomId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalDominionPalette.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var choosingEmail by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val latest = roomId?.let { state.conversation(it) }?.messages?.lastOrNull {
        it.sender == MessageSender.ASSISTANT && it.status == MessageStatus.SENT && it.text.isNotBlank()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val isEmail = choosingEmail
            val uri = requireNotNull(result.data?.data)
            scope.launch {
                try {
                    val value = DeviceContactPicker.read(context, uri, isEmail)
                    if (isEmail) email = value else phone = value
                    error = ""
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "선택한 연락처를 읽지 못했습니다. 직접 입력할 수 있습니다." }
            }
        }
    }
    fun selectContact(isEmail: Boolean) {
        choosingEmail = isEmail
        runCatching { pick.launch(DeviceContactPicker.intent(isEmail)) }
            .onFailure { error = "연락처 선택 앱을 열지 못했습니다." }
    }
    fun perform(action: String) {
        error = ""
        val opened = runCatching {
            when (action) {
                "전화" -> DeviceActions.dialContact(context, phone)
                "문자" -> DeviceActions.composeSms(context, phone, body)
                "이메일" -> DeviceActions.composeEmail(context, email, subject, body)
                "지도" -> DeviceActions.map(context, location)
                else -> false
            }
        }.getOrDefault(false)
        if (!opened) error = "입력값 또는 해당 앱을 확인하세요."
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(p.screen)) {
        ScreenTopBar(title = "휴대폰 기능", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("연락처에서 선택한 번호·이메일만 기기에서 사용합니다. 전화나 메시지는 해당 앱에서 직접 확인하고 실행합니다.", color = p.textSecondary)
            Text("전화 · 문자", style = MaterialTheme.typography.titleMedium)
            AppTextField(phone, { phone = it.take(40) }, placeholder = "전화번호")
            Row {
                OutlinedButton(onClick = { selectContact(false) }) { Text("연락처 선택") }
                TextButton(enabled = phone.isNotBlank(), onClick = { confirm = "전화" }) { Text("전화") }
                TextButton(enabled = phone.isNotBlank(), onClick = { confirm = "문자" }) { Text("문자") }
            }
            HorizontalDivider()
            Text("이메일", style = MaterialTheme.typography.titleMedium)
            AppTextField(email, { email = it.take(254) }, placeholder = "이메일 주소")
            Row {
                OutlinedButton(onClick = { selectContact(true) }) { Text("연락처 선택") }
                TextButton(enabled = email.isNotBlank(), onClick = { confirm = "이메일" }) { Text("이메일 작성") }
            }
            AppTextField(subject, { subject = it.take(200) }, placeholder = "제목 (이메일)")
            AppTextField(body, { body = it.take(10000) }, placeholder = "문자·이메일 내용", singleLine = false, maxLines = 8)
            if (latest != null) OutlinedButton(onClick = { body = latest.text.take(10000) }) { Text("현재 대화의 마지막 AI 답변 가져오기") }
            HorizontalDivider()
            Text("길찾기", style = MaterialTheme.typography.titleMedium)
            AppTextField(location, { location = it.take(250) }, placeholder = "장소·주소")
            OutlinedButton(enabled = location.isNotBlank(), onClick = { perform("지도") }) { Text("지도 앱에서 검색") }
            HorizontalDivider()
            Text("휴대폰 앱", style = MaterialTheme.typography.titleMedium)
            listOf("카카오톡" to "com.kakao.talk", "네이버 지도" to "com.nhn.android.nmap",
                "카카오맵" to "net.daum.android.map", "Gmail" to "com.google.android.gm").forEach { (name, pkg) ->
                OutlinedButton(onClick = {
                    if (!DeviceActions.openInstalledApp(context, pkg)) error = "$name 앱을 열 수 없습니다."
                    else error = ""
                }) { Text(name + " 열기") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
    confirm?.let { action ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("$action 앱으로 이동") },
            text = { Text((if (action == "이메일") email else phone) + "\n" +
                (if (action == "전화") "전화 앱에서 직접 발신합니다." else body.take(500)) +
                "\n아직 발신·발송된 것이 아닙니다.") },
            confirmButton = { TextButton(onClick = { confirm = null; perform(action) }) { Text("열기") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } })
    }
}
