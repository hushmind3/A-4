package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class NavigationScrollTest {
    private val json = """{"schema":"dominion-phone-plan/v1","app":"com.google.android.youtube","steps":[{"action":"scroll_forward","label":"화면"},{"action":"scroll_backward","label":"Screen"},{"action":"expect","label":"구독"}]}"""
    @Test fun acceptsOnlyBoundedScrollWithFinalVerification() {
        val plan = PhoneNavigationPlan.parse(json)
        assertTrue(PhoneNavigationPlan.valid(plan))
        assertEquals("scroll_forward", plan.steps.first().action)
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("화면", "결제")))
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("expect", "scroll_forward")))
        assertFalse(PhoneNavigationPlan.valid(plan.copy(steps = listOf(
            PhoneNavigationStep("search_text", "검색"), plan.steps.last()))))
        assertFalse(PhoneNavigationPlan.valid(plan.copy(steps = listOf(
            PhoneNavigationStep("scroll_forward", "화면", "unreviewed"), plan.steps.last()))))
    }
    @Test fun modelPromptMatchesActualAppLabels() {
        val prompt = PhoneNavigationPlan.prompt("다운로드 목록 확인")
        assertTrue(prompt.contains("com.google.android.apps.nbu.files:"))
        assertTrue(prompt.contains("Downloads"))
        assertTrue(prompt.contains("scroll_forward"))
    }
}
