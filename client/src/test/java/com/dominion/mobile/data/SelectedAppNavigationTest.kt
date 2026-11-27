package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class SelectedAppNavigationTest {
    private val plan = """{"schema":"dominion-phone-plan/v2","app":"org.example.reader","steps":[{"action":"click","label":"라이브러리"},{"action":"scroll_forward","label":"화면"},{"action":"expect","label":"책 목록"}]}"""
    @Test fun selectedAppSupportsNewNavigationWithoutChangingV1() {
        assertTrue(PhoneNavigationPlan.valid(PhoneNavigationPlan.parse(plan)))
        assertNull(PhoneNavigationPlan.fromMessage(plan.replace("/v2", "/v1")))
        assertNull(PhoneNavigationPlan.fromMessage(plan.replace("org.example.reader", "com.android.settings")))
        assertNull(PhoneNavigationPlan.fromMessage(plan.replace("라이브러리", "결제")))
    }
    @Test fun selectedAppPromptContainsOnlyChosenPackage() {
        val prompt = PhoneNavigationPlan.selectedAppPrompt("책 찾기", "org.example.reader", "Reader")
        assertTrue(prompt.contains("org.example.reader"))
        assertFalse(prompt.contains("com.google.android.youtube"))
    }
}
