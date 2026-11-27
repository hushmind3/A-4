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
}
