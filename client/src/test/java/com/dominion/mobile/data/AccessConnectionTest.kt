package com.dominion.mobile.data

import com.dominion.mobile.data.local.EngineConnection
import org.junit.Assert.*
import org.junit.Test

class AccessConnectionTest {
    @Test fun optionalAccessRequiresCompletePairAndNeverAppearsInString() {
        val connection = EngineConnection.parse("https://engine.example.org", "owner-token", "client-id", "client-secret")
        assertEquals("https://engine.example.org/", connection.origin)
        assertEquals("client-id", connection.accessClientId)
        assertFalse(connection.toString().contains("client-secret"))
        assertFalse(connection.toString().contains("owner-token"))
        assertTrue(runCatching { EngineConnection.parse(connection.origin, "owner-token", "client-id", null) }.isFailure)
        assertTrue(runCatching { EngineConnection.parse(connection.origin, "owner-token", "id\nx", "secret") }.isFailure)
    }
}
