package com.dominion.mobile.data

import com.dominion.mobile.data.remote.*
import com.dominion.mobile.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ExistingToolContractTest {
    @Test fun approvalCannotCrossRoom() {
        val approval = EngineApproval("approval", "room:one", "workspace_write",
            JsonParser.parseString("{\"path\":\"note.txt\"}"), "digest", "pending")
        assertTrue(approval.actionableIn("room:one"))
        assertFalse(approval.actionableIn("room:two"))
        assertFalse(approval.copy(argsDigest = null).actionableIn("room:one"))
        assertFalse(approval.copy(status = "approved").actionableIn("room:one"))
    }
    @Test fun nativeHistoryPreservesAttachmentAndClientId() {
        val attachment = EngineAttachment("5f7d83ec-b219-4ce2-94fe-c2b1c9d27f38", "one", "사진.jpg", "image/jpeg", 42, "image")
        val row = NativeRoomMessage("server", "user", "", "2026-11-27T00:00:00Z",
            NativeRoomMetadata("client", listOf(attachment)))
        val message = requireNotNull(row.toDomain("one"))
        assertEquals(CardKind.IMAGE, message.card?.kind)
        assertEquals(listOf(attachment.id), message.attachmentIds)
        assertEquals("client", message.clientMessageId)
        assertNull(row.toDomain("two"))
        assertNull(row.copy(role = "tool").toDomain("one"))
    }
    @Test fun existingRoomSubmissionCarriesAttachmentIds() {
        val json = Gson().toJsonTree(RoomInput("사진 확인", "client", "ko", listOf("attachment"))).asJsonObject
        assertEquals("attachment", json.getAsJsonArray("attachmentIds")[0].asString)
        assertEquals("ko", json.get("reasoningLanguage").asString)
    }
}
