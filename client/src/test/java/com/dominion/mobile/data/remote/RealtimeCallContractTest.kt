package com.dominion.mobile.data.remote

import org.junit.Assert.*
import org.junit.Test

class RealtimeCallContractTest {
    @Test fun grantsAreBoundToAccountRoomSessionAndCall() {
        val request = RealtimeCallRequest("owner","room","session","call",true)
        val grant = RealtimeCallGrant("owner","room","session","call","agent",true,
            CallTokenResponse("rtc-room","signed","wss://rtc.example.org",Long.MAX_VALUE))
        assertEquals(grant,grant.verified(request))
        assertTrue(runCatching { grant.copy(sessionId="different").verified(request) }.isFailure)
        assertTrue(runCatching { grant.copy(videoAllowed=false).verified(request) }.isFailure)
        assertTrue(runCatching { grant.copy(agentIdentity=null).verified(request) }.isFailure)
    }
    @Test fun onlyExpectedAgentMayReceiveMedia() {
        assertTrue(RealtimeCallGrant.matchesParticipants("agent",emptyList(),false))
        assertFalse(RealtimeCallGrant.matchesParticipants("agent",emptyList(),true))
        assertTrue(RealtimeCallGrant.matchesParticipants("agent",listOf("agent"),true))
        assertFalse(RealtimeCallGrant.matchesParticipants("agent",listOf("agent","other"),true))
        assertFalse(RealtimeCallGrant.matchesParticipants("agent",listOf("other"),false))
    }
}
