package com.dominion.mobile.data.remote
import org.junit.Assert.*
import org.junit.Test
class SyncWindowTest {
 @Test fun busyRoomsDoNotStarveOtherRooms(){
  val ids=(1..30).map{"r"+it}
  var cursor=0
  val seen=mutableSetOf<String>()
  repeat(20){
   val window=SyncWindow.choose(ids,ids.take(8).toSet(),cursor)
   assertTrue(window.rooms.size<=8)
   assertEquals(window.rooms.distinct(),window.rooms)
   seen.addAll(window.rooms);cursor=window.nextCursor
  }
  assertEquals(ids.toSet(),seen)
 }
 @Test fun emptyAndSmallLists(){
  assertTrue(SyncWindow.choose(emptyList(),emptySet(),1).rooms.isEmpty())
  assertEquals(setOf("a","b"),SyncWindow.choose(listOf("a","b"),setOf("b"),-2).rooms.toSet())
 }
}
