package com.dominion.mobile.data.remote
import org.junit.Assert.*
import org.junit.Test
class MessengerContractTest {
 @Test fun validatesMembershipAndReadReceipts(){
  val thread=MessengerThread("t","group","group",listOf("me","a","b"))
  val row=MessengerMessage("m","a","hello",1,listOf("me"))
  val page=MessengerPage("me","t",listOf(row))
  assertEquals(page,page.verified("me",thread))
  assertTrue(runCatching{page.copy(messages=listOf(row.copy(readBy=listOf("other")))).verified("me",thread)}.isFailure)
  assertTrue(runCatching{page.copy(accountId="other").verified("me",thread)}.isFailure)
 }
 @Test fun validatesOperationPayload(){
  val intent=MobileIntent.create("me",MobileAction.GROUP_CREATE,"group",messenger=MessengerPayload(title="team",members=listOf("a","b")))
  assertNotNull(intent.messenger)
  assertTrue(runCatching{MobileIntent.create("me",MobileAction.GROUP_CREATE,"group",messenger=MessengerPayload(title="team",members=listOf("me","b")))}.isFailure)
 }
}
