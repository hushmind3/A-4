package com.dominion.mobile.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dominion.mobile.BuildConfig
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.tr

/** User chooses where to send the report. Never claim a support receipt. */
@Composable
fun SupportReportDialog(target: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    TextPromptDialog(
        title = tr(lang, "문제 신고 작성", "Write an issue report"),
        label = tr(lang, "문제 내용 (대화·개인정보는 자동 첨부하지 않음)", "Describe the issue (no automatic transcript or personal data)"),
        placeholder = tr(lang, "어떤 문제가 발생했나요?", "What happened?"),
        confirmLabel = tr(lang, "신고 내용 공유", "Share report"),
        singleLine = false,
        onConfirm = { description ->
            if (description.isNotBlank()) {
                val body = "DOMINION Android " + BuildConfig.VERSION_NAME +
                    "\nArea: " + target + "\n\n" + description.trim().take(12000)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "DOMINION Android issue report")
                    .putExtra(Intent.EXTRA_TEXT, body)
                runCatching { context.startActivity(Intent.createChooser(send,
                    tr(lang, "보낼 앱 선택", "Choose where to send"))) }
                    .onSuccess { onDismiss() }
                    .onFailure { context.toast(tr(lang, "공유 앱을 열 수 없습니다.", "Unable to open sharing app.")) }
            }
        },
        onDismiss = onDismiss
    )
}
