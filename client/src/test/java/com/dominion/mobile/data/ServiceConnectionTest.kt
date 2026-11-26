package com.dominion.mobile.data

import com.dominion.mobile.data.remote.*
import com.google.gson.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ServiceConnectionTest {
    @Test fun googleIdentityAloneDoesNotGrantMailOrCalendar() {
        val account = ServiceAccount("google", "Google", connected = true, grantedScopes = JsonPrimitive("openid email profile"))
        assertFalse(account.authorizes("gmail"))
        assertFalse(account.authorizes("google_calendar"))
        assertTrue(account.copy(grantedScopes = JsonPrimitive("https://www.googleapis.com/auth/gmail.readonly")).authorizes("gmail"))
        assertFalse(account.copy(connected = false).authorizes("google_drive"))
    }
    @Test fun manualRegistrationUsesExistingSecretContract() {
        val entry = ConnectorRegistration("22222222-2222-4222-8222-222222222222", "Custom service", "https://example.com/tools", "con_gmail")
        val record = entry.record("example-test-token")
        assertEquals("http", record["transport"].asString)
        assertEquals("example-test-token", record["apiKey"].asString)
        assertTrue(record["approval"].asBoolean)
        assertTrue(runCatching { entry.copy(url = "http://example.com/tools").validate() }.isFailure)
        assertTrue(runCatching { entry.copy(url = "https://user:pass@example.com/tools").validate() }.isFailure)
    }
}
