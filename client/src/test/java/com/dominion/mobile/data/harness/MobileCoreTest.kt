package com.dominion.mobile.data.harness
import com.dominion.mobile.data.model.MemoryItem
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class MobileCoreTest{
 @Test fun koreanMemoryRankingRespectsPersona(){
  val a=MemoryItem("a","p","서울 여행 일정",retrievalEligible=true)
  val b=MemoryItem("b","other","서울 여행 일정",retrievalEligible=true)
  val c=MemoryItem("c","p","서울 여행 일정",serverBacked=true)
  assertEquals(listOf("a"),MobileCore.retrieve(listOf(a,b,c),"p","서울여행").map{it.id})
 }
 @Test fun rejectsUnexpectedToolAndFields(){
  assertNull(MobileCore.action("""{"tool":"clock","args":{},"extra":1}"""))
  assertTrue(runCatching{MobileCore.validate("shell",JsonObject())}.isFailure)
  assertTrue(runCatching{MobileCore.validate("schedule.create",JsonObject())}.isFailure)
 }
 @Test fun validatesActualFutureSchedule(){
  val a=JsonObject().apply{addProperty("title","회의");addProperty("at","2027-01-01T09:00:00+09:00")}
  assertEquals("회의",MobileCore.validate("schedule.create",a,java.time.Instant.parse("2026-12-31T00:00:00Z").toEpochMilli())["title"].asString)
 }
}
