package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class PhoneReplanBudgetTest {
 @Test fun rejectsLoopAndCrossApp(){
  val hash=VisualGesture.digest("image".toByteArray())
  val plan=PhoneNavigationPlan("org.example.app",listOf(PhoneNavigationStep("tap_point","메뉴","$hash:500:500"),PhoneNavigationStep("visual_inspect","화면")),2)
  val b=PhoneReplanBudget(2)
  assertTrue(b.admit(hash,plan,plan.app))
  assertFalse(b.admit(hash,plan,plan.app))
  assertFalse(b.admit(hash,plan,"org.example.other"))
  assertFalse(b.admit(VisualGesture.digest("other".toByteArray()),plan,plan.app))
 }
}
