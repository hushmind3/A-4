package com.dominion.mobile.data.remote
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class EngineChangeTest {
 private fun hint(json:String)=engineChange(JsonParser.parseString(json).asJsonObject)
 @Test fun onlyAuthoritativeInvalidationTargets(){
  assertEquals(EngineChange("room","s"),hint("""{"type":"record","kind":"job","record":{"sessionId":"s","input":{"roomId":"room"},"status":"completed"}}"""))
  assertEquals(EngineChange(memory=true),hint("""{"type":"record","kind":"memory","record":{}}"""))
  assertEquals(EngineChange("room","s"),hint("""{"type":"message","message":{"session_id":"s","metadata":{"roomId":"room"},"content":"untrusted"}}"""))
  assertNull(hint("""{"type":"token","text":"unrelated"}"""))
 }
}
