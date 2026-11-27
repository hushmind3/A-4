package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class VisualGestureTest {
    @Test fun requiresExactFrameAndBoundedCoordinates() {
        val hash = VisualGesture.digest("frame".toByteArray())
        assertNotNull(VisualGesture.parse("tap_point", "$hash:500:500"))
        assertNull(VisualGesture.parse("tap_point", "$hash:0:500"))
        assertNull(VisualGesture.parse("tap_point", "wrong:500:500"))
        assertNotNull(VisualGesture.parse("swipe_point", "$hash:500:800:500:200"))
        assertNull(VisualGesture.parse("swipe_point", "$hash:500:500:500:501"))
        val step=PhoneNavigationStep("tap_point","목록", "$hash:500:500")
        assertTrue(PhoneNavigationPlan.valid(PhoneNavigationPlan("org.example.app",
            listOf(step,PhoneNavigationStep("visual_inspect","화면")),2)))
        assertFalse(PhoneNavigationPlan.valid(PhoneNavigationPlan("org.example.app",
            listOf(step,step,PhoneNavigationStep("visual_inspect","화면")),2)))
    }
}
