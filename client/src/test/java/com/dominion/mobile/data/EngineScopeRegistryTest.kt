package com.dominion.mobile.data
import com.dominion.mobile.data.local.*
import org.junit.Assert.*
import org.junit.Test
class EngineScopeRegistryTest {
 @Test fun preservesCurrentLegacyCacheButIsolatesNewCredentials(){
  val a=EngineConnection.parse("https://host.example","a")
  val b=EngineConnection.parse("https://host.example","b")
  val (first,old)=EngineScopeRegistry().resolve(a,true)
  val (second,next)=first.resolve(b,false)
  assertNotEquals(old,next)
  assertEquals(old,second.resolve(a,false).second)
  assertEquals(next,second.resolve(b,true).second)
  assertTrue(second.entries.keys.none{it=="a"||it=="b"})
 }
}
