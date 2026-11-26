package com.dominion.mobile.data

import com.dominion.mobile.data.model.MemoryItem
import com.dominion.mobile.data.remote.EngineMemory
import com.dominion.mobile.data.remote.MemoryWrite
import com.dominion.mobile.data.remote.mergeMemories
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class EngineMemoryTest {
    @Test fun explicitOwnerMemoryUsesSelectedAgentAndDoesNotShareWithExternalChannels() {
        val request = MemoryWrite.from(MemoryItem("m1", "agent1", "한국어 기억"))
        val json = Gson().toJsonTree(request).asJsonObject
        assertEquals("agent1", json.get("agentId").asString)
        assertEquals("owner", json.get("source").asString)
        assertTrue(json.get("shareWithRooms").asBoolean)
        assertFalse(json.get("shareWithChannels").asBoolean)
        val row = Gson().fromJson(json, EngineMemory::class.java)
        assertTrue(request.matches(row))
        assertFalse(request.matches(row.copy(agentId = "different")))
    }
    @Test fun reconciliationPreservesLocalRecordsAndRemovesStaleServerCache() {
        val local = MemoryItem("local", "a", "offline")
        val old = MemoryItem("old", "a", "removed", serverBacked = true)
        val confirmed = local.copy(serverBacked = true)
        assertEquals(listOf(local), mergeMemories(listOf(local, old), emptyList()))
        assertEquals(listOf(confirmed), mergeMemories(listOf(local, old), listOf(confirmed)))
    }
}
