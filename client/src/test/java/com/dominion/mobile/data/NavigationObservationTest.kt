package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class NavigationObservationTest {
    private val observation = """{"schema":"dominion-phone-plan/v2","app":"org.example.reader","steps":[{"action":"inspect","label":"화면"}]}"""
    @Test fun observationIsExplicitAndTerminal() {
        val plan = PhoneNavigationPlan.parse(observation)
        assertTrue(PhoneNavigationPlan.valid(plan))
        assertNull(PhoneNavigationPlan.fromMessage(observation.replace("/v2", "/v1")))
        assertNull(PhoneNavigationPlan.fromMessage(observation.replace("화면", "비밀번호")))
        assertFalse(PhoneNavigationPlan.valid(plan.copy(steps = plan.steps + PhoneNavigationStep("expect", "책"))))
    }
}
