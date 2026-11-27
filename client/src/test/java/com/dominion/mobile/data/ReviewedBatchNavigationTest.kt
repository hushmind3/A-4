package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class ReviewedBatchNavigationTest {
    @Test fun onlyKnownV1PlansCanBatch() {
        val original = PhoneNavigationPlan("com.google.android.youtube",
            listOf(PhoneNavigationStep("click","홈"),PhoneNavigationStep("expect","홈")))
        assertTrue(PhoneNavigationPlan.permitsReviewedBatch(original))
        assertFalse(PhoneNavigationPlan.permitsReviewedBatch(original.copy(version=2)))
        assertFalse(PhoneNavigationPlan.permitsReviewedBatch(original.copy(
            steps=listOf(PhoneNavigationStep("click","구매"),PhoneNavigationStep("expect","완료")))))
        assertFalse(PhoneNavigationPlan.permitsReviewedBatch(original.copy(
            app="org.example.reader",version=2)))
    }
}
