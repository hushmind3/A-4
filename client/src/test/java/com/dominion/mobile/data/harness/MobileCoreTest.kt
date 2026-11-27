package com.dominion.mobile.data.harness
import com.dominion.mobile.data.model.MemoryItem
import com.google.gson.JsonObject
import com.dominion.mobile.data.remote.CloudChatMessage
import com.dominion.mobile.data.remote.CloudCompletionRequest
import org.junit.Assert.*
import org.junit.Test
class MobileCoreTest{
 @Test fun koreanMemoryRankingRespectsPersona(){
  val a=MemoryItem("a","p","서울 여행 일정",retrievalEligible=true)
  val b=MemoryItem("b","other","서울 여행 일정",retrievalEligible=true)
  val c=MemoryItem("c","p","서울 여행 일정",serverBacked=true)
  assertEquals(listOf("a"),MobileCore.retrieve(listOf(a,b,c),"p","서울여행").map{it.id})
 }
 @Test fun boundedToolHistoryKeepsSystemAndFitsCloudProtocol(){
  val longContext=listOf(CloudChatMessage("system","Local rules"))+
   (1..6).flatMap{listOf(CloudChatMessage("user","previous request $it"),
     CloudChatMessage("assistant","previous answer $it"))}+
   CloudChatMessage("user","Current device task")
  val bounded=MobileCore.compact(longContext)
  assertEquals("system",bounded.first().role)
  assertEquals("Current device task",bounded.last().content)
  assertTrue(bounded.size<=12)
  CloudCompletionRequest("dominion/free-auto",bounded).validated()
  assertEquals(6,MobileCore.MAX_READ_STEPS)
 }
 @Test fun originalRequestSurvivesToolContextCompaction(){
  val request=CloudChatMessage("user","Find and verify the requested information")
  val messages=listOf(CloudChatMessage("system","Rules"),request)+
   (1..12).flatMap{listOf(CloudChatMessage("assistant","tool $it"),CloudChatMessage("user","observation $it"))}
  val result=MobileCore.compact(messages,request)
  assertTrue(result.any{it===request})
  assertEquals("observation 12",result.last().content)
  CloudCompletionRequest("dominion/free-auto",result).validated()
 }
 @Test fun validatesNewNativeToolContracts(){
  assertEquals(0,MobileCore.validate("battery.status",JsonObject()).size())
  val mail=JsonObject().apply{
   addProperty("address","person@example.com")
   addProperty("subject","회의")
   addProperty("body","시간 확인")
  }
  assertEquals("person@example.com",MobileCore.validate("email.draft",mail)["address"].asString)
  assertTrue(runCatching{
   MobileCore.validate("email.draft",JsonObject().apply{addProperty("address","http://127.0.0.1")})
  }.isFailure)
 }
 @Test fun rejectsUnexpectedToolAndFields(){
  assertNull(MobileCore.action("""{"tool":"clock","args":{},"extra":1}"""))
  assertTrue(runCatching{MobileCore.validate("shell",JsonObject())}.isFailure)
  assertTrue(runCatching{MobileCore.validate("schedule.create",JsonObject())}.isFailure)
 }
 @Test fun workflowOfferAcceptsOnlyEmptyArguments(){
  assertTrue(MobileCore.validate("errand.plan",JsonObject()).entrySet().isEmpty())
  val forged=JsonObject().apply{addProperty("goal","untrusted model goal")}
  assertTrue(runCatching{MobileCore.validate("errand.plan",forged)}.isFailure)
 }
 @Test fun terminalStatusDistinguishesVerifiedPartialWork(){
  assertEquals("failed",MobileCore.terminalStatus(false,false,listOf("failed")))
  assertEquals("paused",MobileCore.terminalStatus(false,true,listOf("completed")))
  assertEquals("paused",MobileCore.terminalStatus(false,false,listOf("completed","failed")))
  assertEquals("completed",MobileCore.terminalStatus(false,false,listOf("failed","completed")))
  assertEquals("needs_review",MobileCore.terminalStatus(true,false,listOf("completed")))
  assertEquals("paused",MobileCore.terminalStatus(false,false,emptyList(),requiresExecution=true))
  assertEquals("completed",MobileCore.terminalStatus(false,false,emptyList()))
  assertEquals("completed",MobileCore.terminalStatus(false,false,listOf("completed"),requiresExecution=true))
  assertEquals("needs_review",MobileCore.terminalStatus(true,false,emptyList(),requiresExecution=true))
  assertTrue(MobileCore.toolLike("""{"tool":"clock","args":{},"extra":1}"""))
 }
 @Test fun validatesActualFutureSchedule(){
  val a=JsonObject().apply{addProperty("title","회의");addProperty("at","2027-01-01T09:00:00+09:00")}
  assertEquals("회의",MobileCore.validate("schedule.create",a,java.time.Instant.parse("2026-12-31T00:00:00Z").toEpochMilli())["title"].asString)
 }
}

class PhoneInquiryValidationTest {
  @Test fun actualPhoneInquiryRequiresKoreanBusinessNumberAndGoal(){
    val args=com.google.gson.JsonParser.parseString("""{"number":"02-1234-5678","goal":"영업시간을 확인해 주세요"}""").asJsonObject
    val checked=MobileCore.validate("phone.inquire",args)
    assertEquals("+82212345678",checked.get("number").asString)
    assertTrue(checked.get("goal").asString.contains("영업시간"))
    assertTrue(runCatching{MobileCore.validate("phone.inquire",
      com.google.gson.JsonParser.parseString("""{"number":"+1 555 0100","goal":"ask"}""").asJsonObject)}.isFailure)
  }
}
