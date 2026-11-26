package com.dominion.mobile.data

import com.dominion.mobile.data.remote.CLOUD_ORIGIN
import com.dominion.mobile.data.remote.CloudPairingProtocol
import org.junit.Assert.*
import org.junit.Test

class CloudPairingProtocolTest {
    @Test fun generatesValidUniqueBearerAndHash() {
        val token = CloudPairingProtocol.token()
        assertTrue(CloudPairingProtocol.validToken(token))
        assertNotEquals(token, CloudPairingProtocol.token())
        assertTrue(CloudPairingProtocol.hash(token).matches(Regex("[A-Za-z0-9_-]{43}")))
    }

    @Test fun acceptsExactApprovalUrl() {
        val id = "a".repeat(43)
        assertEquals("$CLOUD_ORIGIN/connect?code=$id",
            CloudPairingProtocol.approvalUrl(id, "$CLOUD_ORIGIN/connect?code=$id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignOrigin() {
        CloudPairingProtocol.approvalUrl("a".repeat(43), "https://example.test/connect")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExtraQuery() {
        val id = "a".repeat(43)
        CloudPairingProtocol.approvalUrl(id, "$CLOUD_ORIGIN/connect?code=$id&other=1")
    }
}
