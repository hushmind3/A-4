package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class PhoneNavigationPlanTest {
    private val json = """{"schema":"dominion-phone-plan/v1","app":"com.nhn.android.nmap","steps":[{"action":"click","label":"검색"},{"action":"search_text","label":"검색어 입력","value":"서울역"},{"action":"expect","label":"서울역"}]}"""
    @Test fun onlyExplicitMapNavigation() {
        val plan = PhoneNavigationPlan.parse(json)
        assertEquals(3,plan.steps.size)
        assertEquals("서울역",plan.steps[1].value)
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("com.nhn.android.nmap","com.android.settings")))
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("\"검색\"","\"결제\"")))
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("\"expect\"","\"click\"")))
    }

    @org.junit.Test fun publicResultCompletionIsScopedToSelectedReadOnlyApp() {
        val app="com.coupang.mobile"
        val report=PhoneNavigationPlan(app,listOf(PhoneNavigationStep("report_result","청소기 19900원")),2)
        org.junit.Assert.assertTrue(PhonePublicResult.permits(report,"청소기 찾아줘"))
        org.junit.Assert.assertFalse(PhoneNavigationPlan.valid(report.copy(app="com.kakao.talk")))
        val order=PhoneNavigationPlan(app,listOf(PhoneNavigationStep("click","주문하기"),
            PhoneNavigationStep("inspect","화면")),2)
        org.junit.Assert.assertFalse(PhonePublicResult.permits(order,"청소기 찾아줘"))
        org.junit.Assert.assertNull(PhonePublicResult.text("010-1234-5678"))
    }
}
