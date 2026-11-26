package com.dominion.mobile.data

import com.dominion.mobile.data.remote.CallTokenResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class CallTokenTest {
    private val valid = CallTokenResponse("room", "token", "wss://voice.example.test", 2000L)

    @Test fun acceptsSecureUnexpiredToken() {
        assertEquals(valid, valid.requireUsable(1000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCleartextEndpoint() {
        valid.copy(url = "ws://voice.example.test").requireUsable(1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExpiredToken() {
        valid.requireUsable(2000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyToken() {
        valid.copy(token = "").requireUsable(1000L)
    }
}
