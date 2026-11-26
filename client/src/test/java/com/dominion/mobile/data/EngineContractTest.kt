package com.dominion.mobile.data

import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.remote.RoomCreate
import com.dominion.mobile.data.remote.RoomInput
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class EngineContractTest {
    @Test fun engineOriginIsNormalizedAndCredentialIsNotPrinted() {
        val connection = EngineConnection.parse("https://example.com", "owner-secret")
        assertEquals("https://example.com/", connection.origin)
        assertFalse(connection.toString().contains("owner-secret"))
    }

    @Test fun cloudBearerAndUnsafeOriginsAreRejected() {
        for (origin in listOf("http://example.com", "https://example.com/path",
            "https://user@example.com", "https://example.com/?q=1",
            "https://dominion-cloud.dominion-hushmind.workers.dev")) {
            assertTrue(runCatching { EngineConnection.parse(origin, "owner-secret") }.isFailure)
        }
        assertTrue(runCatching { EngineConnection.parse("https://example.com", "dapp_test") }.isFailure)
    }

    @Test fun nativeRoomSubmissionUsesStableIdAndKoreanReasoning() {
        val json = Gson().toJsonTree(RoomInput("일정 확인", "client-123", "ko")).asJsonObject
        assertEquals("client-123", json.get("clientMessageId").asString)
        assertEquals("ko", json.get("reasoningLanguage").asString)
        assertEquals("일정 확인", json.get("input").asString)
    }

    @Test fun newRoomHasToolsByDefault() {
        val json = Gson().toJsonTree(RoomCreate("room-123", "새 대화", listOf("agent-1"))).asJsonObject
        assertTrue(json.get("toolsEnabled").asBoolean)
        assertTrue(json.get("autoModels").asBoolean)
    }
}
