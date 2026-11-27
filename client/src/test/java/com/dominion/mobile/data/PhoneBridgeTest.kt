package com.dominion.mobile.data

import com.dominion.mobile.data.remote.PhoneBridgeConfig
import com.dominion.mobile.data.remote.PhoneBridgeHealth
import org.junit.Assert.*
import org.junit.Test

class PhoneBridgeTest {
    private val token="t".repeat(40)+"-safe"

    @Test fun acceptsOnlyBareHttpsOriginAndBoundedToken() {
        val checked=PhoneBridgeConfig("https://phone.example.com/",token).checked()
        assertEquals("https://phone.example.com",checked.origin)
        assertTrue(runCatching{PhoneBridgeConfig("http://phone.example.com",token).checked()}.isFailure)
        assertTrue(runCatching{PhoneBridgeConfig("https://phone.example.com/v1",token).checked()}.isFailure)
        assertTrue(runCatching{PhoneBridgeConfig("https://user:pass@phone.example.com",token).checked()}.isFailure)
        assertTrue(runCatching{PhoneBridgeConfig("https://phone.example.com", "short").checked()}.isFailure)
    }

    @Test fun healthResponseCannotClaimAnUnknownState() {
        assertEquals(3,PhoneBridgeHealth("ok",3).checked().approved_numbers)
        assertTrue(runCatching{PhoneBridgeHealth("degraded",1).checked()}.isFailure)
        assertTrue(runCatching{PhoneBridgeHealth("ok",-1).checked()}.isFailure)
    }
}
