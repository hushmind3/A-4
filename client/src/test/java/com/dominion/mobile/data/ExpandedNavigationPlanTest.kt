package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class ExpandedNavigationPlanTest {
    @Test fun navigationIsScopedByAppAndAction() {
        val json="""{"schema":"dominion-phone-plan/v1","app":"com.google.android.calendar","steps":[{"action":"click","label":"오늘"},{"action":"expect","label":"오늘"}]}"""
        assertNotNull(PhoneNavigationPlan.fromMessage(json))
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("com.google.android.calendar","com.google.android.youtube")))
        assertNull(PhoneNavigationPlan.fromMessage(json.replace("\"오늘\"","\"삭제\"")))
        assertTrue("검색" in PhoneNavigationPlan.labels("com.google.android.youtube","search_text"))
        assertFalse("구독" in PhoneNavigationPlan.labels("com.google.android.youtube","search_text"))
    }
}
