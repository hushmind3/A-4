package com.dominion.mobile.data

import com.dominion.mobile.data.remote.TaskReceipt
import java.text.DateFormat
import java.util.Date

/** User-initiated export of the already observed task result. Never adds
 * credentials, account identifiers or unobserved completion claims. */
object TaskExport {
    fun filename(title: String): String {
        val cleaned = title.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ").take(80).ifBlank { "dominion-task" }
        return cleaned + ".txt"
    }

    fun document(title: String, receipt: TaskReceipt): String = buildString {
        append("DOMINION 작업 결과\n")
        append("작업: ").append(title.trim().take(300)).append('\n')
        append("상태: ").append(receipt.status).append('\n')
        append("작업 ID: ").append(receipt.jobId).append('\n')
        append("조회 시각: ").append(DateFormat.getDateTimeInstance().format(Date(receipt.checkedAt))).append('\n')
        receipt.reason?.takeIf { it.isNotBlank() }?.let { append("종료 사유: ").append(it).append('\n') }
        receipt.error?.takeIf { it.isNotBlank() }?.let { append("오류: ").append(it).append('\n') }
        append('\n')
        if (receipt.outputs.isEmpty()) {
            append("저장된 AI 결과가 없습니다.\n")
        } else {
            receipt.outputs.forEachIndexed { index, output ->
                append("== 결과 ").append(index + 1).append(" ==\n")
                append("에이전트: ").append(output.name ?: output.agentId ?: "알 수 없음").append('\n')
                append("응답 ID: ").append(output.messageId.orEmpty()).append('\n')
                append(output.evidenceCount?.let { "실행 근거: " + it + "개\n" } ?: "실행 근거 수: 미제공\n")
                append('\n').append(output.content.orEmpty().trim()).append("\n\n")
            }
        }
        val evidence = receipt.cloudEvidence.orEmpty()
        if (evidence.isNotEmpty()) {
            append("== 도구 실행 기록 ==\n")
            evidence.forEach { row ->
                append(row.step).append(". ").append(row.tool).append(" · ").append(row.status)
                append(" · ").append(DateFormat.getDateTimeInstance()
                    .format(Date(row.observed_at * 1000))).append('\n')
                append(row.source).append('\n')
                append(row.observation.take(8_000)).append("\n\n")
            }
        }
        append("이 파일은 기기에 이미 저장된 관측 결과입니다. 외부 기관의 접수·결제·전송 완료를 자동으로 증명하지 않습니다.\n")
    }
}
