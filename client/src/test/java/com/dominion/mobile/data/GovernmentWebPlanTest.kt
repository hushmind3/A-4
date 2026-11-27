package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class GovernmentWebPlanTest {
    private val elements=listOf(
        GovernmentElement("e0","이름","input:text",required=true),
        GovernmentElement("e1","신청 구분","select:select"),
        GovernmentElement("e2","제출","button:submit")
    )

    @Test fun acceptsBoundedRefPlanAndDocumentNumber(){
        val plan=GovernmentWebPlan.parse("gov","""
            {"schema":"dominion-gov-web/v1","actions":[
              {"action":"fill","ref":"e0","value":"홍길동"},
              {"action":"select","ref":"e1","value":"본인"},
              {"action":"submit","ref":"e2"}],"receipt_hint":"접수번호"}
        """.trimIndent(),elements)
        assertEquals(3,plan.actions.size)
        assertTrue(plan.hasSubmit)
        val check=GovernmentSubmission.inspect("민원이 접수되었습니다. 접수번호: 2026-12345")
        assertTrue(check.submitted)
        assertEquals("2026-12345",check.receiptNumber)
    }

    @Test fun rejectsCredentialsRawCodeAndUnknownElements(){
        assertTrue(runCatching{GovernmentWebPlan.parse("gov",
            """{"schema":"dominion-gov-web/v1","actions":[{"action":"fill","ref":"e9","value":"x"}]}""",
            elements+GovernmentElement("e9","비밀번호","input:password"))}.isFailure)
        assertTrue(runCatching{GovernmentWebPlan.parse("gov",
            """{"schema":"dominion-gov-web/v1","actions":[{"action":"click","ref":"e2"}]}""",
            elements)}.isFailure)
    }

    @Test fun noReceiptIsNotTreatedAsSubmissionSuccess(){
        val check=GovernmentSubmission.inspect("입력 내용을 확인해 주세요.")
        assertFalse(check.submitted)
        assertNull(check.receiptNumber)
    }
}
