package com.dominion.mobile.data.remote
import com.dominion.mobile.data.local.EngineConnection
import org.junit.Assert.*
import org.junit.Test
class CloudEngineLinksTest {
 @Test fun restoresOnlyExplicitAccountAndPausesExactCredential(){
  val a=EngineConnection.parse("https://engine.example","owner-a")
  val b=EngineConnection.parse("https://engine.example","owner-b")
  val links=CloudEngineLinks().bind("google-a",a).bind("google-b",b)
  assertNull(links.forAccount("google-c"))
  assertEquals("owner-a",links.forAccount("google-a")!!.connection.ownerToken)
  val paused=links.pause(a)
  assertFalse(paused.forAccount("google-a")!!.autoReconnect)
  assertTrue(paused.forAccount("google-b")!!.autoReconnect)
  assertNull(paused.forget("google-a").forAccount("google-a"))
 }
}
