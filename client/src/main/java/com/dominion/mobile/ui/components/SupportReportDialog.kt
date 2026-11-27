package com.dominion.mobile.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.BuildConfig
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.tr

/** The repository's real GitHub Issues channel; submission stays with the signed-in user. */
@Composable
fun SupportReportDialog(target: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(false) }
    val title = "DOMINION Android issue report"
    fun body() = "DOMINION Android " + BuildConfig.VERSION_NAME +
        "\nArea: " + target.filterNot(Char::isISOControl).take(120) + "\n\n" + description.trim()
    fun openIssue() {
        val uri = Uri.parse("https://github.com/hushmind3/A-4/issues/new").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body().take(1400))
            .build()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onSuccess { opened = true; error = "" }
            .onFailure { error = tr(lang,"신고 페이지를 열 수 없습니다.","Unable to open issue page.") }
    }
    fun shareReport() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, body())
        runCatching { context.startActivity(Intent.createChooser(send,
            tr(lang, "보낼 앱 선택", "Choose where to send"))) }
            .onFailure { error = tr(lang,"공유 앱을 열 수 없습니다.","Unable to open sharing app.") }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr(lang,"문제 신고 작성","Write an issue report")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(lang,"A-4의 실제 GitHub 이슈 접수 페이지에서 내용을 검토하고 제출할 수 있습니다. 저장소 접근 권한과 GitHub 로그인이 필요합니다.",
                    "Review and submit on A-4's actual GitHub issue page. Repository access and GitHub sign-in are required."))
                OutlinedTextField(description, { description = it.take(12000) }, label = {
                    Text(tr(lang,"문제 내용","Description"))
                }, minLines = 3, maxLines = 6)
                Text(tr(lang,"대화·개인정보·인증 정보는 자동 첨부하지 않습니다. 긴 내용은 전체 내용 공유를 사용하세요.",
                    "No transcripts or credentials are attached automatically. Use share for long reports."),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = description.isNotBlank(), onClick = ::shareReport) {
                    Text(tr(lang,"전체 신고 내용 공유","Share complete report"))
                }
                if (opened) Text(tr(lang,"접수 페이지를 열었습니다. 제출 완료 여부는 GitHub에서 확인하세요.",
                    "Issue page opened. Confirm actual submission in GitHub."))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = description.isNotBlank(), onClick = ::openIssue) {
            Text(tr(lang,"신고 접수 페이지 열기","Open issue submission"))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang,"닫기","Close")) } })
}
